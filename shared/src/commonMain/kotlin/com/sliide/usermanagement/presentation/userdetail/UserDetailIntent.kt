package com.sliide.usermanagement.presentation.userdetail

sealed interface UserDetailIntent {
    data object NavigateBack : UserDetailIntent
    data object DeleteUser : UserDetailIntent
    data object ConfirmDelete : UserDetailIntent
    data object DismissDeleteDialog : UserDetailIntent
}
