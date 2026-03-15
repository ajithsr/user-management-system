package com.sliide.usermanagement.data.remote.dto

import kotlinx.serialization.Serializable

/** Request body for POST /users/add. */
@Serializable
data class CreateUserDto(
    val firstName: String,
    val lastName: String,
    val email: String,
    val username: String,
    val age: Int,
    val gender: String     // "male" | "female" | "other"
)

/**
 * Response from POST /users/add.
 * DummyJSON echoes back the submitted fields plus a synthetic [id].
 * The server does not actually persist the record — this id is only
 * used to replace the local temporary ID so the row looks confirmed.
 */
@Serializable
data class CreateUserResponseDto(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val username: String
)
