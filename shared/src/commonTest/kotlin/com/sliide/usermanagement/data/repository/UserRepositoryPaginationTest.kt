package com.sliide.usermanagement.data.repository

import com.sliide.usermanagement.data.mapper.toEntity
import com.sliide.usermanagement.data.remote.dto.AddressDto
import com.sliide.usermanagement.data.remote.dto.CompanyDto
import com.sliide.usermanagement.data.remote.dto.UserDto
import com.sliide.usermanagement.data.remote.dto.UsersResponseDto
import com.sliide.usermanagement.fake.FakeClock
import com.sliide.usermanagement.fake.FakeUserApiService
import com.sliide.usermanagement.fake.FakeUserLocalDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for pagination calculation inside [UserRepositoryImpl].
 *
 * Covers:
 *  - [loadNextPage]: skip tracking, hasMore transitions, dedup guards,
 *    [initPaginationIfNeeded] warm/cold start
 *  - [refresh]: always reloads from 0, preserves pending/soft-deleted rows
 *  - [fetchLastPage]: skip arithmetic for exact and partial page sizes,
 *    probe-on-cold-start vs cached-total-on-warm-start
 */
class UserRepositoryPaginationTest {

    private val fixedInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val PAGE_SIZE    = UserRepositoryImpl.PAGE_SIZE

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRepo(
        api: FakeUserApiService    = FakeUserApiService(),
        db : FakeUserLocalDataSource = FakeUserLocalDataSource()
    ) = UserRepositoryImpl(api, db, FakeClock(fixedInstant))

    private fun pageOf(skip: Int = 0, count: Int = PAGE_SIZE, total: Int = 100) =
        UsersResponseDto(
            users  = List(count) { i -> stubDto(id = skip + i + 1) },
            total  = total,
            skip   = skip,
            limit  = PAGE_SIZE
        )

    private fun stubDto(id: Int = 1) = UserDto(
        id        = id,    firstName = "User",   lastName = "$id",
        username  = "u$id", email    = "u$id@x.com", phone = "+1",
        image     = "",    age      = 25,        gender = "male", role = "user",
        address   = AddressDto("St", "City", "State", "Country", "00000"),
        company   = CompanyDto("Corp", "Eng", "Engineer")
    )

    // ══════════════════════════════════════════════════════════════════════════
    // loadNextPage — basic flow
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `loadNextPage fetches page with skip=0 on first call`() = runTest {
        var capturedSkip = -1
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> capturedSkip = skip; pageOf(skip = skip, total = 60) }
        }
        buildRepo(api).loadNextPage()
        assertEquals(0, capturedSkip)
    }

    @Test fun `loadNextPage passes PAGE_SIZE as limit`() = runTest {
        var capturedLimit = -1
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, limit -> capturedLimit = limit; pageOf(skip = skip, total = 60) }
        }
        buildRepo(api).loadNextPage()
        assertEquals(PAGE_SIZE, capturedLimit)
    }

    @Test fun `loadNextPage upserts all returned users into the DB`() = runTest {
        val db  = FakeUserLocalDataSource()
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 5, total = 5) }
        }
        buildRepo(api, db).loadNextPage()
        assertEquals(5, db.rows.size)
    }

    @Test fun `loadNextPage advances skip by the count of users returned`() = runTest {
        val db  = FakeUserLocalDataSource()
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 30, total = 90) }
        }
        buildRepo(api, db).loadNextPage()
        assertEquals(30, db.getSkip())
    }

    @Test fun `loadNextPage returns Result success on happy path`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, total = 60) }
        }
        assertTrue(buildRepo(api).loadNextPage().isSuccess)
    }

    // ── hasMore transitions ───────────────────────────────────────────────────

    @Test fun `loadNextPage sets hasMore=true when newSkip is less than total`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 30, total = 90) }
        }
        val repo = buildRepo(api)
        repo.loadNextPage()
        // newSkip=30, total=90 → 30 < 90 → true
        assertTrue(repo.paginationState.value.hasMore)
    }

    @Test fun `loadNextPage sets hasMore=false when newSkip equals total`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 30, total = 30) }
        }
        val repo = buildRepo(api)
        repo.loadNextPage()
        // newSkip=30, total=30 → 30 < 30 is false
        assertFalse(repo.paginationState.value.hasMore)
    }

    @Test fun `loadNextPage clears isLoadingMore after success`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, total = 60) }
        }
        val repo = buildRepo(api)
        repo.loadNextPage()
        assertFalse(repo.paginationState.value.isLoadingMore)
    }

    // ── error handling ────────────────────────────────────────────────────────

    @Test fun `loadNextPage on failure returns Result failure`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { _, _ -> throw RuntimeException("timeout") }
        }
        assertTrue(buildRepo(api).loadNextPage().isFailure)
    }

    @Test fun `loadNextPage on failure sets error message in pagination state`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { _, _ -> throw RuntimeException("timeout") }
        }
        val repo = buildRepo(api)
        repo.loadNextPage()
        assertEquals("timeout", repo.paginationState.value.error)
    }

    @Test fun `loadNextPage on failure clears isLoadingMore`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { _, _ -> throw RuntimeException("offline") }
        }
        val repo = buildRepo(api)
        repo.loadNextPage()
        assertFalse(repo.paginationState.value.isLoadingMore)
    }

    // ── deduplication guards ──────────────────────────────────────────────────

    @Test fun `loadNextPage is a no-op when hasMore is false`() = runTest {
        var callCount = 0
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> callCount++; pageOf(skip = skip, count = 30, total = 30) }
        }
        val repo = buildRepo(api)
        repo.loadNextPage()   // sets hasMore=false
        callCount = 0
        repo.loadNextPage()   // must be skipped
        assertEquals(0, callCount, "API must not be called when hasMore=false")
    }

    @Test fun `loadNextPage no-op returns Result success`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 30, total = 30) }
        }
        val repo = buildRepo(api)
        repo.loadNextPage()
        assertTrue(repo.loadNextPage().isSuccess)
    }

    // ── initPaginationIfNeeded (warm / cold start) ────────────────────────────

    @Test fun `loadNextPage cold start with no cache defaults hasMore=true and calls API`() = runTest {
        var callCount = 0
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> callCount++; pageOf(skip = skip, total = 30) }
        }
        buildRepo(api).loadNextPage()
        assertEquals(1, callCount, "no cached total → assume more pages → fetch must proceed")
    }

    @Test fun `loadNextPage warm start with skip lt total seeds hasMore=true and proceeds`() = runTest {
        var callCount = 0
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> callCount++; pageOf(skip = skip, total = 90) }
        }
        val db = FakeUserLocalDataSource().apply { saveSkip(30); saveTotal(90) }
        buildRepo(api, db).loadNextPage()
        assertEquals(1, callCount, "skip=30 < total=90 → hasMore=true → API must be called")
    }

    @Test fun `loadNextPage warm start with skip ge total seeds hasMore=false — skips API`() = runTest {
        var callCount = 0
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> callCount++; pageOf(skip = skip, total = 60) }
        }
        // skip == total → hasMore=false
        val db = FakeUserLocalDataSource().apply { saveSkip(60); saveTotal(60) }
        buildRepo(api, db).loadNextPage()
        assertEquals(0, callCount, "skip=60 >= total=60 → hasMore=false → no API call")
    }

    @Test fun `loadNextPage warm start with skip gt total seeds hasMore=false`() = runTest {
        var callCount = 0
        val api = FakeUserApiService().apply {
            getUsersResult = { _, _ -> callCount++; pageOf(total = 30) }
        }
        // Stale DB: skip is ahead of total (e.g. after total shrank)
        val db = FakeUserLocalDataSource().apply { saveSkip(90); saveTotal(30) }
        buildRepo(api, db).loadNextPage()
        assertEquals(0, callCount, "skip=90 > total=30 → hasMore=false → no API call")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // refresh
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `refresh always fetches from skip=0 regardless of saved skip`() = runTest {
        var capturedSkip = -1
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> capturedSkip = skip; pageOf(skip = skip, total = 60) }
        }
        val db = FakeUserLocalDataSource().apply { saveSkip(60) }
        buildRepo(api, db).refresh()
        assertEquals(0, capturedSkip, "refresh must always start from the first page")
    }

    @Test fun `refresh resets saved skip to the size of the returned page`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 30, total = 90) }
        }
        val db = FakeUserLocalDataSource().apply { saveSkip(60) }
        buildRepo(api, db).refresh()
        assertEquals(30, db.getSkip())
    }

    @Test fun `refresh sets hasMore=true when returned page does not cover total`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 30, total = 90) }
        }
        val repo = buildRepo(api)
        repo.refresh()
        assertTrue(repo.paginationState.value.hasMore)
    }

    @Test fun `refresh sets hasMore=false when total fits in one page`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 15, total = 15) }
        }
        val repo = buildRepo(api)
        repo.refresh()
        assertFalse(repo.paginationState.value.hasMore)
    }

    @Test fun `refresh preserves rows where isPendingCreate=1`() = runTest {
        val db = FakeUserLocalDataSource()
        db.insertPendingUser(stubDto(id = -999).toEntity(fixedInstant.toEpochMilliseconds()))

        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 5, total = 5) }
        }
        buildRepo(api, db).refresh()

        assertTrue(
            db.rows.any { it.isPendingCreate == 1L },
            "in-flight optimistic create must survive a refresh"
        )
    }

    @Test fun `refresh preserves rows where isDeleted=1 so the undo window stays open`() = runTest {
        val db = FakeUserLocalDataSource()
        db.upsertUsers(listOf(stubDto(id = 42).toEntity(fixedInstant.toEpochMilliseconds())))
        db.softDeleteUser(42L)

        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 5, total = 5) }
        }
        buildRepo(api, db).refresh()

        assertTrue(
            db.rows.any { it.id == 42L && it.isDeleted == 1L },
            "soft-deleted row must survive refresh so undo window remains valid"
        )
    }

    @Test fun `refresh clears API rows that are not pending or soft-deleted`() = runTest {
        val db = FakeUserLocalDataSource()
        db.upsertUsers(listOf(stubDto(id = 1).toEntity(fixedInstant.toEpochMilliseconds())))

        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ ->
                // API no longer returns id=1
                UsersResponseDto(users = listOf(stubDto(id = 2)), total = 1, skip = skip, limit = PAGE_SIZE)
            }
        }
        buildRepo(api, db).refresh()

        assertFalse(db.rows.any { it.id == 1L }, "stale API row must be removed by refresh")
        assertTrue(db.rows.any  { it.id == 2L }, "new API row must be present after refresh")
    }

    @Test fun `refresh on failure sets error and clears isLoading`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { _, _ -> throw RuntimeException("503") }
        }
        val repo = buildRepo(api)
        repo.refresh()
        val state = repo.paginationState.value
        assertFalse(state.isLoading)
        assertEquals("503", state.error)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // fetchLastPage — skip arithmetic
    // ══════════════════════════════════════════════════════════════════════════

    @Test fun `fetchLastPage cold start makes a probe call to get total`() = runTest {
        val calls = mutableListOf<Pair<Int, Int>>()
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, limit ->
                calls += skip to limit
                if (limit == 1) UsersResponseDto(emptyList(), total = 90, skip = 0, limit = 1)
                else pageOf(skip = skip, count = 30, total = 90)
            }
        }
        buildRepo(api).fetchLastPage()

        assertEquals(2, calls.size, "cold start: probe + page = 2 API calls")
        val (probeSkip, probeLimit) = calls[0]
        assertEquals(0, probeSkip, "probe must request skip=0")
        assertEquals(1, probeLimit, "probe must request limit=1")
    }

    @Test fun `fetchLastPage cold start requests correct last-page skip`() = runTest {
        val calls = mutableListOf<Int>()
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, limit ->
                calls += skip
                if (limit == 1) UsersResponseDto(emptyList(), total = 90, skip = 0, limit = 1)
                else pageOf(skip = skip, count = 30, total = 90)
            }
        }
        buildRepo(api).fetchLastPage()
        // total=90, PAGE_SIZE=30 → fullPages=3 → skip=(3-1)*30=60
        assertEquals(60, calls[1], "page request must start at skip=60")
    }

    @Test fun `fetchLastPage warm start skips probe and uses one API call`() = runTest {
        val calls = mutableListOf<Pair<Int, Int>>()
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, limit ->
                calls += skip to limit
                pageOf(skip = skip, count = 30, total = 90)
            }
        }
        val db = FakeUserLocalDataSource().apply { saveTotal(90) }
        buildRepo(api, db).fetchLastPage()

        assertEquals(1, calls.size, "warm start must make exactly one API call — no probe")
    }

    @Test fun `fetchLastPage skip for exact multiple of page size — total=90 PAGE=30 skip=60`() = runTest {
        // fullPages = 90/30 = 3; total % PAGE_SIZE == 0 → skip = (3-1)*30 = 60
        var pageSkip = -1
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageSkip = skip; pageOf(skip = skip, total = 90) }
        }
        val db = FakeUserLocalDataSource().apply { saveTotal(90) }
        buildRepo(api, db).fetchLastPage()
        assertEquals(60, pageSkip)
    }

    @Test fun `fetchLastPage skip for partial last page — total=75 PAGE=30 skip=60`() = runTest {
        // fullPages = 75/30 = 2; 75 % 30 != 0 → skip = 2*30 = 60 (15 items on last page)
        var pageSkip = -1
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageSkip = skip; pageOf(skip = skip, count = 15, total = 75) }
        }
        val db = FakeUserLocalDataSource().apply { saveTotal(75) }
        buildRepo(api, db).fetchLastPage()
        assertEquals(60, pageSkip)
    }

    @Test fun `fetchLastPage skip when total equals one page — total=30 PAGE=30 skip=0`() = runTest {
        // fullPages = 30/30 = 1; total % PAGE_SIZE == 0 → skip = (1-1)*30 = 0
        var pageSkip = -1
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageSkip = skip; pageOf(skip = skip, count = 30, total = 30) }
        }
        val db = FakeUserLocalDataSource().apply { saveTotal(30) }
        buildRepo(api, db).fetchLastPage()
        assertEquals(0, pageSkip)
    }

    @Test fun `fetchLastPage skip when total is less than page size — total=10 PAGE=30 skip=0`() = runTest {
        // fullPages = 10/30 = 0; 10 % 30 != 0 → skip = 0*30 = 0 (maxOf(0, 0))
        var pageSkip = -1
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageSkip = skip; pageOf(skip = skip, count = 10, total = 10) }
        }
        val db = FakeUserLocalDataSource().apply { saveTotal(10) }
        buildRepo(api, db).fetchLastPage()
        assertEquals(0, pageSkip)
    }

    @Test fun `fetchLastPage always sets hasMore=false after success`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, count = 30, total = 90) }
        }
        val db = FakeUserLocalDataSource().apply { saveTotal(90) }
        val repo = buildRepo(api, db)
        repo.fetchLastPage()
        assertFalse(repo.paginationState.value.hasMore,
            "feed shows the last page — there is no next page to load")
    }

    @Test fun `fetchLastPage clears isLoading on success`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { skip, _ -> pageOf(skip = skip, total = 30) }
        }
        val db = FakeUserLocalDataSource().apply { saveTotal(30) }
        val repo = buildRepo(api, db)
        repo.fetchLastPage()
        assertFalse(repo.paginationState.value.isLoading)
    }

    @Test fun `fetchLastPage on failure sets error and clears isLoading`() = runTest {
        val api = FakeUserApiService().apply {
            getUsersResult = { _, _ -> throw RuntimeException("network error") }
        }
        val repo = buildRepo(api)
        repo.fetchLastPage()
        val state = repo.paginationState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }
}
