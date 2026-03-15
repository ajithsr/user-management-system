package com.sliide.usermanagement.fake

import com.sliide.usermanagement.data.local.UserLocalDataSource
import com.sliide.usermanagement.data.local.db.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [UserLocalDataSource] for tests.
 *
 * Backed by a [MutableStateFlow<List<UserEntity>>] so that
 * [observeAllUsers] emits reactively whenever the list changes —
 * exactly the contract the repository depends on.
 *
 * Thread safety: all mutations go through [MutableStateFlow.update]
 * which is atomically safe. Tests running under [runTest] are
 * single-threaded, so this is sufficient.
 */
class FakeUserLocalDataSource : UserLocalDataSource {

    // ── In-memory store ───────────────────────────────────────────────────────

    /** All rows, including soft-deleted ones. */
    private val _rows = MutableStateFlow<List<UserEntity>>(emptyList())

    /** Snapshot for direct inspection in assertions. */
    val rows: List<UserEntity> get() = _rows.value

    // ── Pagination metadata ───────────────────────────────────────────────────

    private var skip: Int = 0
    private var total: Int? = null

    // ── Observation ───────────────────────────────────────────────────────────

    override fun observeAllUsers(): Flow<List<UserEntity>> =
        _rows.map { list -> list.filter { it.isDeleted == 0L } }

    override fun observeUserById(id: Int): Flow<UserEntity?> =
        _rows.map { list ->
            list.firstOrNull { it.id == id.toLong() && it.isDeleted == 0L }
        }

    // ── API-fetch upsert ──────────────────────────────────────────────────────

    override suspend fun upsertUsers(users: List<UserEntity>) {
        _rows.update { current ->
            val byId = current.associateBy { it.id }.toMutableMap()
            users.forEach { user ->
                val existing = byId[user.id]
                byId[user.id] = if (existing != null) {
                    // Preserve createdAt / isPendingCreate / isDeleted on re-fetch
                    existing.copy(
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
                        jobTitle   = user.jobTitle
                    )
                } else {
                    user
                }
            }
            byId.values.toList()
        }
    }

    // ── Optimistic create ─────────────────────────────────────────────────────

    override suspend fun insertPendingUser(user: UserEntity) {
        _rows.update { it + user.copy(isPendingCreate = 1L, isDeleted = 0L) }
    }

    /**
     * Atomically replaces the row's id and clears the pending flag.
     * Mirrors the SQL: UPDATE SET id=realId, isPendingCreate=0 WHERE id=tempId.
     */
    override suspend fun confirmPendingUser(tempId: Long, realId: Long) {
        _rows.update { list ->
            list.map { row ->
                if (row.id == tempId) row.copy(id = realId, isPendingCreate = 0L)
                else row
            }
        }
    }

    override suspend fun getOneById(id: Long): UserEntity? =
        _rows.value.firstOrNull { it.id == id }

    // ── Soft delete / Undo / Hard delete ─────────────────────────────────────

    override suspend fun softDeleteUser(id: Long) {
        _rows.update { list ->
            list.map { if (it.id == id) it.copy(isDeleted = 1L) else it }
        }
    }

    override suspend fun undoDeleteUser(id: Long) {
        _rows.update { list ->
            list.map { if (it.id == id) it.copy(isDeleted = 0L) else it }
        }
    }

    override suspend fun hardDeleteUser(id: Long) {
        _rows.update { list -> list.filter { it.id != id } }
    }

    // ── Optimistic-create recovery ────────────────────────────────────────────

    override suspend fun getPendingUsers(): List<UserEntity> =
        _rows.value.filter { it.isPendingCreate == 1L }

    // ── Refresh support ───────────────────────────────────────────────────────

    override suspend fun deleteAllApiUsers() {
        _rows.update { list -> list.filter { it.isPendingCreate == 1L } }
    }

    override suspend fun deleteAll() {
        _rows.value = emptyList()
    }

    override suspend fun countAll(): Long = _rows.value.size.toLong()

    // ── Pagination metadata ───────────────────────────────────────────────────

    override suspend fun getSkip(): Int = skip
    override suspend fun saveSkip(value: Int) { skip = value }
    override suspend fun getCachedTotal(): Int? = total
    override suspend fun saveTotal(value: Int) { total = value }
    override suspend fun clearMeta() { skip = 0; total = null }
}
