package com.sliide.usermanagement.presentation.userdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sliide.usermanagement.domain.usecase.GetUserDetailUseCase
import com.sliide.usermanagement.domain.usecase.GetUsersUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserDetailViewModel(
    private val userId: Int,
    private val getUserDetailUseCase: GetUserDetailUseCase,
    private val getUsersUseCase: GetUsersUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(UserDetailState())
    val state: StateFlow<UserDetailState> = _state.asStateFlow()

    private val _effects = Channel<UserDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /**
     * Set to true once the user emits a non-null value. Used to distinguish
     * between "user not yet loaded" (still loading) and "user was loaded but
     * has since disappeared from the DB" (deleted or pending-create rolled
     * back). The latter should navigate back rather than show a spinner.
     */
    private var userWasLoaded = false

    init {
        observeUser()
    }

    fun processIntent(intent: UserDetailIntent) {
        when (intent) {
            is UserDetailIntent.NavigateBack      -> navigateBack()
            is UserDetailIntent.DeleteUser        -> _state.update { it.copy(showDeleteConfirmation = true) }
            is UserDetailIntent.DismissDeleteDialog -> _state.update { it.copy(showDeleteConfirmation = false) }
            is UserDetailIntent.ConfirmDelete     -> confirmDelete()
        }
    }

    private fun observeUser() {
        getUserDetailUseCase(userId)
            .onEach { user ->
                when {
                    user != null -> {
                        userWasLoaded = true
                        _state.update { it.copy(user = user, isLoading = false) }
                    }
                    userWasLoaded -> {
                        // User was visible but is no longer in the DB:
                        // either deleted (soft→hard) or a pending-create was
                        // rolled back. Navigate away rather than showing a
                        // spinner that never resolves.
                        _effects.send(UserDetailEffect.NavigateBack)
                    }
                    else -> {
                        // User not yet in DB — show loading spinner.
                        _state.update { it.copy(isLoading = true) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun navigateBack() {
        viewModelScope.launch {
            _effects.send(UserDetailEffect.NavigateBack)
        }
    }

    private fun confirmDelete() {
        _state.update { it.copy(showDeleteConfirmation = false) }
        viewModelScope.launch {
            getUsersUseCase.softDeleteUser(userId)
                .onSuccess { getUsersUseCase.confirmDelete(userId) }
                .onFailure { _effects.send(UserDetailEffect.NavigateBack) }
        }
    }
}
