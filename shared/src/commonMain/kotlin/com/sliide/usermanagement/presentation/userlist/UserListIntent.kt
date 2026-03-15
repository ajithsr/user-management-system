package com.sliide.usermanagement.presentation.userlist

sealed interface UserListIntent {
    data object LoadInitial : UserListIntent
    data object LoadNextPage : UserListIntent
    data object Refresh : UserListIntent
    data class SelectUser(val userId: Int) : UserListIntent
    data object DismissError : UserListIntent
}
