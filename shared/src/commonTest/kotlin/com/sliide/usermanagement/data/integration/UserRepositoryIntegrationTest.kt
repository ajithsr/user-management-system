package com.sliide.usermanagement.data.integration

import com.sliide.usermanagement.data.mapper.toEntity
import com.sliide.usermanagement.data.remote.KtorUserApiService
import com.sliide.usermanagement.data.remote.dto.AddressDto
import com.sliide.usermanagement.data.remote.dto.CompanyDto
import com.sliide.usermanagement.data.remote.dto.CreateUserResponseDto
import com.sliide.usermanagement.data.remote.dto.UserDto
import com.sliide.usermanagement.data.remote.dto.UsersResponseDto
import com.sliide.usermanagement.data.repository.UserRepositoryImpl
import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.Gender
import com.sliide.usermanagement.fake.FakeClock
import com.sliide.usermanagement.fake.FakeUserLocalDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json as installJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for [UserRepositoryImpl] backed by Ktor [MockEngine].
 *
 * Unlike the unit tests that swap [UserApiService] with [FakeUserApiService],
 * these tests exercise the full HTTP stack:
 *
 *   MockEngine (raw JSON bytes)
 *     → Ktor ContentNegotiation (kotlinx.serialization deserialisation)
 *       → [KtorUserApiService] (URL construction, parameter encoding)
 *         → [UserRepositoryImpl] (pagination logic, DB writes)
 *           → [FakeUserLocalDataSource] (in-memory assertions)
 *
 * This catches serialisation contract regressions, URL typos, and query-
 * parameter encoding bugs that are invisible to unit tests using fakes.
 *
 * Three areas are covered:
 *  A. Successful pagination fetch — URLs, query parameters, deserialisation,
 *     skip tracking, hasMore transitions, multi-page accumulation.
 *  B. Network failure fallback — exceptions, server errors, DB untouched,
 *     optimistic-create rollback.
 *  C. DB sync correctness — field mapping, createdAt/isPendingCreate/isDeleted
 *     preservation on re-fetch, stale-row removal on refresh.
 */
class UserRepositoryIntegrationTest {

    // ── Infrastructure ────────────────────────────────────────────────────────

    private val fixedInstant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    /** Matches the Json config in production NetworkModule. */
    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        isLenient          = true
        coerceInputValues  = true
    }

    private fun buildClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { installJson(jsonCodec) }
    }

    private fun buildRepo(
        engine : MockEngine,
        db     : FakeUserLocalDataSource = FakeUserLocalDataSource()
    ) = UserRepositoryImpl(
        remoteDataSource = KtorUserApiService(buildClient(engine)),
        localDataSource  = db,
        clock            = FakeClock(fixedInstant)
    )

    // ── JSON response helpers ─────────────────────────────────────────────────

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun usersJson(skip: Int = 0, count: Int = 3, total: Int = 100) =
        jsonCodec.encodeToString(UsersResponseDto(
            users  = List(count) { i -> stubUserDto(skip + i + 1) },
            total  = total,
            skip   = skip,
            limit  = UserRepositoryImpl.PAGE_SIZE
        ))

    private fun createJson(id: Int = 42) =
        jsonCodec.encodeToString(CreateUserResponseDto(
            id        = id,
            firstName = "Alice",
            lastName  = "Smith",
            email     = "alice@example.com",
            username  = "alice_smith"
        ))

    private fun stubUserDto(id: Int = 1) = UserDto(
        id        = id,
        firstName = "User",
        lastName  = "$id",
        username  = "u$id",
        email     = "u$id@example.com",
        phone     = "+1-000-0000",
        image     = "https://cdn.example.com/$id.jpg",
        age       = 25,
        gender    = "male",
        role      = "user",
        address   = AddressDto("123 St", "City", "State", "Country", "00000"),
        company   = CompanyDto("Corp", "Engineering", "Engineer")
    )

    private val stubCreateRequest = CreateUserRequest(
        firstName = "Alice",
        lastName  = "Smith",
        email     = "alice@example.com",
        username  = "alice_smith",
        age       = 30,
        gender    = Gender.Female
    )

    // ══════════════════════════════════════════════════════════════════════════
    // A. Successful pagination fetch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `loadNextPage sends GET to the correct URL path`() = runTest {
        var capturedPath = ""
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            respond(usersJson(total = 30), HttpStatusCode.OK, jsonHeaders)
        }
        buildRepo(engine).loadNextPage()
        assertEquals("/users", capturedPath)
    }

    @Test
    fun `loadNextPage forwards saved skip as a query parameter`() = runTest {
        var capturedSkip: String? = null
        val db = FakeUserLocalDataSource().apply { saveSkip(30) }
        val engine = MockEngine { request ->
            capturedSkip = request.url.parameters["skip"]
            respond(usersJson(skip = 30, total = 90), HttpStatusCode.OK, jsonHeaders)
        }
        buildRepo(engine, db).loadNextPage()
        assertEquals("30", capturedSkip)
    }

    @Test
    fun `loadNextPage forwards PAGE_SIZE as the limit query parameter`() = runTest {
        var capturedLimit: String? = null
        val engine = MockEngine { request ->
            capturedLimit = request.url.parameters["limit"]
            respond(usersJson(total = 60), HttpStatusCode.OK, jsonHeaders)
        }
        buildRepo(engine).loadNextPage()
        assertEquals(UserRepositoryImpl.PAGE_SIZE.toString(), capturedLimit)
    }

    @Test
    fun `loadNextPage uses the HTTP GET method`() = runTest {
        var capturedMethod = HttpMethod.DefaultMethods.first()
        val engine = MockEngine { request ->
            capturedMethod = request.method
            respond(usersJson(total = 30), HttpStatusCode.OK, jsonHeaders)
        }
        buildRepo(engine).loadNextPage()
        assertEquals(HttpMethod.Get, capturedMethod)
    }

    @Test
    fun `loadNextPage deserialises all users from the JSON response into the DB`() = runTest {
        val db = FakeUserLocalDataSource()
        val engine = MockEngine { respond(usersJson(count = 5, total = 5), HttpStatusCode.OK, jsonHeaders) }
        buildRepo(engine, db).loadNextPage()
        assertEquals(5, db.rows.size)
    }

    @Test
    fun `loadNextPage maps every field from JSON through the full deserialisation stack`() = runTest {
        val db = FakeUserLocalDataSource()
        val wire = UserDto(
            id = 7, firstName = "Bob", lastName = "Jones",
            username = "bjones", email = "bob@example.com", phone = "+44-7000",
            image = "https://img.example.com/7.jpg",
            age = 35, gender = "male", role = "admin",
            address = AddressDto("10 Elm St", "London", "England", "UK", "EC1A 1BB"),
            company = CompanyDto("TechCorp", "Platform", "Staff Engineer")
        )
        val engine = MockEngine {
            respond(
                jsonCodec.encodeToString(UsersResponseDto(listOf(wire), 1, 0, 30)),
                HttpStatusCode.OK, jsonHeaders
            )
        }
        buildRepo(engine, db).loadNextPage()

        val row = db.rows.single()
        assertEquals(7L,                                row.id)
        assertEquals("Bob",                             row.firstName)
        assertEquals("Jones",                           row.lastName)
        assertEquals("bjones",                          row.username)
        assertEquals("bob@example.com",                 row.email)
        assertEquals("+44-7000",                        row.phone)
        assertEquals("https://img.example.com/7.jpg",   row.avatarUrl)
        assertEquals(35L,                               row.age)
        assertEquals("MALE",                            row.gender)   // mapper uppercases
        assertEquals("ADMIN",                           row.role)    // mapper uppercases
        assertEquals("10 Elm St",                       row.street)
        assertEquals("London",                          row.city)
        assertEquals("England",                         row.state)
        assertEquals("UK",                              row.country)
        assertEquals("EC1A 1BB",                        row.postalCode)
        assertEquals("TechCorp",                        row.company)
        assertEquals("Platform",                        row.department)
        assertEquals("Staff Engineer",                  row.jobTitle)
    }

    @Test
    fun `loadNextPage advances saved skip by the count of users returned`() = runTest {
        val db = FakeUserLocalDataSource()
        val engine = MockEngine { respond(usersJson(count = 7, total = 100), HttpStatusCode.OK, jsonHeaders) }
        buildRepo(engine, db).loadNextPage()
        assertEquals(7, db.getSkip())
    }

    @Test
    fun `loadNextPage saves the total returned by the server`() = runTest {
        val db = FakeUserLocalDataSource()
        val engine = MockEngine { respond(usersJson(count = 3, total = 47), HttpStatusCode.OK, jsonHeaders) }
        buildRepo(engine, db).loadNextPage()
        assertEquals(47, db.getCachedTotal())
    }

    @Test
    fun `loadNextPage sets hasMore=true when newSkip is less than total`() = runTest {
        val engine = MockEngine { respond(usersJson(count = 30, total = 90), HttpStatusCode.OK, jsonHeaders) }
        val repo = buildRepo(engine)
        repo.loadNextPage()
        assertTrue(repo.paginationState.value.hasMore,
            "skip=30 < total=90 — more pages remain")
    }

    @Test
    fun `loadNextPage sets hasMore=false when newSkip equals total`() = runTest {
        val engine = MockEngine { respond(usersJson(count = 30, total = 30), HttpStatusCode.OK, jsonHeaders) }
        val repo = buildRepo(engine)
        repo.loadNextPage()
        assertFalse(repo.paginationState.value.hasMore,
            "skip=30 == total=30 — no more pages")
    }

    @Test
    fun `two sequential loadNextPage calls accumulate skip and store users from both pages`() = runTest {
        val db = FakeUserLocalDataSource()
        var call = 0
        val engine = MockEngine {
            respond(
                when (call++) {
                    0    -> usersJson(skip = 0, count = 3, total = 6)
                    else -> usersJson(skip = 3, count = 3, total = 6)
                },
                HttpStatusCode.OK, jsonHeaders
            )
        }
        val repo = buildRepo(engine, db)
        repo.loadNextPage()
        repo.loadNextPage()

        assertEquals(6, db.rows.size, "both pages must be persisted")
        assertEquals(6, db.getSkip(),  "skip must equal total users fetched")
        assertEquals(
            (1L..6L).toList(),
            db.rows.map { it.id }.sorted(),
            "IDs from page 1 (1–3) and page 2 (4–6) must all be present"
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // B. Network failure fallback
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `loadNextPage returns Result failure when the engine throws`() = runTest {
        val engine = MockEngine { throw RuntimeException("Connection refused") }
        val result = buildRepo(engine).loadNextPage()
        assertTrue(result.isFailure)
    }

    @Test
    fun `loadNextPage sets error message in pagination state on network exception`() = runTest {
        val engine = MockEngine { throw RuntimeException("Connection refused") }
        val repo = buildRepo(engine)
        repo.loadNextPage()
        assertEquals("Connection refused", repo.paginationState.value.error)
    }

    @Test
    fun `loadNextPage clears isLoadingMore after network failure`() = runTest {
        val engine = MockEngine { throw RuntimeException("Timeout") }
        val repo = buildRepo(engine)
        repo.loadNextPage()
        assertFalse(repo.paginationState.value.isLoadingMore)
    }

    @Test
    fun `loadNextPage on 500 server error does not write any rows to the DB`() = runTest {
        val db = FakeUserLocalDataSource()
        // 500 response with non-JSON body → deserialisation throws → runCatching catches
        val engine = MockEngine {
            respond("Internal Server Error", HttpStatusCode.InternalServerError, jsonHeaders)
        }
        buildRepo(engine, db).loadNextPage()
        assertTrue(db.rows.isEmpty(), "DB must be untouched after a failed page load")
    }

    @Test
    fun `loadNextPage on 500 does not advance saved skip`() = runTest {
        val db = FakeUserLocalDataSource().apply { saveSkip(30) }
        val engine = MockEngine {
            respond("Internal Server Error", HttpStatusCode.InternalServerError, jsonHeaders)
        }
        buildRepo(engine, db).loadNextPage()
        assertEquals(30, db.getSkip(), "skip must not change after a failed load")
    }

    @Test
    fun `refresh on network failure preserves pre-existing DB rows`() = runTest {
        val db = FakeUserLocalDataSource()
        db.upsertUsers(listOf(stubUserDto(1).toEntity(fixedInstant.toEpochMilliseconds())))

        val engine = MockEngine { throw RuntimeException("Offline") }
        buildRepo(engine, db).refresh()

        assertEquals(1, db.rows.size, "existing rows must survive a failed refresh")
    }

    @Test
    fun `createUser rolls back temp row when the POST throws`() = runTest {
        val db = FakeUserLocalDataSource()
        val engine = MockEngine { throw RuntimeException("POST failed") }
        buildRepo(engine, db).createUser(stubCreateRequest)
        assertTrue(db.rows.isEmpty(), "temp row must be hard-deleted after a POST failure")
    }

    @Test
    fun `createUser returns Result failure when the POST throws`() = runTest {
        val engine = MockEngine { throw RuntimeException("Timeout") }
        val result = buildRepo(engine).createUser(stubCreateRequest)
        assertTrue(result.isFailure)
        assertEquals("Timeout", result.exceptionOrNull()?.message)
    }

    @Test
    fun `createUser POST targets the correct URL path`() = runTest {
        var capturedPath = ""
        val engine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            respond(createJson(), HttpStatusCode.OK, jsonHeaders)
        }
        buildRepo(engine).createUser(stubCreateRequest)
        assertEquals("/users/add", capturedPath)
    }

    @Test
    fun `createUser POST uses the HTTP POST method`() = runTest {
        var capturedMethod = HttpMethod.Get
        val engine = MockEngine { request ->
            capturedMethod = request.method
            respond(createJson(), HttpStatusCode.OK, jsonHeaders)
        }
        buildRepo(engine).createUser(stubCreateRequest)
        assertEquals(HttpMethod.Post, capturedMethod)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // C. DB sync correctness
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `re-fetch does not overwrite createdAt for an existing row`() = runTest {
        val db = FakeUserLocalDataSource()
        val originalCreatedAt = 9_000_000L
        db.upsertUsers(listOf(stubUserDto(1).toEntity(originalCreatedAt)))

        // API returns the same user again
        val engine = MockEngine {
            respond(
                jsonCodec.encodeToString(UsersResponseDto(listOf(stubUserDto(1)), 1, 0, 30)),
                HttpStatusCode.OK, jsonHeaders
            )
        }
        buildRepo(engine, db).loadNextPage()

        assertEquals(originalCreatedAt, db.rows.single().createdAt,
            "createdAt must be stamped once on first insert and never overwritten")
    }

    @Test
    fun `re-fetch does not overwrite isPendingCreate for a pending row`() = runTest {
        val db = FakeUserLocalDataSource()
        // Seed a row that happens to share an ID with an API user but is still pending
        db.upsertUsers(listOf(
            stubUserDto(1).toEntity(fixedInstant.toEpochMilliseconds()).copy(isPendingCreate = 1L)
        ))

        val engine = MockEngine {
            respond(
                jsonCodec.encodeToString(UsersResponseDto(listOf(stubUserDto(1)), 1, 0, 30)),
                HttpStatusCode.OK, jsonHeaders
            )
        }
        buildRepo(engine, db).loadNextPage()

        assertEquals(1L, db.rows.single { it.id == 1L }.isPendingCreate,
            "re-fetch must not clear isPendingCreate — the pending row must remain pending")
    }

    @Test
    fun `re-fetch does not resurrect a soft-deleted row`() = runTest {
        val db = FakeUserLocalDataSource()
        db.upsertUsers(listOf(stubUserDto(1).toEntity(fixedInstant.toEpochMilliseconds())))
        db.softDeleteUser(1L)

        val engine = MockEngine {
            respond(
                jsonCodec.encodeToString(UsersResponseDto(listOf(stubUserDto(1)), 1, 0, 30)),
                HttpStatusCode.OK, jsonHeaders
            )
        }
        buildRepo(engine, db).loadNextPage()

        assertEquals(1L, db.rows.single { it.id == 1L }.isDeleted,
            "re-fetch must not clear isDeleted — the undo window must stay valid")
    }

    @Test
    fun `refresh removes stale API rows and replaces them with the fresh page`() = runTest {
        val db = FakeUserLocalDataSource()
        // Stale users: IDs 10, 11
        db.upsertUsers(listOf(
            stubUserDto(10).toEntity(fixedInstant.toEpochMilliseconds()),
            stubUserDto(11).toEntity(fixedInstant.toEpochMilliseconds())
        ))

        // Refresh returns completely different users: IDs 20, 21
        val engine = MockEngine {
            respond(
                jsonCodec.encodeToString(UsersResponseDto(
                    listOf(stubUserDto(20), stubUserDto(21)), 2, 0, 30
                )),
                HttpStatusCode.OK, jsonHeaders
            )
        }
        buildRepo(engine, db).refresh()

        val ids = db.rows.map { it.id }.sorted()
        assertFalse(ids.any { it == 10L || it == 11L }, "stale rows must be removed by refresh")
        assertEquals(listOf(20L, 21L), ids, "only the fresh rows from the server must remain")
    }

    @Test
    fun `createUser success promotes temp ID to the server-assigned ID in DB`() = runTest {
        val db = FakeUserLocalDataSource()
        val serverAssignedId = 101
        val engine = MockEngine { respond(createJson(id = serverAssignedId), HttpStatusCode.OK, jsonHeaders) }

        buildRepo(engine, db).createUser(stubCreateRequest)

        val confirmed = db.rows.singleOrNull { it.id == serverAssignedId.toLong() }
        assertNotNull(confirmed, "DB must contain a row with the server-assigned id=$serverAssignedId")
        assertEquals(0L, confirmed.isPendingCreate, "confirmed row must have isPendingCreate=0")
        assertFalse(db.rows.any { it.id < 0 }, "negative temp ID must be gone after confirmation")
    }

    @Test
    fun `deleteUser confirmDelete sends DELETE to the correct URL path`() = runTest {
        val db = FakeUserLocalDataSource()
        db.upsertUsers(listOf(stubUserDto(55).toEntity(fixedInstant.toEpochMilliseconds())))

        var deletePath = ""
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Delete) deletePath = request.url.encodedPath
            respond("{}", HttpStatusCode.OK, jsonHeaders)
        }
        val repo = buildRepo(engine, db)
        repo.softDeleteUser(55)
        repo.confirmDelete(55)

        assertEquals("/users/55", deletePath)
    }
}
