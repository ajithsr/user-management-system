package com.sliide.usermanagement.data.remote

import com.sliide.usermanagement.data.remote.dto.CreateUserDto
import com.sliide.usermanagement.data.remote.dto.CreateUserResponseDto
import com.sliide.usermanagement.data.remote.dto.UsersResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** Ktor-backed implementation of [UserApiService]. Bound in [NetworkModule]. */
class KtorUserApiService(private val httpClient: HttpClient) : UserApiService {

    companion object {
        private const val BASE_URL = "https://dummyjson.com"
    }

    override suspend fun getUsers(skip: Int, limit: Int): UsersResponseDto =
        httpClient.get("$BASE_URL/users") {
            parameter("skip", skip)
            parameter("limit", limit)
        }.body()

    /**
     * POST /users/add — DummyJSON returns a synthetic response with a new id
     * but does not persist the record server-side. The response id is used
     * only to promote the local temp row to a confirmed state.
     */
    override suspend fun createUser(dto: CreateUserDto): CreateUserResponseDto =
        httpClient.post("$BASE_URL/users/add") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }.body()

    /**
     * DELETE /users/{id} — best-effort; DummyJSON acknowledges the call but
     * does not actually remove the record. The repository hard-deletes locally
     * regardless of this response.
     */
    override suspend fun deleteUser(id: Int) {
        httpClient.delete("$BASE_URL/users/$id")
    }
}
