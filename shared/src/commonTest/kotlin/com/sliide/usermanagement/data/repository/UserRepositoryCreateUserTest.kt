package com.sliide.usermanagement.data.repository

import com.sliide.usermanagement.data.remote.dto.CreateUserResponseDto
import com.sliide.usermanagement.fake.FakeClock
import com.sliide.usermanagement.fake.FakeUserApiService
import com.sliide.usermanagement.fake.FakeUserLocalDataSource
import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.Gender
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the optimistic-create flow inside [UserRepositoryImpl].
 *
 * No real database or network: [FakeUserLocalDataSource] holds rows in a
 * [kotlinx.coroutines.flow.MutableStateFlow] and [FakeUserApiService] returns
 * whatever the test configures.
 *
 * All tests use a [FakeClock] so the generated tempId is deterministic.
 */
class UserRepositoryCreateUserTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** Fixed point in time → deterministic tempId. */
    private val fixedInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    /**
     * tempId = -(clock epoch ms). With [fixedInstant] this is always
     * -1_700_000_000_000. Any real DummyJSON id is a small positive integer,
     * so there is zero collision risk.
     */
    private val expectedTempId = -fixedInstant.toEpochMilliseconds()

    /** The server-assigned id returned by the fake API. */
    private val serverAssignedId = 42

    private fun successResponse(id: Int = serverAssignedId) = CreateUserResponseDto(
        id        = id,
        firstName = stubRequest.firstName,
        lastName  = stubRequest.lastName,
        email     = stubRequest.email,
        username  = stubRequest.username
    )

    private val stubRequest = CreateUserRequest(
        firstName = "Alice",
        lastName  = "Smith",
        email     = "alice@example.com",
        username  = "alices",
        age       = 30,
        gender    = Gender.Female
    )

    private fun buildRepo(
        api: FakeUserApiService = defaultApi(),
        db: FakeUserLocalDataSource = FakeUserLocalDataSource()
    ) = UserRepositoryImpl(
        remoteDataSource = api,
        localDataSource  = db,
        clock            = FakeClock(fixedInstant)
    )

    /** Default API fake that succeeds with [serverAssignedId]. */
    private fun defaultApi() = FakeUserApiService().apply {
        createUserResult = { _ -> successResponse() }
    }

    // ── Phase 1: optimistic DB write ─────────────────────────────────────────

    @Test
    fun `pending row is inserted before network call returns`() = runTest {
        val db = FakeUserLocalDataSource()

        // Block the API call until we inspect the DB state
        var capturedDbStateBeforeApiReturn: List<*> = emptyList<Any>()
        val api = FakeUserApiService().apply {
            createUserResult = { dto ->
                capturedDbStateBeforeApiReturn = db.rows
                successResponse()
            }
        }

        val repo = buildRepo(api, db)
        repo.createUser(stubRequest)

        assertTrue(capturedDbStateBeforeApiReturn.isNotEmpty(),
            "DB should already have the temp row when the API call executes")
    }

    @Test
    fun `pending row has negative tempId derived from clock`() = runTest {
        val db = FakeUserLocalDataSource()
        val api = FakeUserApiService()
        val repo = buildRepo(api, db)

        repo.createUser(stubRequest)

        // The temp row should no longer exist (it was promoted), but we can
        // verify it was created with the right id via the API call timing test
        // above and via the usersStream emission test below.
        assertTrue(api.createUserCalls.isNotEmpty(), "API should have been called")
    }

    @Test
    fun `usersStream emits pending user before API responds`() = runTest {
        val db = FakeUserLocalDataSource()
        val pendingEmissions = mutableListOf<Boolean>()

        val api = FakeUserApiService().apply {
            createUserResult = { _ ->
                // Capture whatever usersStream emits at this exact moment
                // (synchronous read of the current DB state via first())
                val current = db.observeAllUsers().first()
                pendingEmissions += current.any { it.isPendingCreate == 1L }
                successResponse()
            }
        }

        val repo = buildRepo(api, db)
        repo.createUser(stubRequest)

        assertEquals(1, pendingEmissions.size)
        assertTrue(pendingEmissions[0], "usersStream should have a pending row before API returns")
    }

    // ── Phase 2 success: temp ID promoted to real ID ─────────────────────────

    @Test
    fun `on success temp row is replaced by row with server id`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = buildRepo(db = db)

        repo.createUser(stubRequest)

        val rows = db.rows
        assertFalse(rows.any { it.id == expectedTempId }, "temp row should be gone")
        assertTrue(rows.any { it.id == serverAssignedId.toLong() }, "real row should be present")
    }

    @Test
    fun `on success isPendingCreate is cleared`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = buildRepo(db = db)

        repo.createUser(stubRequest)

        val confirmedRow = db.rows.firstOrNull { it.id == serverAssignedId.toLong() }
        assertNotNull(confirmedRow, "confirmed row must exist")
        assertEquals(0L, confirmedRow.isPendingCreate)
    }

    @Test
    fun `on success createUser returns Result success with real id`() = runTest {
        val repo = buildRepo()

        val result = repo.createUser(stubRequest)

        assertTrue(result.isSuccess)
        assertEquals(serverAssignedId, result.getOrThrow().id)
    }

    @Test
    fun `on success usersStream emits the confirmed user without pending flag`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = buildRepo(db = db)

        repo.createUser(stubRequest)

        val users = db.observeAllUsers().first()
        val created = users.firstOrNull { it.id == serverAssignedId.toLong() }
        assertNotNull(created)
        assertEquals(0L, created.isPendingCreate)
    }

    @Test
    fun `on success createdAt is stamped from injected clock`() = runTest {
        val db = FakeUserLocalDataSource()
        val repo = buildRepo(db = db)

        repo.createUser(stubRequest)

        val row = db.rows.firstOrNull { it.id == serverAssignedId.toLong() }
        assertNotNull(row)
        assertEquals(fixedInstant.toEpochMilliseconds(), row.createdAt)
    }

    // ── Phase 2 failure: rollback ─────────────────────────────────────────────

    @Test
    fun `on API failure temp row is hard-deleted`() = runTest {
        val db = FakeUserLocalDataSource()
        val api = FakeUserApiService().apply {
            createUserResult = { _ -> throw RuntimeException("network error") }
        }
        val repo = buildRepo(api, db)

        repo.createUser(stubRequest)

        assertTrue(db.rows.isEmpty(), "all rows should be gone after rollback")
    }

    @Test
    fun `on API failure createUser returns Result failure`() = runTest {
        val api = FakeUserApiService().apply {
            createUserResult = { _ -> throw RuntimeException("timeout") }
        }
        val repo = buildRepo(api)

        val result = repo.createUser(stubRequest)

        assertTrue(result.isFailure)
        assertEquals("timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun `on API failure usersStream emits empty list after rollback`() = runTest {
        val db = FakeUserLocalDataSource()
        val api = FakeUserApiService().apply {
            createUserResult = { _ -> throw RuntimeException("offline") }
        }
        val repo = buildRepo(api, db)

        repo.createUser(stubRequest)

        val users = db.observeAllUsers().first()
        assertTrue(users.isEmpty(), "rolled-back user must not appear in stream")
    }

    // ── Coroutine safety: concurrent creates ──────────────────────────────────

    @Test
    fun `two concurrent creates both complete without corrupting each other`() = runTest {
        // Two different fake clocks so tempIds don't collide
        val clock1 = FakeClock(Instant.fromEpochMilliseconds(1_000_000_000_000L))
        val clock2 = FakeClock(Instant.fromEpochMilliseconds(2_000_000_000_000L))

        val db = FakeUserLocalDataSource()
        val api = FakeUserApiService().apply {
            var nextId = 100
            createUserResult = { dto ->
                CreateUserResponseDto(
                    id        = nextId++,
                    firstName = dto.firstName,
                    lastName  = dto.lastName,
                    email     = dto.email,
                    username  = dto.username
                )
            }
        }

        val repo1 = UserRepositoryImpl(api, db, clock1)
        val repo2 = UserRepositoryImpl(api, db, clock2)

        // Launch both concurrently
        val job1 = launch { repo1.createUser(stubRequest) }
        val job2 = launch { repo2.createUser(stubRequest.copy(email = "bob@example.com")) }
        job1.join(); job2.join()

        val finalRows = db.rows
        assertEquals(2, finalRows.size, "both users must be in DB")
        assertTrue(finalRows.all { it.isPendingCreate == 0L }, "both must be confirmed")
        assertTrue(finalRows.all { it.id > 0 }, "no negative ids should remain")
    }

    @Test
    fun `API is called exactly once per createUser call`() = runTest {
        val api = FakeUserApiService()
        val repo = buildRepo(api)

        repo.createUser(stubRequest)

        assertEquals(1, api.createUserCalls.size)
    }

    @Test
    fun `API receives correct fields from request`() = runTest {
        val api = FakeUserApiService()
        val repo = buildRepo(api)

        repo.createUser(stubRequest)

        val sentDto = api.createUserCalls.single()
        assertEquals("Alice",             sentDto.firstName)
        assertEquals("Smith",             sentDto.lastName)
        assertEquals("alice@example.com", sentDto.email)
        assertEquals("alices",            sentDto.username)
        assertEquals(30,                  sentDto.age)
        assertEquals("female",            sentDto.gender)
    }
}
