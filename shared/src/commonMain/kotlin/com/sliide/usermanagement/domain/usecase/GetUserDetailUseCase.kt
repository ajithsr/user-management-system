package com.sliide.usermanagement.domain.usecase

import com.sliide.usermanagement.domain.model.User
import com.sliide.usermanagement.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow

class GetUserDetailUseCase(private val repository: UserRepository) {
    operator fun invoke(userId: Int): Flow<User?> = repository.getUserStream(userId)
}
