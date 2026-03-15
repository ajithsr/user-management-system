package com.sliide.usermanagement.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.sliide.usermanagement.data.local.db.UserDatabase
import com.sliide.usermanagement.data.local.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** SQLDelight-backed implementation of [UserLocalDataSource]. Bound in [DatabaseModule]. */
class SQLDelightUserLocalDataSource(database: UserDatabase) : UserLocalDataSource {

    private val userQueries = database.userEntityQueries
    private val metaQueries = database.paginationMetaQueries

    // ── Observation ──────────────────────────────────────────────────────────

    override fun observeAllUsers(): Flow<List<UserEntity>> =
        userQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)

    override fun observeUserById(id: Int): Flow<UserEntity?> =
        userQueries.selectById(id.toLong())
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    // ── API-fetch upsert (two-step, preserves createdAt) ─────────────────────

    /**
     * Upserts a batch of API-fetched users inside a single transaction.
     *
     * Step 1 — insertOrIgnore: inserts new rows (sets createdAt).
     *   Existing rows are untouched by this step — createdAt is preserved.
     * Step 2 — updateUser: refreshes all mutable columns for the given id.
     *   createdAt, isPendingCreate, and isDeleted are NOT updated.
     */
    override suspend fun upsertUsers(users: List<UserEntity>) = withContext(Dispatchers.IO) {
        userQueries.transaction {
            users.forEach { user ->
                userQueries.insertOrIgnore(
                    id              = user.id,
                    firstName       = user.firstName,
                    lastName        = user.lastName,
                    username        = user.username,
                    email           = user.email,
                    phone           = user.phone,
                    avatarUrl       = user.avatarUrl,
                    age             = user.age,
                    gender          = user.gender,
                    role            = user.role,
                    street          = user.street,
                    city            = user.city,
                    state           = user.state,
                    country         = user.country,
                    postalCode      = user.postalCode,
                    company         = user.company,
                    department      = user.department,
                    jobTitle        = user.jobTitle,
                    isPendingCreate = user.isPendingCreate,
                    isDeleted       = user.isDeleted,
                    createdAt       = user.createdAt
                )
                userQueries.updateUser(
                    firstName  = user.firstName,
                    lastName   = user.lastName,
                    username   = user.username,
                    email      = user.email,
                    phone      = user.phone,
                    avatarUrl  = user.avatarUrl,
                    age        = user.age,
                    gender     = user.gender,
                    role       = user.role,
                    street     = user.street,
                    city       = user.city,
                    state      = user.state,
                    country    = user.country,
                    postalCode = user.postalCode,
                    company    = user.company,
                    department = user.department,
                    jobTitle   = user.jobTitle,
                    id         = user.id
                )
            }
        }
    }

    // ── Optimistic create ────────────────────────────────────────────────────

    /**
     * Inserts a pending user row. No updateUser step — this is a fresh insert,
     * not an upsert. The [UserEntity.id] is a negative temp value.
     */
    override suspend fun insertPendingUser(user: UserEntity) = withContext(Dispatchers.IO) {
        userQueries.insertOrIgnore(
            id              = user.id,
            firstName       = user.firstName,
            lastName        = user.lastName,
            username        = user.username,
            email           = user.email,
            phone           = user.phone,
            avatarUrl       = user.avatarUrl,
            age             = user.age,
            gender          = user.gender,
            role            = user.role,
            street          = user.street,
            city            = user.city,
            state           = user.state,
            country         = user.country,
            postalCode      = user.postalCode,
            company         = user.company,
            department      = user.department,
            jobTitle        = user.jobTitle,
            isPendingCreate = 1L,
            isDeleted       = 0L,
            createdAt       = user.createdAt
        )
    }

    /**
     * Promotes a pending row: replaces the negative [tempId] with the
     * API-assigned [realId] and clears the isPendingCreate flag.
     */
    override suspend fun confirmPendingUser(tempId: Long, realId: Long) =
        withContext(Dispatchers.IO) {
            userQueries.confirmPendingUser(realId = realId, tempId = tempId)
        }

    override suspend fun getOneById(id: Long): UserEntity? = withContext(Dispatchers.IO) {
        userQueries.selectOneById(id).executeAsOneOrNull()
    }

    // ── Soft delete / Undo / Hard delete ─────────────────────────────────────

    override suspend fun softDeleteUser(id: Long) = withContext(Dispatchers.IO) {
        userQueries.softDeleteUser(id)
    }

    override suspend fun undoDeleteUser(id: Long) = withContext(Dispatchers.IO) {
        userQueries.undoDeleteUser(id)
    }

    override suspend fun hardDeleteUser(id: Long) = withContext(Dispatchers.IO) {
        userQueries.hardDeleteUser(id)
    }

    // ── Optimistic-create recovery ────────────────────────────────────────────

    override suspend fun getPendingUsers(): List<UserEntity> = withContext(Dispatchers.IO) {
        userQueries.selectPendingUsers().executeAsList()
    }

    // ── Refresh support ──────────────────────────────────────────────────────

    override suspend fun deleteAllApiUsers() = withContext(Dispatchers.IO) {
        userQueries.deleteAllApiUsers()
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        userQueries.deleteAll()
    }

    override suspend fun countAll(): Long = withContext(Dispatchers.IO) {
        userQueries.countAll().executeAsOne()
    }

    // ── Pagination metadata ──────────────────────────────────────────────────

    override suspend fun getSkip(): Int = withContext(Dispatchers.IO) {
        metaQueries.selectMeta("skip").executeAsOneOrNull()?.toInt() ?: 0
    }

    override suspend fun saveSkip(skip: Int) = withContext(Dispatchers.IO) {
        metaQueries.upsertMeta("skip", skip.toLong())
    }

    override suspend fun getCachedTotal(): Int? = withContext(Dispatchers.IO) {
        metaQueries.selectMeta("total").executeAsOneOrNull()?.toInt()
    }

    override suspend fun saveTotal(total: Int) = withContext(Dispatchers.IO) {
        metaQueries.upsertMeta("total", total.toLong())
    }

    override suspend fun clearMeta() = withContext(Dispatchers.IO) {
        metaQueries.clearMeta()
    }
}
