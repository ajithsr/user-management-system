package com.sliide.usermanagement.data.repository

import com.sliide.usermanagement.fake.FakeClock
import com.sliide.usermanagement.fake.FakeUserApiService
import com.sliide.usermanagement.fake.FakeUserLocalDataSource
import com.sliide.usermanagement.data.mapper.toEntity
import com.sliide.usermanagement.data.remote.dto.AddressDto
import com.sliide.usermanagement.data.remote.dto.CompanyDto
import com.sliide.usermanagement.data.remote.dto.UserDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repository-level tests for the soft-delete / undo / confirm-delete lifecycle.
 *
 * These tests exercise the full flow through [UserRepositoryImpl] using
 * in-memory fakes — no real DB or network required.
 */
class UserRepositoryDeleteTest {

    private val fixedInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val userId = 42

    private fun buildRepo(
        api: FakeUserApiService = FakeUserApiService(),
        db: FakeUserLocalDataSource = FakeUserLocalDataSource()
    ) = UserRepositoryImpl(
        remoteDataSource = api,
        localDataSource  = db,
        clock            = FakeClock(fixedInstant)
    )

    /** Seeds [db] with one visible user. Returns the repo for chaining. */
    private suspend fun repoWithUser(
        db: FakeUserLocalDataSource,
        api: FakeUserApiService = FakeUserApiService()
    ): UserRepositoryImpl {
        db.upsertUsers(listOf(stubDto().toEntity(createdAt = fixedInstant.toEpochMilliseconds())))
        return buildRepo(api, db)
    }

    // ── softDeleteUser ────────────────────────────────────────────────────────

    @Test
    fun `softDelete removes user from usersStream immediately`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = repoWithUser(db)

        repo.softDeleteUser(userId)

        val users = db.observeAllUsers().first()
        assertTrue(users.isEmpty(), "soft-deleted user must not appear in stream")
    }

    @Test
    fun `softDelete sets isDeleted=1 in DB`() = runTest {
        val db = FakeUserLocalDataSource()
        repoWithUser(db).softDeleteUser(userId)

        val row = db.rows.single()
        assertEquals(1L, row.isDeleted)
    }

    @Test
    fun `softDelete does NOT call the API`() = runTest {
        val api = FakeUserApiService()
        val db  = FakeUserLocalDataSource()
        repoWithUser(db, api).softDeleteUser(userId)

        assertTrue(api.deleteUserCalls.isEmpty(), "API must not be called on soft delete")
    }

    @Test
    fun `softDelete returns Result success`() = runTest {
        val db = FakeUserLocalDataSource()
        val result = repoWithUser(db).softDeleteUser(userId)

        assertTrue(result.isSuccess)
    }

    // ── undoDelete ────────────────────────────────────────────────────────────

    @Test
    fun `undoDelete restores user in usersStream`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = repoWithUser(db)

        repo.softDeleteUser(userId)
        repo.undoDelete(userId)

        val users = db.observeAllUsers().first()
        assertEquals(1, users.size, "user must reappear after undo")
        assertEquals(userId.toLong(), users.single().id)
    }

    @Test
    fun `undoDelete sets isDeleted=0 in DB`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = repoWithUser(db)

        repo.softDeleteUser(userId)
        repo.undoDelete(userId)

        val row = db.rows.single()
        assertEquals(0L, row.isDeleted)
    }

    @Test
    fun `undoDelete does NOT call the API`() = runTest {
        val api = FakeUserApiService()
        val db  = FakeUserLocalDataSource()
        val repo = repoWithUser(db, api)

        repo.softDeleteUser(userId)
        repo.undoDelete(userId)

        assertTrue(api.deleteUserCalls.isEmpty(), "API must not be called on undo")
    }

    // ── confirmDelete ─────────────────────────────────────────────────────────

    @Test
    fun `confirmDelete hard-deletes the row from DB`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = repoWithUser(db)

        repo.softDeleteUser(userId)
        repo.confirmDelete(userId)

        assertTrue(db.rows.isEmpty(), "row must be physically removed after confirm")
    }

    @Test
    fun `confirmDelete calls the delete API`() = runTest {
        val api = FakeUserApiService()
        val db  = FakeUserLocalDataSource()
        val repo = repoWithUser(db, api)

        repo.softDeleteUser(userId)
        repo.confirmDelete(userId)

        assertTrue(api.deleteUserCalls.contains(userId))
    }

    @Test
    fun `confirmDelete succeeds even if API call throws`() = runTest {
        val api = FakeUserApiService().apply {
            deleteUserResult = { _ -> throw RuntimeException("503") }
        }
        val db = FakeUserLocalDataSource()
        val repo = repoWithUser(db, api)

        repo.softDeleteUser(userId)
        val result = repo.confirmDelete(userId)

        assertTrue(result.isSuccess, "API failure must be swallowed; local hard-delete still happens")
        assertTrue(db.rows.isEmpty(), "row must be gone even after API error")
    }

    @Test
    fun `confirmDelete after undo has no row to delete — no crash`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = repoWithUser(db)

        repo.softDeleteUser(userId)
        repo.undoDelete(userId)          // user restored
        val result = repo.confirmDelete(userId)  // called out of order

        // Row was never hard-deleted because it was restored before confirm
        assertTrue(result.isSuccess)
        assertFalse(db.rows.isEmpty(), "user should still be in DB")
    }

    // ── Full lifecycle ────────────────────────────────────────────────────────

    @Test
    fun `soft then confirm removes user permanently`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = repoWithUser(db)

        repo.softDeleteUser(userId)

        // Verify intermediate state: row exists but is marked deleted
        val intermediate = db.observeAllUsers().first()
        assertTrue(intermediate.isEmpty())
        assertEquals(1L, db.rows.single().isDeleted)

        repo.confirmDelete(userId)

        // Final state: no trace in DB
        assertTrue(db.rows.isEmpty())
    }

    @Test
    fun `soft then undo restores user to original state`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = repoWithUser(db)

        repo.softDeleteUser(userId)
        repo.undoDelete(userId)

        val users = db.observeAllUsers().first()
        assertEquals(1, users.size)
        assertEquals(0L, users.single().isDeleted)
    }

    @Test
    fun `two independent users can be deleted concurrently`() = runTest {
        val db = FakeUserLocalDataSource()
        db.upsertUsers(listOf(
            stubDto(id = 1).toEntity(fixedInstant.toEpochMilliseconds()),
            stubDto(id = 2).toEntity(fixedInstant.toEpochMilliseconds())
        ))
        val repo = buildRepo(db = db)

        repo.softDeleteUser(1)
        repo.softDeleteUser(2)

        val visible = db.observeAllUsers().first()
        assertTrue(visible.isEmpty(), "both users should be hidden")
        assertEquals(2, db.rows.size, "rows still exist, just soft-deleted")

        repo.undoDelete(1)

        val afterUndo = db.observeAllUsers().first()
        assertEquals(1, afterUndo.size)
        assertEquals(1L, afterUndo.single().id)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun stubDto(id: Int = userId) = UserDto(
        id        = id,
        firstName = "Test",
        lastName  = "User",
        username  = "testuser$id",
        email     = "test$id@example.com",
        phone     = "+1-000-0000",
        image     = "",
        age       = 25,
        gender    = "male",
        role      = "user",
        address   = AddressDto("Street", "City", "State", "Country", "00000"),
        company   = CompanyDto("Acme", "Engineering", "Engineer")
    )
}
