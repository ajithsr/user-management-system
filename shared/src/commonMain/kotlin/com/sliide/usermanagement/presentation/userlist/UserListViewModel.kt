package com.sliide.usermanagement.presentation.userlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.usecase.GetUsersUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class UserListViewModel(
    private val getUsersUseCase: GetUsersUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(UserListState())
    val state: StateFlow<UserListState> = _state.asStateFlow()

    private val _effects = Channel<UserListEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val pendingDeletes = mutableMapOf<Int, Job>()

    init {
        // Combine users and pagination into a single state update so that a
        // page-load — which emits on both streams simultaneously — produces one
        // recomposition instead of two.
        combine(
            getUsersUseCase.usersStream,
            getUsersUseCase.paginationState
        ) { users, pagination ->
            UserListState(
                users         = users,
                isLoading     = pagination.isLoading,
                isLoadingMore = pagination.isLoadingMore,
                canLoadMore   = pagination.hasMore,
                error         = pagination.error
            )
        }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)

        // Seed the first page on cold start.
        viewModelScope.launch { getUsersUseCase.loadNextPage() }
    }

    fun processIntent(intent: UserListIntent) {
        when (intent) {
            is UserListIntent.LoadInitial  -> viewModelScope.launch { getUsersUseCase.refresh() }
            is UserListIntent.LoadNextPage -> viewModelScope.launch { getUsersUseCase.loadNextPage() }
            is UserListIntent.Refresh      -> viewModelScope.launch { getUsersUseCase.refresh() }
            is UserListIntent.SelectUser   -> viewModelScope.launch {
                _effects.send(UserListEffect.NavigateToDetail(intent.userId))
            }
            is UserListIntent.DismissError -> _state.update { it.copy(error = null) }
            is UserListIntent.DeleteUser   -> deleteUser(intent.userId, intent.userName)
            is UserListIntent.UndoDelete   -> undoDelete(intent.userId)
            is UserListIntent.CreateUser   -> createUser(intent.request)
        }
    }

    override fun onCleared() {
        super.onCleared()
        flushPendingDeletes()
    }

    private fun createUser(request: CreateUserRequest) {
        viewModelScope.launch {
            getUsersUseCase.createUser(request)
                .onFailure { error ->
                    _effects.send(UserListEffect.ShowError(
                        error.message ?: "Failed to add user"
                    ))
                }
        }
    }

    private fun deleteUser(id: Int, userName: String) {
        if (pendingDeletes.containsKey(id)) return

        pendingDeletes[id] = viewModelScope.launch {
            getUsersUseCase.softDeleteUser(id)
                .onFailure { error ->
                    pendingDeletes.remove(id)
                    _effects.send(UserListEffect.ShowError(
                        error.message ?: "Failed to delete user"
                    ))
                    return@launch
                }

            _effects.send(UserListEffect.ShowUndoDelete(id, userName))

            delay(UNDO_WINDOW_MS)

            getUsersUseCase.confirmDelete(id)
                .onFailure { error ->
                    _effects.send(UserListEffect.ShowError(
                        error.message ?: "Failed to finalize delete"
                    ))
                }
            pendingDeletes.remove(id)
        }
    }

    private fun undoDelete(id: Int) {
        // Cancel the pending confirm-delete timer if it is still running.
        // Do NOT early-return when the job is absent: the timer may have just
        // completed before this call arrived (race at the window boundary), but
        // confirmDelete's isDeleted guard means the row is only hard-deleted if
        // isDeleted == 1. If the race is lost the undo is a no-op SQL UPDATE,
        // which is safe — we still attempt it so the user isn't silently failed.
        pendingDeletes.remove(id)?.cancel()
        viewModelScope.launch {
            getUsersUseCase.undoDelete(id)
                .onSuccess {
                    _effects.send(UserListEffect.ScrollToUser(id))
                }
                .onFailure { error ->
                    _effects.send(UserListEffect.ShowError(
                        error.message ?: "Failed to undo delete"
                    ))
                    getUsersUseCase.confirmDelete(id)
                }
        }
    }

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

    companion object {
        // Must be >= SnackbarDuration.Long (~10 000 ms) so the row is never
        // hard-deleted while the "Undo" snackbar is still on screen.
        const val UNDO_WINDOW_MS = 10_000L
    }
}
