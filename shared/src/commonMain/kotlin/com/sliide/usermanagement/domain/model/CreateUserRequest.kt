package com.sliide.usermanagement.domain.model

/**
 * Input to [com.sliide.usermanagement.domain.repository.UserRepository.createUser].
 * Carries only the fields required to create a new user; the repository
 * generates the rest (temporary ID, createdAt, blank address/company).
 */
data class CreateUserRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val username: String,
    val age: Int,
    val gender: Gender
)
