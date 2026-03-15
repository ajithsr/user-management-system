package com.sliide.usermanagement.data.repository

import com.sliide.usermanagement.data.local.UserLocalDataSource
import com.sliide.usermanagement.data.mapper.toCreateDto
import com.sliide.usermanagement.data.mapper.toDomain
import com.sliide.usermanagement.data.mapper.toEntity
import com.sliide.usermanagement.data.mapper.toTempEntity
import com.sliide.usermanagement.data.remote.UserApiService
import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.PaginationState
import com.sliide.usermanagement.domain.model.User
import com.sliide.usermanagement.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class UserRepositoryImpl(
    private val remoteDataSource: UserApiService,
    private val localDataSource: UserLocalDataSource,
    private val clock: Clock = Clock.System
) : UserRepository {

    /**
     * Guards all DB write operations.
     *
     * Network calls are intentionally performed OUTSIDE this mutex so that a
     * slow or timing-out request does not starve other writes (e.g. a 30-second
     * [createUser] POST must not block a concurrent [softDeleteUser]).
     *
     * Invariants protected by the mutex:
     *  - skip offset is read and incremented atomically in [loadNextPage].
     *  - [deleteAllApiUsers] + [upsertUsers] pair in [refresh] is atomic.
     *  - [confirmPendingUser] / [hardDeleteUser] in [createUser] cannot race
     *    with a concurrent [refresh].
     *  - [confirmDelete] isDeleted check + [hardDeleteUser] is atomic.
     */
    private val mutex = Mutex()

    /**
     * Tracks whether [paginationState].hasMore has been seeded from
     * [PaginationMeta] on this process lifecycle. Written only inside
     * [mutex.withLock] so reads elsewhere under the same lock are consistent.
     */
    private var paginationInitialized = false

    private val _paginationState = MutableStateFlow(PaginationState())
    override val paginationState = _paginationState.asStateFlow()

    // ── Source of truth ───────────────────────────────────────────────────────

    override val usersStream: Flow<List<User>> =
        localDataSource.observeAllUsers().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getUserStream(id: Int): Flow<User?> =
        localDataSource.observeUserById(id).map { it?.toDomain() }

    // ── Pagination ────────────────────────────────────────────────────────────

    /**
     * Fetches the next page. The mutex is held only for the brief check+read
     * at the start and for the DB writes at the end; the network call runs
     * outside the lock so it never starves concurrent write operations.
     */
    override suspend fun loadNextPage(): Result<Unit> {
        val skip: Int
        mutex.withLock {
            initPaginationIfNeeded()
            val state = _paginationState.value
            if (state.isLoading || state.isLoadingMore || !state.hasMore) {
                return Result.success(Unit)
            }
            _paginationState.update { it.copy(isLoadingMore = true, error = null) }
            skip = localDataSource.getSkip()
        }

        return runCatching {
            val response = remoteDataSource.getUsers(skip = skip, limit = PAGE_SIZE)
            mutex.withLock {
                localDataSource.upsertUsers(response.users.map { it.toEntity() })
                val newSkip = skip + response.users.size
                localDataSource.saveSkip(newSkip)
                localDataSource.saveTotal(response.total)
                _paginationState.update {
                    it.copy(isLoadingMore = false, hasMore = newSkip < response.total)
                }
            }
        }.onFailure { error ->
            _paginationState.update { it.copy(isLoadingMore = false, error = error.message) }
        }
    }

    /**
     * Clears API rows and reloads page 0. The network call runs outside the
     * mutex; [deleteAllApiUsers] + [upsertUsers] are kept in the same lock
     * acquisition so no concurrent writer can interleave between them.
     */
    override suspend fun refresh(): Result<Unit> {
        _paginationState.update { it.copy(isLoading = true, error = null) }

        return runCatching {
            val response = remoteDataSource.getUsers(skip = 0, limit = PAGE_SIZE)
            mutex.withLock {
                // deleteAllApiUsers preserves pending rows (isPendingCreate=1)
                // and soft-deleted rows (isDeleted=1) — see UserEntity.sq comment.
                localDataSource.deleteAllApiUsers()
                localDataSource.upsertUsers(response.users.map { it.toEntity() })
                val newSkip = response.users.size
                localDataSource.saveSkip(newSkip)
                localDataSource.saveTotal(response.total)
                paginationInitialized = true
                _paginationState.update {
                    it.copy(isLoading = false, hasMore = newSkip < response.total)
                }
            }
        }.onFailure { error ->
            _paginationState.update { it.copy(isLoading = false, error = error.message) }
        }.map { }
    }

    // ── Optimistic create ─────────────────────────────────────────────────────

    /**
     * Two-phase creation with a narrow mutex window on each phase:
     *
     * Phase 1 (under mutex) — insert pending row with a negative temp ID.
     *   Releases the lock immediately so the subsequent network call does not
     *   starve other writers for up to 30 seconds.
     *
     * Phase 2 (network, no mutex) — POST /users/add.
     *   Success: reacquire mutex, promote temp→real id, clear pending flag.
     *   Failure: reacquire mutex, hard-delete the temp row.
     */
    override suspend fun createUser(request: CreateUserRequest): Result<User> {
        val now = clock.now()
        val tempId = -now.toEpochMilliseconds()
        val tempEntity = request.toTempEntity(
            tempId    = tempId,
            createdAt = now.toEpochMilliseconds()
        )

        // Phase 1: optimistic insert — mutex held only for the DB write
        mutex.withLock { localDataSource.insertPendingUser(tempEntity) }

        // Phase 2: network outside mutex — does not starve concurrent writes
        return runCatching { remoteDataSource.createUser(request.toCreateDto()) }
            .fold(
                onSuccess = { dto ->
                    runCatching {
                        mutex.withLock {
                            localDataSource.confirmPendingUser(
                                tempId = tempId,
                                realId = dto.id.toLong()
                            )
                            localDataSource.getOneById(dto.id.toLong())?.toDomain()
                                ?: tempEntity.copy(
                                    id              = dto.id.toLong(),
                                    isPendingCreate = 0L
                                ).toDomain()
                        }
                    }
                },
                onFailure = { error ->
                    mutex.withLock { localDataSource.hardDeleteUser(tempId) }
                    Result.failure(error)
                }
            )
    }

    // ── Undoable delete ───────────────────────────────────────────────────────

    /**
     * Soft-deletes the user. Returns a failure if the user's creation is still
     * pending (isPendingCreate=1) — deleting a not-yet-confirmed user would
     * leave an orphaned temp row if the POST later succeeds.
     */
    override suspend fun softDeleteUser(id: Int): Result<Unit> = mutex.withLock {
        runCatching {
            val row = localDataSource.getOneById(id.toLong())
            if (row?.isPendingCreate == 1L) {
                error("Cannot delete a user whose creation is still pending")
            }
            localDataSource.softDeleteUser(id.toLong())
        }
    }

    override suspend fun undoDelete(id: Int): Result<Unit> = mutex.withLock {
        runCatching {
            localDataSource.undoDeleteUser(id.toLong())
        }
    }

    /**
     * Permanently removes the user — but only if the row is still soft-deleted.
     *
     * The isDeleted guard prevents the following race:
     *   1. User swipes delete → undo window opens.
     *   2. User taps Undo → [undoDelete] restores the row (isDeleted=0).
     *   3. Timer fires before cancellation propagates → [confirmDelete] runs.
     *   Without the guard, step 3 would hard-delete a row the user just restored.
     */
    override suspend fun confirmDelete(id: Int): Result<Unit> = mutex.withLock {
        runCatching {
            val row = localDataSource.getOneById(id.toLong())
            // If the row is absent or was already restored, nothing to do.
            if (row == null || row.isDeleted != 1L) return@withLock Result.success(Unit)
            // Best-effort API call — DummyJSON does not persist deletions.
            runCatching { remoteDataSource.deleteUser(id) }
            localDataSource.hardDeleteUser(id.toLong())
        }
    }

    // ── Last-page fetch ───────────────────────────────────────────────────────

    /**
     * Fetches the last page for feed-style screens. Both network calls
     * ([lastPageSkip] probe + page GET) run outside the mutex; the resulting
     * DB writes are batched inside a single lock acquisition.
     */
    override suspend fun fetchLastPage(): Result<Unit> {
        _paginationState.update { it.copy(isLoading = true, error = null) }

        return runCatching {
            // lastPageSkip may issue a probe GET; no mutex needed for reads
            val skip     = lastPageSkip()
            val response = remoteDataSource.getUsers(skip = skip, limit = PAGE_SIZE)

            mutex.withLock {
                localDataSource.upsertUsers(response.users.map { it.toEntity() })
                localDataSource.saveSkip(skip + response.users.size)
                localDataSource.saveTotal(response.total)
                paginationInitialized = true
            }
            _paginationState.update {
                it.copy(isLoading = false, hasMore = false)
            }
        }.onFailure { error ->
            _paginationState.update { it.copy(isLoading = false, error = error.message) }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Calculates the skip offset for the last page.
     *
     * On warm starts the cached total is used (zero extra network calls).
     * On cold starts a lightweight GET /users?limit=1&skip=0 reads [total]
     * from the response, then saves it for subsequent calls.
     *
     * Intentionally called outside [mutex] — the probe network call must not
     * hold the lock. Concurrent writes to [saveTotal] are atomic SQLite
     * transactions; the worst case is a redundant probe, not corruption.
     */
    private suspend fun lastPageSkip(): Int {
        val cachedTotal = localDataSource.getCachedTotal()
        val total = if (cachedTotal != null) {
            cachedTotal
        } else {
            val probe = remoteDataSource.getUsers(skip = 0, limit = 1)
            localDataSource.saveTotal(probe.total)
            probe.total
        }
        val fullPages    = total / PAGE_SIZE
        val lastPageSkip = if (total % PAGE_SIZE == 0) (fullPages - 1) * PAGE_SIZE
                           else fullPages * PAGE_SIZE
        return maxOf(0, lastPageSkip)
    }

    /**
     * Seeds [paginationState].hasMore from [PaginationMeta] on the first call
     * so a cold start does not issue an unnecessary probe when all pages are
     * already cached from a previous session.
     *
     * Must be called inside [mutex.withLock].
     */
    private suspend fun initPaginationIfNeeded() {
        if (paginationInitialized) return
        val skip  = localDataSource.getSkip()
        val total = localDataSource.getCachedTotal()
        if (total != null) {
            _paginationState.update { it.copy(hasMore = skip < total) }
        }
        paginationInitialized = true
    }

    companion object {
        const val PAGE_SIZE = 30
    }
}
