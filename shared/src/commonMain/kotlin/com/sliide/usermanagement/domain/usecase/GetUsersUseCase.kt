package com.sliide.usermanagement.domain.usecase

import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.PaginationState
import com.sliide.usermanagement.domain.model.User
import com.sliide.usermanagement.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow

class GetUsersUseCase(private val repository: UserRepository) {
    val usersStream: Flow<List<User>> get() = repository.usersStream
    val paginationState: Flow<PaginationState> get() = repository.paginationState
    suspend fun loadNextPage(): Result<Unit> = repository.loadNextPage()
    suspend fun refresh(): Result<Unit> = repository.refresh()
    suspend fun createUser(request: CreateUserRequest): Result<User> = repository.createUser(request)
    suspend fun softDeleteUser(id: Int): Result<Unit> = repository.softDeleteUser(id)
    suspend fun undoDelete(id: Int): Result<Unit> = repository.undoDelete(id)
    suspend fun confirmDelete(id: Int): Result<Unit> = repository.confirmDelete(id)
    suspend fun fetchLastPage(): Result<Unit> = repository.fetchLastPage()
}
