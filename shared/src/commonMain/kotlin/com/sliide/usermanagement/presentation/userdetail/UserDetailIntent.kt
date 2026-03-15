package com.sliide.usermanagement.presentation.userdetail

sealed interface UserDetailIntent {
    data object NavigateBack : UserDetailIntent
}
