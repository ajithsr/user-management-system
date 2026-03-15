package com.sliide.usermanagement.data.remote.dto

import kotlinx.serialization.Serializable

/** Top-level paginated response from GET /users. */
@Serializable
data class UsersResponseDto(
    val users: List<UserDto>,
    val total: Int,
    val skip: Int,
    val limit: Int
)

/**
 * Wire representation of a single user from the DummyJSON API.
 *
 * Only fields this app consumes are declared. Unknown JSON keys are silently
 * ignored by the Json { ignoreUnknownKeys = true } configuration in NetworkModule.
 *
 * Fields intentionally absent (must never be parsed or stored):
 *   password, ssn, ein       — credentials / tax IDs
 *   bank.*                   — payment card data (PCI DSS scope)
 *   crypto.*                 — financial wallet data
 *   ip, macAddress, userAgent — device fingerprinting (GDPR/CCPA concern)
 */
@Serializable
data class UserDto(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val username: String,
    val email: String,
    val phone: String,
    val image: String,
    val age: Int,
    val gender: String,       // "male" | "female"
    val address: AddressDto,
    val company: CompanyDto,
    val role: String          // "admin" | "moderator" | "user"
)

@Serializable
data class AddressDto(
    val address: String,      // street line
    val city: String,
    val state: String,
    val country: String,
    val postalCode: String
)

@Serializable
data class CompanyDto(
    val name: String,
    val department: String,
    val title: String         // job title
)
