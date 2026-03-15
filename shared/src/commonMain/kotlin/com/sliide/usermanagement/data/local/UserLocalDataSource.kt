package com.sliide.usermanagement.data.local

import com.sliide.usermanagement.data.local.db.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the local user data source.
 * Production implementation: [SQLDelightUserLocalDataSource].
 * Test implementation: any in-memory fake.
 */
interface UserLocalDataSource {

    // ── Observation ──────────────────────────────────────────────────────────

    /** Emits on every write to UserEntity. Soft-deleted rows are excluded. */
    fun observeAllUsers(): Flow<List<UserEntity>>

    /** Emits the user if present and not soft-deleted, otherwise null. */
    fun observeUserById(id: Int): Flow<UserEntity?>

    // ── API-fetch upsert ─────────────────────────────────────────────────────

    suspend fun upsertUsers(users: List<UserEntity>)

    // ── Optimistic create ────────────────────────────────────────────────────

    suspend fun insertPendingUser(user: UserEntity)
    suspend fun confirmPendingUser(tempId: Long, realId: Long)
    suspend fun getOneById(id: Long): UserEntity?

    // ── Soft delete / Undo / Hard delete ─────────────────────────────────────

    suspend fun softDeleteUser(id: Long)
    suspend fun undoDeleteUser(id: Long)
    suspend fun hardDeleteUser(id: Long)

    // ── Optimistic-create recovery ────────────────────────────────────────────

    /**
     * Returns all rows where [UserEntity.isPendingCreate] == 1.
     *
     * Used by the ViewModel's `onCleared` flush path and by any future
     * process-death recovery that needs to know which creates were still
     * in-flight when the process was killed.
     */
    suspend fun getPendingUsers(): List<UserEntity>

    // ── Refresh support ──────────────────────────────────────────────────────

    suspend fun deleteAllApiUsers()
    suspend fun deleteAll()
    suspend fun countAll(): Long

    // ── Pagination metadata ──────────────────────────────────────────────────

    suspend fun getSkip(): Int
    suspend fun saveSkip(skip: Int)
    suspend fun getCachedTotal(): Int?
    suspend fun saveTotal(total: Int)
    suspend fun clearMeta()
}
