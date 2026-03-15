package com.sliide.usermanagement.presentation.userlist

sealed interface UserListEffect {
    data class NavigateToDetail(val userId: Int) : UserListEffect
}
