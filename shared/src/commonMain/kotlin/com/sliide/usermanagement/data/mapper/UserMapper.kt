package com.sliide.usermanagement.data.mapper

import com.sliide.usermanagement.data.local.db.UserEntity
import com.sliide.usermanagement.data.remote.dto.CreateUserDto
import com.sliide.usermanagement.data.remote.dto.UserDto
import com.sliide.usermanagement.domain.model.Address
import com.sliide.usermanagement.domain.model.Company
import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.Gender
import com.sliide.usermanagement.domain.model.User
import com.sliide.usermanagement.domain.model.UserRole
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// ── DTO → Entity (API fetch path) ────────────────────────────────────────────

/**
 * Maps an API response to a flat database entity.
 *
 * [createdAt] defaults to now() so callers don't need to pass it. Tests
 * can inject a deterministic timestamp without touching production call sites.
 *
 * Transformations:
 *  - Nested address / company → flattened scalar columns.
 *  - gender / role strings → normalised uppercase DB constants.
 *  - image → avatarUrl (intent-revealing rename).
 *  - firstName / lastName kept separate (DB sort/filter capability).
 *  - isPendingCreate = 0  — API rows are always confirmed.
 *  - isDeleted = 0        — API rows are never pre-deleted.
 */
fun UserDto.toEntity(
    createdAt: Long = Clock.System.now().toEpochMilliseconds()
): UserEntity = UserEntity(
    id              = id.toLong(),
    firstName       = firstName,
    lastName        = lastName,
    username        = username,
    email           = email,
    phone           = phone,
    avatarUrl       = image,
    age             = age.toLong(),
    gender          = gender.toStoredGender(),
    role            = role.toStoredRole(),
    street          = address.address,
    city            = address.city,
    state           = address.state,
    country         = address.country,
    postalCode      = address.postalCode,
    company         = company.name,
    department      = company.department,
    jobTitle        = company.title,
    isPendingCreate = 0L,
    isDeleted       = 0L,
    createdAt       = createdAt
)

// ── CreateUserRequest → DTO (optimistic create API call) ─────────────────────

/** Maps the domain creation request to the API wire format. */
fun CreateUserRequest.toCreateDto(): CreateUserDto = CreateUserDto(
    firstName = firstName,
    lastName  = lastName,
    email     = email,
    username  = username,
    age       = age,
    gender    = gender.toApiValue()
)

/**
 * Creates a temporary [UserEntity] with a negative [tempId] and
 * [isPendingCreate] = 1. The row is visible immediately in [selectAll]
 * so the UI renders it before the network call returns.
 *
 * Non-provided fields (address, company, etc.) default to empty strings;
 * they will be populated from the API response on confirmation.
 */
fun CreateUserRequest.toTempEntity(
    tempId: Long,
    createdAt: Long = Clock.System.now().toEpochMilliseconds()
): UserEntity = UserEntity(
    id              = tempId,
    firstName       = firstName,
    lastName        = lastName,
    username        = username,
    email           = email,
    phone           = "",
    avatarUrl       = "",
    age             = age.toLong(),
    gender          = gender.toStoredValue(),
    role            = UserRole.User.toStoredValue(),
    street          = "",
    city            = "",
    state           = "",
    country         = "",
    postalCode      = "",
    company         = "",
    department      = "",
    jobTitle        = "",
    isPendingCreate = 1L,
    isDeleted       = 0L,
    createdAt       = createdAt
)

// ── Entity → Domain ──────────────────────────────────────────────────────────

/**
 * Maps a stored DB row to the pure domain model.
 *
 * Transformations:
 *  - firstName + lastName → fullName (API split is an implementation detail).
 *  - Scalar address columns → Address value object.
 *  - Scalar company columns → Company value object.
 *  - DB gender/role strings → typed enum / sealed class.
 *  - createdAt epoch millis → Instant.
 *  - isPendingCreate Long → User.isPending Boolean.
 *  - Long columns → Int (SQLite INTEGER vs domain Int).
 */
fun UserEntity.toDomain(): User = User(
    id        = id.toInt(),
    fullName  = buildFullName(firstName, lastName),
    username  = username,
    email     = email,
    phone     = phone,
    avatarUrl = avatarUrl,
    age       = age.toInt(),
    gender    = gender.toDomainGender(),
    role      = role.toDomainRole(),
    address   = Address(
        street     = street,
        city       = city,
        state      = state,
        country    = country,
        postalCode = postalCode
    ),
    company   = Company(
        name       = company,
        department = department,
        jobTitle   = jobTitle
    ),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    isPending = isPendingCreate == 1L
)

// ── Private helpers ──────────────────────────────────────────────────────────

private fun buildFullName(firstName: String, lastName: String): String =
    "$firstName $lastName".trim()

/** API string → DB constant. Case-insensitive to tolerate future API drift. */
private fun String.toStoredGender(): String = when (trim().lowercase()) {
    "male"   -> "MALE"
    "female" -> "FEMALE"
    else     -> "OTHER"
}

/** Domain Gender → API wire value. */
private fun Gender.toApiValue(): String = when (this) {
    Gender.Male   -> "male"
    Gender.Female -> "female"
    Gender.Other  -> "other"
}

/** Domain Gender → DB constant (used when creating temp entities). */
private fun Gender.toStoredValue(): String = when (this) {
    Gender.Male   -> "MALE"
    Gender.Female -> "FEMALE"
    Gender.Other  -> "OTHER"
}

/** DB constant → typed Gender. */
private fun String.toDomainGender(): Gender = when (this) {
    "MALE"   -> Gender.Male
    "FEMALE" -> Gender.Female
    else     -> Gender.Other
}

/**
 * API string → DB constant.
 * Unknown values are uppercased and stored verbatim — [toDomainRole] surfaces
 * them as [UserRole.Unknown] so callers handle them explicitly rather than
 * silently receiving a wrong privilege level.
 */
private fun String.toStoredRole(): String = when (trim().lowercase()) {
    "admin"     -> "ADMIN"
    "moderator" -> "MODERATOR"
    "user"      -> "USER"
    else        -> uppercase()
}

/** Domain UserRole → DB constant (used when creating temp entities). */
private fun UserRole.toStoredValue(): String = when (this) {
    is UserRole.Admin     -> "ADMIN"
    is UserRole.Moderator -> "MODERATOR"
    is UserRole.User      -> "USER"
    is UserRole.Unknown   -> raw
}

/** DB constant → typed UserRole. */
private fun String.toDomainRole(): UserRole = when (this) {
    "ADMIN"     -> UserRole.Admin
    "MODERATOR" -> UserRole.Moderator
    "USER"      -> UserRole.User
    else        -> UserRole.Unknown(raw = this)
}
