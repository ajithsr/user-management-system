package com.sliide.usermanagement.presentation.userlist

import com.sliide.usermanagement.domain.model.User

data class UserListState(
    val users: List<User>       = emptyList(),
    val isLoading: Boolean      = false,
    val isLoadingMore: Boolean  = false,
    val canLoadMore: Boolean    = true,
    val error: String?          = null
)
