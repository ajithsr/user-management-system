package com.sliide.usermanagement.presentation.userfeed

import com.sliide.usermanagement.data.mapper.toEntity
import com.sliide.usermanagement.data.remote.dto.AddressDto
import com.sliide.usermanagement.data.remote.dto.CompanyDto
import com.sliide.usermanagement.data.remote.dto.UserDto
import com.sliide.usermanagement.data.remote.dto.UsersResponseDto
import com.sliide.usermanagement.data.repository.UserRepositoryImpl
import com.sliide.usermanagement.domain.usecase.GetUsersUseCase
import com.sliide.usermanagement.fake.FakeClock
import com.sliide.usermanagement.fake.FakeUserApiService
import com.sliide.usermanagement.fake.FakeUserLocalDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue

/**
 * Edge-case ViewModel tests that complement [UserFeedViewModelDeleteTest].
 *
 * Covers:
 *  - Duplicate-tap guard: a second DeleteUser while the undo window is open
 *    must be silently ignored (no second snackbar, no timer reset, one API call).
 *  - Retry deduplication: tapping Retry while Loading/Refreshing must not
 *    start a second concurrent fetch.
 *  - Pending-user delete: attempting to delete a user with isPendingCreate=1
 *    must emit ShowError, not ShowUndoDelete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserFeedViewModelEdgeCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedInstant   = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val userId         = 10
    private val userName       = "Alice Smith"

    private lateinit var db  : FakeUserLocalDataSource
    private lateinit var api : FakeUserApiService
    private lateinit var vm  : UserFeedViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        db  = FakeUserLocalDataSource()
        api = FakeUserApiService()
        vm  = buildVm()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Duplicate-delete guard ─────────────────────────────────────────────────

    @Test
    fun `duplicate DeleteUser while undo window is open emits ShowUndoDelete only once`() = runTest {
        seedUser()
        advanceUntilIdle()

        val effects  = mutableListOf<UserFeedEffect>()
        val collectJob = launch { vm.effects.toList(effects) }

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()
        // Second tap while the undo window is still open — must be a no-op
        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        val undoEffects = effects.filterIsInstance<UserFeedEffect.ShowUndoDelete>()
        assertEquals(1, undoEffects.size,
            "ShowUndoDelete must be emitted exactly once; duplicate tap must be ignored")

        collectJob.cancel()
    }

    @Test
    fun `duplicate DeleteUser does not reset the undo window timer`() = runTest {
        seedUser()
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        // Duplicate tap 2 s into the window
        advanceTimeBy(2_000)
        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()

        // 3 more seconds → the original 5 s window closes (2 + 3 = 5)
        advanceTimeBy(3_000)
        advanceUntilIdle()

        assertTrue(db.rows.none { it.id == userId.toLong() },
            "row must be hard-deleted at the original 5 s mark — timer must not have been reset")
    }

    @Test
    fun `duplicate DeleteUser results in exactly one DELETE API call`() = runTest {
        seedUser()
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))
        advanceUntilIdle()
        vm.onIntent(UserFeedIntent.DeleteUser(userId, userName))  // duplicate — ignored
        advanceUntilIdle()

        advanceTimeBy(UserFeedViewModel.UNDO_WINDOW_MS)
        advanceUntilIdle()

        assertEquals(1, api.deleteUserCalls.count { it == userId },
            "DELETE API must be called exactly once even after a duplicate tap")
    }

    // ── Retry deduplication ───────────────────────────────────────────────────

    @Test
    fun `Retry while initial fetch is in flight does not start a second fetch`() = runTest {
        // Override getUsersResult before any coroutine runs (StandardTestDispatcher
        // suspends all launched coroutines until advanceUntilIdle is called).
        var fetchCount = 0
        api.getUsersResult = { _, _ ->
            fetchCount++
            UsersResponseDto(users = emptyList(), total = 0, skip = 0, limit = 30)
        }
        // Rebuild ViewModel with the updated api — init coroutine is enqueued, not run yet
        val freshVm = buildVm()

        // _loadPhase = Loading; the guard in onIntent(Retry) fires and returns immediately
        freshVm.onIntent(UserFeedIntent.Retry)

        advanceUntilIdle()  // only the init coroutine runs

        assertEquals(1, fetchCount, "Retry during Loading must be ignored — only init fetch runs")
    }

    @Test
    fun `Retry tapped twice rapidly triggers only one fetch`() = runTest {
        // Let init finish in a failed state so Retry is allowed once
        api.getUsersResult = { _, _ -> throw RuntimeException("net") }
        val freshVm = buildVm()
        advanceUntilIdle()  // init fails → _loadPhase = Failed

        var fetchCount = 0
        api.getUsersResult = { _, _ ->
            fetchCount++
            UsersResponseDto(users = emptyList(), total = 0, skip = 0, limit = 30)
        }

        // First Retry: sets _loadPhase = Refreshing and enqueues fetch coroutine
        freshVm.onIntent(UserFeedIntent.Retry)
        // Second Retry immediately: _loadPhase is now Refreshing → guard fires
        freshVm.onIntent(UserFeedIntent.Retry)

        advanceUntilIdle()

        assertEquals(1, fetchCount, "only one fetch must run even when Retry is tapped twice")
    }

    // ── Pending-user delete ────────────────────────────────────────────────────

    @Test
    fun `DeleteUser on pending user emits ShowError instead of ShowUndoDelete`() = runTest {
        val pendingId = -1_700_000_000
        db.insertPendingUser(stubDto(id = pendingId).toEntity(fixedInstant.toEpochMilliseconds()))
        advanceUntilIdle()

        val effects    = mutableListOf<UserFeedEffect>()
        val collectJob = launch { vm.effects.toList(effects) }

        vm.onIntent(UserFeedIntent.DeleteUser(pendingId, "Pending User"))
        advanceUntilIdle()

        assertFalse(
            effects.any { it is UserFeedEffect.ShowUndoDelete },
            "must not show undo snackbar when the repository rejects the soft-delete"
        )
        assertTrue(
            effects.any { it is UserFeedEffect.ShowError },
            "must surface a ShowError effect when soft-delete of a pending user is rejected"
        )
        collectJob.cancel()
    }

    @Test
    fun `DeleteUser on pending user never calls the DELETE API`() = runTest {
        val pendingId = -1_700_000_000
        db.insertPendingUser(stubDto(id = pendingId).toEntity(fixedInstant.toEpochMilliseconds()))
        advanceUntilIdle()

        vm.onIntent(UserFeedIntent.DeleteUser(pendingId, "Pending User"))
        advanceUntilIdle()

        // Advance well past any hypothetical window to confirm no delayed API call either
        advanceTimeBy(UserFeedViewModel.UNDO_WINDOW_MS + 1_000)
        advanceUntilIdle()

        assertTrue(api.deleteUserCalls.isEmpty(),
            "DELETE API must never be called after a rejected soft-delete")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildVm() = UserFeedViewModel(
        getUsersUseCase = GetUsersUseCase(
            UserRepositoryImpl(api, db, FakeClock(fixedInstant))
        ),
        clock = FakeClock(fixedInstant)
    )

    private suspend fun seedUser(id: Int = userId) {
        db.upsertUsers(listOf(stubDto(id).toEntity(fixedInstant.toEpochMilliseconds())))
    }

    private fun stubDto(id: Int = userId) = UserDto(
        id        = id,
        firstName = "Alice",
        lastName  = "Smith",
        username  = "alice$id",
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
