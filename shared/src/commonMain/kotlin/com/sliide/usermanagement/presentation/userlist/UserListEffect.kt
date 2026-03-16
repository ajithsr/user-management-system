package com.sliide.usermanagement.presentation.userlist

sealed interface UserListEffect {
    data class NavigateToDetail(val userId: Int) : UserListEffect
    data class ShowUndoDelete(val userId: Int, val userName: String) : UserListEffect
    data class ShowError(val message: String) : UserListEffect
}
