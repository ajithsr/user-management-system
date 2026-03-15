package com.sliide.usermanagement.domain.repository

import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.PaginationState
import com.sliide.usermanagement.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {

    // ── Source of truth ───────────────────────────────────────────────────────

    /**
     * Cold Flow backed by SQLDelight. Emits a new list on every DB write.
     * Soft-deleted users are excluded. Pending (optimistically created) users
     * ARE included so the UI can show them with an in-progress indicator.
     */
    val usersStream: Flow<List<User>>

    /**
     * Loading / error state for the list screen. Driven by the repository
     * so pagination details (skip, limit, total) never leak to the ViewModel.
     */
    val paginationState: Flow<PaginationState>

    /** Emits a single non-deleted user, or null if absent from the cache. */
    fun getUserStream(id: Int): Flow<User?>

    // ── Pagination (skip/limit managed internally) ────────────────────────────

    /**
     * Fetches the next page from the API using the repository-owned skip offset.
     * No-op if a load is already in progress or [PaginationState.hasMore] is false.
     * Appended rows trigger a new [usersStream] emission automatically.
     */
    suspend fun loadNextPage(): Result<Unit>

    /**
     * Two-phase last-page fetch for feed-style screens:
     *  1. GET /users?limit=1&skip=0 to read [total] — skipped on warm starts
     *     when a cached total is already available.
     *  2. GET /users?skip=lastPageSkip&limit=PAGE_SIZE to fetch the final page.
     *
     * DB is written after phase 2; [usersStream] emits automatically.
     * Calling this when data is already cached re-fetches to stay fresh.
     */
    suspend fun fetchLastPage(): Result<Unit>

    /**
     * Clears all API-fetched rows and reloads from page 0.
     * Optimistically created rows that are still pending are preserved so that
     * in-flight user creations survive a refresh.
     */
    suspend fun refresh(): Result<Unit>

    // ── Optimistic create ─────────────────────────────────────────────────────

    /**
     * Two-phase creation:
     *  1. Writes a row with a negative temp ID and isPendingCreate=true to the DB.
     *     [usersStream] emits immediately — the UI shows the user at once.
     *  2. POSTs to /users/add.
     *     - Success: promotes the row (real id replaces temp id, pending flag cleared).
     *     - Failure: rolls back by hard-deleting the temp row; returns [Result.failure].
     */
    suspend fun createUser(request: CreateUserRequest): Result<User>

    // ── Undoable delete ───────────────────────────────────────────────────────

    /**
     * Marks the user isDeleted=true in the DB. [usersStream] stops emitting this
     * user immediately. No API call is made — the operation is reversible via
     * [undoDelete] until [confirmDelete] is called.
     */
    suspend fun softDeleteUser(id: Int): Result<Unit>

    /**
     * Reverts a [softDeleteUser]. Sets isDeleted=false; the user reappears in
     * [usersStream]. Must be called before [confirmDelete] to have any effect.
     */
    suspend fun undoDelete(id: Int): Result<Unit>

    /**
     * Permanently removes the user:
     *  - Calls DELETE /users/{id} on a best-effort basis (DummyJSON is a mock
     *    and will not persist the deletion server-side).
     *  - Hard-deletes the row from the local DB regardless of the API result.
     */
    suspend fun confirmDelete(id: Int): Result<Unit>
}
