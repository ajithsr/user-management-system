package com.sliide.usermanagement.presentation.userlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class UserListViewModel(
    private val getUsersUseCase: GetUsersUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(UserListState())
    val state: StateFlow<UserListState> = _state.asStateFlow()

    private val _effects = Channel<UserListEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        // DB is the source of truth; the stream emits on every write.
        getUsersUseCase.usersStream
            .onEach { users -> _state.update { it.copy(users = users) } }
            .launchIn(viewModelScope)

        // Pagination loading/error flags live in the repository state.
        getUsersUseCase.paginationState
            .onEach { pagination ->
                _state.update {
                    it.copy(
                        isLoading    = pagination.isLoading,
                        isLoadingMore = pagination.isLoadingMore,
                        canLoadMore  = pagination.hasMore,
                        error        = pagination.error
                    )
                }
            }
            .launchIn(viewModelScope)

        // Seed the first page on cold start.
        viewModelScope.launch { getUsersUseCase.loadNextPage() }
    }

    fun processIntent(intent: UserListIntent) {
        when (intent) {
            is UserListIntent.LoadInitial  -> viewModelScope.launch { getUsersUseCase.refresh() }
            is UserListIntent.LoadNextPage -> viewModelScope.launch { getUsersUseCase.loadNextPage() }
            is UserListIntent.Refresh      -> viewModelScope.launch { getUsersUseCase.refresh() }
            is UserListIntent.SelectUser   -> viewModelScope.launch {
                _effects.send(UserListEffect.NavigateToDetail(intent.userId))
            }
            is UserListIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }
}
