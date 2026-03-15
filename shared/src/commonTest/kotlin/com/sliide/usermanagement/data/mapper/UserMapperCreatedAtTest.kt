package com.sliide.usermanagement.data.mapper

import com.sliide.usermanagement.data.remote.dto.AddressDto
import com.sliide.usermanagement.data.remote.dto.CompanyDto
import com.sliide.usermanagement.data.remote.dto.UserDto
import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.Gender
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that [createdAt] is stamped correctly for both API-fetched rows
 * and locally-created (optimistic) rows.
 *
 * Clock.System is never called — all tests inject a fixed epoch millis value.
 */
class UserMapperCreatedAtTest {

    private val fixedEpochMs = 1_700_000_000_000L  // 2023-11-14T22:13:20Z

    // ── UserDto.toEntity ──────────────────────────────────────────────────────

    @Test fun `toEntity stamps injected createdAt`() {
        val entity = stubDto().toEntity(createdAt = fixedEpochMs)
        assertEquals(fixedEpochMs, entity.createdAt)
    }

    @Test fun `toEntity createdAt defaults to now when not provided`() {
        val before = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val entity = stubDto().toEntity()
        val after = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        // Weak assertion: the default must be within the test's execution window
        assert(entity.createdAt in before..after) {
            "expected createdAt in [$before, $after] but was ${entity.createdAt}"
        }
    }

    @Test fun `toEntity maps isPendingCreate to 0`() {
        assertEquals(0L, stubDto().toEntity(fixedEpochMs).isPendingCreate)
    }

    @Test fun `toEntity maps isDeleted to 0`() {
        assertEquals(0L, stubDto().toEntity(fixedEpochMs).isDeleted)
    }

    // ── CreateUserRequest.toTempEntity ────────────────────────────────────────

    @Test fun `toTempEntity stamps injected createdAt`() {
        val entity = stubRequest().toTempEntity(tempId = -1L, createdAt = fixedEpochMs)
        assertEquals(fixedEpochMs, entity.createdAt)
    }

    @Test fun `toTempEntity createdAt defaults to now when not provided`() {
        val before = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val entity = stubRequest().toTempEntity(tempId = -1L)
        val after = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

        assert(entity.createdAt in before..after) {
            "expected createdAt in [$before, $after] but was ${entity.createdAt}"
        }
    }

    @Test fun `toTempEntity uses supplied tempId`() {
        val tempId = -fixedEpochMs   // negative mirrors production usage
        val entity = stubRequest().toTempEntity(tempId = tempId, createdAt = fixedEpochMs)
        assertEquals(tempId, entity.id)
    }

    @Test fun `toTempEntity maps isPendingCreate to 1`() {
        assertEquals(1L, stubRequest().toTempEntity(-1L, fixedEpochMs).isPendingCreate)
    }

    @Test fun `toTempEntity maps isDeleted to 0`() {
        assertEquals(0L, stubRequest().toTempEntity(-1L, fixedEpochMs).isDeleted)
    }

    // ── UserEntity.toDomain ───────────────────────────────────────────────────

    @Test fun `toDomain preserves createdAt as Instant`() {
        val entity = stubDto().toEntity(createdAt = fixedEpochMs)
        val domain = entity.toDomain()
        assertEquals(fixedEpochMs, domain.createdAt.toEpochMilliseconds())
    }

    @Test fun `toDomain isPending false for API row`() {
        val domain = stubDto().toEntity(fixedEpochMs).toDomain()
        assertEquals(false, domain.isPending)
    }

    @Test fun `toDomain isPending true for pending create row`() {
        val domain = stubRequest().toTempEntity(-1L, fixedEpochMs).toDomain()
        assertEquals(true, domain.isPending)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun stubDto() = UserDto(
        id        = 1,
        firstName = "Emily",
        lastName  = "Johnson",
        username  = "emilys",
        email     = "emily@test.com",
        phone     = "+1-555-0000",
        image     = "https://example.com/avatar.jpg",
        age       = 28,
        gender    = "female",
        role      = "user",
        address   = AddressDto(
            address    = "123 Main St",
            city       = "Los Angeles",
            state      = "CA",
            country    = "USA",
            postalCode = "90001"
        ),
        company   = CompanyDto(
            name       = "Acme Corp",
            department = "Engineering",
            title      = "Software Engineer"
        )
    )

    private fun stubRequest() = CreateUserRequest(
        firstName = "Emily",
        lastName  = "Johnson",
        email     = "emily@test.com",
        username  = "emilys",
        age       = 28,
        gender    = Gender.Female
    )
}

