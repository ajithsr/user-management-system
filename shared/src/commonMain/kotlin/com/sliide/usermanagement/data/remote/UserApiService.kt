package com.sliide.usermanagement.data.remote

import com.sliide.usermanagement.data.remote.dto.CreateUserDto
import com.sliide.usermanagement.data.remote.dto.CreateUserResponseDto
import com.sliide.usermanagement.data.remote.dto.UsersResponseDto

/**
 * Contract for the remote user data source.
 * Production implementation: [KtorUserApiService].
 * Test implementation: any in-memory fake.
 */
interface UserApiService {
    suspend fun getUsers(skip: Int, limit: Int): UsersResponseDto
    suspend fun createUser(dto: CreateUserDto): CreateUserResponseDto
    suspend fun deleteUser(id: Int)
}
