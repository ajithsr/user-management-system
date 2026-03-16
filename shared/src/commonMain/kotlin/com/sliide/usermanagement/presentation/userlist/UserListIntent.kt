package com.sliide.usermanagement.presentation.userlist

sealed interface UserListIntent {
    data object LoadInitial : UserListIntent
    data object LoadNextPage : UserListIntent
    data object Refresh : UserListIntent
    data class SelectUser(val userId: Int) : UserListIntent
    data object DismissError : UserListIntent
    data class DeleteUser(val userId: Int, val userName: String) : UserListIntent
    data class UndoDelete(val userId: Int) : UserListIntent
}
