package com.sliide.usermanagement.presentation.userdetail

import com.sliide.usermanagement.domain.model.User

data class UserDetailState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false
)
