package com.sliide.usermanagement.presentation.userfeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.User
import com.sliide.usermanagement.domain.usecase.GetUsersUseCase
import com.sliide.usermanagement.util.RelativeTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class UserFeedViewModel(
    private val getUsersUseCase: GetUsersUseCase,
    private val clock: Clock = Clock.System
) : ViewModel() {

    // ── Internal load phase ────────────────────────────────────────────────────

    private sealed interface LoadPhase {
        data object Loading    : LoadPhase
        data object Refreshing : LoadPhase
        data class  Failed(val message: String) : LoadPhase
        data object Done       : LoadPhase
    }

    private val _loadPhase = MutableStateFlow<LoadPhase>(LoadPhase.Loading)

    // ── Pending-delete tracker ─────────────────────────────────────────────────
    //
    // Maps userId → the running "delay then confirm" Job.
    //
    // Invariant: a job is in this map for exactly as long as the user is
    // soft-deleted and the undo window is open. The job is removed when:
    //  (a) the delay elapses and confirmDelete runs, or
    //  (b) UndoDelete cancels the job and restores the row, or
    //  (c) onCleared flushes everything.
    //
    // Access is safe without synchronisation because every read/write happens
    // on Dispatchers.Main (viewModelScope + onIntent are both main-thread).
    private val pendingDeletes = mutableMapOf<Int, Job>()

    // ── Public contracts ───────────────────────────────────────────────────────

    private val _state = MutableStateFlow<UserFeedState>(UserFeedState.Loading)
    val state = _state.asStateFlow()

    private val _effects = Channel<UserFeedEffect>(Channel.BUFFERED)
    val effects: Flow<UserFeedEffect> = _effects.receiveAsFlow()

    // ── 60-second tick for relative timestamp refresh ──────────────────────────

    private val tickFlow: Flow<Unit> = flow {
        while (true) { emit(Unit); delay(60_000L) }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        combine(getUsersUseCase.usersStream, _loadPhase, tickFlow) { users, phase, _ ->
            buildState(users, phase, clock.now())
        }
            // Drop tick-triggered rebuilds where no createdAt string actually
            // changed (all items are still "X minutes ago"). StateFlow already
            // deduplicates equal values, but distinctUntilChanged short-circuits
            // the downstream onEach before reaching the StateFlow assignment.
            .distinctUntilChanged()
            .onEach { _state.value = it }
            .launchIn(viewModelScope)

        viewModelScope.launch { fetchFeed() }
    }

    // ── Intent handler ─────────────────────────────────────────────────────────

    fun onIntent(intent: UserFeedIntent) {
        when (intent) {
            is UserFeedIntent.Retry      -> {
                // Deduplicate: ignore tap if a fetch is already in flight.
                val phase = _loadPhase.value
                if (phase is LoadPhase.Loading || phase is LoadPhase.Refreshing) return
                viewModelScope.launch {
                    _loadPhase.value = LoadPhase.Refreshing
                    fetchFeed()
                }
            }
            is UserFeedIntent.OpenUser   -> viewModelScope.launch {
                _effects.send(UserFeedEffect.NavigateToDetail(intent.userId))
            }
            is UserFeedIntent.CreateUser -> viewModelScope.launch {
                createUser(intent.request)
            }
            is UserFeedIntent.DeleteUser -> deleteUser(intent.userId, intent.userName)
            is UserFeedIntent.UndoDelete -> undoDelete(intent.userId)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled immediately after onCleared returns, which
        // would cancel all pending delay jobs and leave soft-deleted rows as ghost
        // entries in the DB. Flush them first via an orphaned scope.
        flushPendingDeletes()
    }

    // ── Delete / Undo ──────────────────────────────────────────────────────────

    /**
     * Soft-deletes the user, shows an undo snackbar, then waits [UNDO_WINDOW_MS].
     * If the window elapses without an undo, calls [confirmDelete].
     *
     * Concurrency notes
     * -----------------
     * • [containsKey] guard at the top prevents a duplicate tap from opening a
     *   second snackbar for the same user while the undo window is already open.
     * • The job is stored in [pendingDeletes] synchronously before the coroutine
     *   body runs, so a rapid UndoDelete intent always finds the job to cancel.
     * • [delay] is the only cooperative cancellation point. Once [confirmDelete]
     *   starts it runs to completion regardless.
     */
    private fun deleteUser(id: Int, userName: String) {
        // Ignore duplicate tap — undo window is already open for this user.
        if (pendingDeletes.containsKey(id)) return

        pendingDeletes[id] = viewModelScope.launch {
            // ① Soft-delete: isDeleted=1 in DB → usersStream emits → UI animates out
            getUsersUseCase.softDeleteUser(id)
                .onFailure { error ->
                    pendingDeletes.remove(id)
                    _effects.send(UserFeedEffect.ShowError(
                        error.message ?: "Failed to delete user"
                    ))
                    return@launch
                }

            // ② Notify UI to show snackbar with Undo button
            _effects.send(UserFeedEffect.ShowUndoDelete(id, userName))

            // ③ Wait for the undo window — this is the only cancellation point.
            //   If UndoDelete arrives here, CancellationException propagates and
            //   the confirm call below is never reached.
            delay(UNDO_WINDOW_MS)

            // ④ Window elapsed without undo: hard-delete locally + best-effort API
            getUsersUseCase.confirmDelete(id)
                .onFailure { error ->
                    _effects.send(UserFeedEffect.ShowError(
                        error.message ?: "Failed to finalize delete"
                    ))
                }
            pendingDeletes.remove(id)
        }
    }

    /**
     * Cancels the pending confirm timer and restores the soft-deleted row.
     *
     * If the undo arrives after the window closed (job completed or null),
     * it is silently ignored — the user is already hard-deleted.
     *
     * On restore failure the error is shown and [confirmDelete] is called to
     * keep the DB consistent (row stays soft-deleted rather than stranded).
     */
    private fun undoDelete(id: Int) {
        val job = pendingDeletes.remove(id) ?: return  // window already closed
        job.cancel()
        viewModelScope.launch {
            getUsersUseCase.undoDelete(id)
                .onFailure { error ->
                    // Undo failed — re-confirm so the row is not stranded as soft-deleted.
                    _effects.send(UserFeedEffect.ShowError(
                        error.message ?: "Failed to undo delete"
                    ))
                    getUsersUseCase.confirmDelete(id)
                }
        }
    }

    /**
     * Called from [onCleared]. Cancels all delay timers and confirms every
     * pending delete in an orphaned [CoroutineScope] that outlives the ViewModel.
     *
     * [SupervisorJob] ensures one failing confirmation does not abort others.
     * [withTimeout] caps the flush at 10 seconds; any confirms that do not
     * complete within that window are abandoned (DB rows remain soft-deleted
     * until the next cold start, where [getPendingUsers] can recover them).
     */
    private fun flushPendingDeletes() {
        if (pendingDeletes.isEmpty()) return
        val ids = pendingDeletes.keys.toList()
        pendingDeletes.values.forEach { it.cancel() }
        pendingDeletes.clear()
        CoroutineScope(SupervisorJob()).launch {
            withTimeout(10_000L) {
                ids.forEach { id -> launch { getUsersUseCase.confirmDelete(id) } }
            }
        }
    }

    // ── Create ────────────────────────────────────────────────────────────────

    private suspend fun createUser(request: CreateUserRequest) {
        getUsersUseCase.createUser(request)
            .onSuccess { user -> _effects.send(UserFeedEffect.UserCreated(user)) }
            .onFailure { error ->
                _effects.send(UserFeedEffect.ShowError(
                    error.message ?: "Failed to create user"
                ))
            }
    }

    // ── Feed fetch ────────────────────────────────────────────────────────────

    private suspend fun fetchFeed() {
        val hadData = _loadPhase.value.let {
            it is LoadPhase.Done || it is LoadPhase.Refreshing
        }
        getUsersUseCase.fetchLastPage()
            .onSuccess { _loadPhase.value = LoadPhase.Done }
            .onFailure { error ->
                val message = error.message ?: "Failed to load feed"
                _loadPhase.value = LoadPhase.Failed(message)
                if (hadData) _effects.send(UserFeedEffect.ShowError(message))
            }
    }

    // ── State builder ─────────────────────────────────────────────────────────

    private fun buildState(users: List<User>, phase: LoadPhase, now: Instant): UserFeedState =
        when (phase) {
            is LoadPhase.Loading, is LoadPhase.Refreshing -> {
                if (users.isEmpty()) UserFeedState.Loading
                else UserFeedState.Success(items = users.toFeedItems(now), isRefreshing = true)
            }
            is LoadPhase.Done -> {
                if (users.isEmpty()) UserFeedState.Empty
                else UserFeedState.Success(items = users.toFeedItems(now))
            }
            is LoadPhase.Failed -> {
                if (users.isEmpty()) UserFeedState.Error(phase.message)
                else UserFeedState.Success(items = users.toFeedItems(now))
            }
        }

    private fun List<User>.toFeedItems(now: Instant): List<UserFeedItem> = map { user ->
        UserFeedItem(
            id        = user.id,
            initials  = user.initials,
            fullName  = user.fullName,
            email     = user.email,
            role      = user.company.roleDescription,
            location  = user.address.cityRegion,
            avatarUrl = user.avatarUrl,
            createdAt = RelativeTimeFormatter.format(user.createdAt, now),
            isPending = user.isPending,
            isAdmin   = user.isAdmin
        )
    }

    companion object {
        /** Duration of the undo window in milliseconds. Matches the snackbar duration. */
        const val UNDO_WINDOW_MS = 5_000L
    }
}
