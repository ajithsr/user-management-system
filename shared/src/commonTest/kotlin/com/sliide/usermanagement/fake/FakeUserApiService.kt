package com.sliide.usermanagement.fake

import com.sliide.usermanagement.data.remote.UserApiService
import com.sliide.usermanagement.data.remote.dto.CreateUserDto
import com.sliide.usermanagement.data.remote.dto.CreateUserResponseDto
import com.sliide.usermanagement.data.remote.dto.UsersResponseDto

/**
 * In-memory [UserApiService] for tests.
 *
 * Behaviour is controlled per call via the lambda properties:
 *  - Set [createUserResult] to a lambda that returns the desired [CreateUserResponseDto]
 *    or throws to simulate network failure.
 *  - [getUsersResult] and [deleteUserResult] have sensible defaults so tests
 *    that don't care about those endpoints don't need to configure them.
 *
 * All calls are recorded in the corresponding `*Calls` lists for assertions.
 */
class FakeUserApiService : UserApiService {

    // ── Configuration ─────────────────────────────────────────────────────────

    var createUserResult: suspend (CreateUserDto) -> CreateUserResponseDto =
        { dto ->
            CreateUserResponseDto(
                id        = 999,
                firstName = dto.firstName,
                lastName  = dto.lastName,
                email     = dto.email,
                username  = dto.username
            )
        }

    var getUsersResult: suspend (Int, Int) -> UsersResponseDto =
        { _, _ -> UsersResponseDto(users = emptyList(), total = 0, skip = 0, limit = 30) }

    var deleteUserResult: suspend (Int) -> Unit = { }

    // ── Call recording ────────────────────────────────────────────────────────

    val createUserCalls = mutableListOf<CreateUserDto>()
    val deleteUserCalls = mutableListOf<Int>()

    // ── Implementation ────────────────────────────────────────────────────────

    override suspend fun getUsers(skip: Int, limit: Int): UsersResponseDto =
        getUsersResult(skip, limit)

    override suspend fun createUser(dto: CreateUserDto): CreateUserResponseDto {
        createUserCalls += dto
        return createUserResult(dto)
    }

    override suspend fun deleteUser(id: Int) {
        deleteUserCalls += id
        deleteUserResult(id)
    }
}
