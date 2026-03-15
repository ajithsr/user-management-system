package com.sliide.usermanagement.presentation.userdetail

sealed interface UserDetailEffect {
    data object NavigateBack : UserDetailEffect
}
