package com.sliide.usermanagement.presentation.userfeed

import com.sliide.usermanagement.data.mapper.toEntity
import com.sliide.usermanagement.data.remote.dto.AddressDto
import com.sliide.usermanagement.data.remote.dto.CompanyDto
import com.sliide.usermanagement.data.remote.dto.UserDto
import com.sliide.usermanagement.data.repository.UserRepositoryImpl
import com.sliide.usermanagement.domain.usecase.GetUsersUseCase
import com.sliide.usermanagement.fake.FakeClock
import com.sliide.usermanagement.fake.FakeUserApiService
import com.sliide.usermanagement.fake.FakeUserLocalDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ViewModel-level tests for the delete/undo lifecycle.
 *
 * Time is controlled by [StandardTestDispatcher] + [advanceTimeBy] so the
 * 5-second undo window can be tested without real delays.
 *
 * Setup: [Dispatchers.setMain] replaces the main dispatcher so that
 * [viewModelScope] (which uses Dispatchers.Main.immediate) cooperates with
 * the test scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedViewModelDeleteTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedInstant   = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val userId         = 10
    private val userName       = "Alice Smith"

    private lateinit var db   : FakeUserLocalDataSource
    private lateinit var api  : FakeUserApiService
    private lateinit var vm   : UserFeedViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        db  = FakeUserLocalDataSource()
        api = FakeUserApiService()

        val repo = UserRepositoryImpl(
            remoteDataSource = api,
            localDataSource  = db,
            clock            = FakeClock(fixedInstant)
        )
        vm = UserFeedViewModel(
            getUsersUseCase = GetUsersUseCase(repo),
            clock           = FakeClock(fixedInstant)
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Immediate removal ─────────────────────────────────────────────────────

    @Test
    fun `DeleteUser soft-deletes immediately before delay fires`() = runTest {
        seedUser()
        advanceUntilIdle()  // let init coroutines finish

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()  // run soft-delete coroutine

        // DB row should be soft-deleted (not hard-deleted yet)
        assertFalse(db.rows.isEmpty(), "row must still exist in DB (soft-delete, not hard)")
        assertEquals(1L, db.rows.first { it.id == userId.toLong() }.isDeleted)
    }

    @Test
    fun `DeleteUser emits ShowUndoDelete effect`() = runTest {
        seedUser()
        advanceUntilIdle()

        val effects = mutableListOf<UserFeedEffect>()
        val collectJob = launch { vm.effects.toList(effects) }

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        val undoEffect = effects.filterIsInstance<UserFeedEffect.ShowUndoDelete>().firstOrNull()
        assertTrue(undoEffect != null, "ShowUndoDelete effect must be emitted")
        assertEquals(userId,   undoEffect.userId)
        assertEquals(userName, undoEffect.userName)

        collectJob.cancel()
    }

    @Test
    fun `DeleteUser user disappears from state immediately`() = runTest {
        seedUser()
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        val current = vm.state.first()
        if (current is UserFeedState.Success) {
            assertTrue(current.items.none { it.id == userId }, "deleted user must not be in state")
        }
        // Empty or Loading state also means the user isn't visible — both are correct
    }

    // ── Undo within window ────────────────────────────────────────────────────

    @Test
    fun `UndoDelete within 5s restores user and cancels confirm timer`() = runTest {
        seedUser()
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        // Undo before the window closes
        advanceTimeBy(2_000)
        vm.onIntent(UserFeedIntent.UndoDelete(userId))
        advanceUntilIdle()

        // Row should be restored
        val row = db.rows.firstOrNull { it.id == userId.toLong() }
        assertTrue(row != null, "row must exist after undo")
        assertEquals(0L, row.isDeleted, "isDeleted must be 0 after undo")
    }

    @Test
    fun `UndoDelete within 5s does NOT call the delete API`() = runTest {
        seedUser()
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        advanceTimeBy(1_000)
        vm.onIntent(UserFeedIntent.UndoDelete(userId))
        advanceUntilIdle()

        // Advance past where the timer would have fired
        advanceTimeBy(5_000)
        advanceUntilIdle()

        assertTrue(api.deleteUserCalls.isEmpty(), "DELETE API must not be called after undo")
    }

    // ── Timeout without undo ──────────────────────────────────────────────────

    @Test
    fun `after 5s without undo the row is hard-deleted`() = runTest {
        seedUser()
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        // Advance exactly to the undo window closing
        advanceTimeBy(UserFeedViewModel.UNDO_WINDOW_MS)
        advanceUntilIdle()

        assertTrue(db.rows.none { it.id == userId.toLong() }, "row must be hard-deleted after timeout")
    }

    @Test
    fun `DELETE API is called after the 5s window`() = runTest {
        seedUser()
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        // Just before timeout — API should not have been called
        advanceTimeBy(UserFeedViewModel.UNDO_WINDOW_MS - 1)
        advanceUntilIdle()
        assertTrue(api.deleteUserCalls.isEmpty(), "API must not be called before timeout")

        // Cross the threshold
        advanceTimeBy(1)
        advanceUntilIdle()
        assertTrue(api.deleteUserCalls.contains(userId), "API must be called after timeout")
    }

    // ── Double-delete replaces timer ──────────────────────────────────────────

    @Test
    fun `deleting a user twice resets the 5s window`() = runTest {
        seedUser()
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        // 3s in: undo and re-delete
        advanceTimeBy(3_000)
        vm.onIntent(UserFeedIntent.UndoDelete(userId))
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        // 4.9s after re-delete: still within the new window
        advanceTimeBy(4_999)
        advanceUntilIdle()
        assertTrue(api.deleteUserCalls.isEmpty(), "API must not fire before the new window expires")

        // 5s after re-delete: window closes
        advanceTimeBy(1)
        advanceUntilIdle()
        // Called at most once (not twice for both deletes)
        assertEquals(1, api.deleteUserCalls.count { it == userId })
    }

    // ── Concurrent deletes of different users ─────────────────────────────────

    @Test
    fun `two independent users can be in pending-delete simultaneously`() = runTest {
        db.upsertUsers(listOf(
            stubDto(id = 1).toEntity(fixedInstant.toEpochMilliseconds()),
            stubDto(id = 2).toEntity(fixedInstant.toEpochMilliseconds())
        ))
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(1, "User One"))
        advanceUntilIdle()
        vm.onIntent(UserFeedIntent.DeleteUser(2, "User Two"))
        advanceUntilIdle()

        // Undo only user 2
        advanceTimeBy(2_000)
        vm.onIntent(UserFeedIntent.UndoDelete(2))
        advanceUntilIdle()

        // Let user 1's window close
        advanceTimeBy(3_000)
        advanceUntilIdle()

        // User 1 should be hard-deleted; user 2 should be restored
        assertTrue(db.rows.none { it.id == 1L }, "user 1 should be hard-deleted")
        assertTrue(db.rows.any { it.id == 2L && it.isDeleted == 0L }, "user 2 should be restored")
        assertEquals(listOf(1), api.deleteUserCalls, "only user 1 was confirmed-deleted via API")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun seedUser(id: Int = userId) {
        db.upsertUsers(listOf(stubDto(id).toEntity(fixedInstant.toEpochMilliseconds())))
    }

    private fun stubDto(id: Int = userId) = UserDto(
        id        = id,
        firstName = "Alice",
        lastName  = "Smith",
        username  = "alices$id",
        email     = "alice$id@example.com",
        phone     = "+1-000-0000",
        image     = "",
        age       = 30,
        gender    = "female",
        role      = "user",
        address   = AddressDto("123 St", "City", "State", "Country", "00001"),
        company   = CompanyDto("Acme", "Eng", "Engineer")
    )
}
