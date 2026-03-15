package com.sliide.usermanagement.presentation.userfeed

/**
 * Immutable UI state for the Smart User Feed screen.
 *
 * Modelled as a sealed hierarchy so the composable can exhaustively switch
 * without any nullable fields or boolean flags.
 */
sealed interface UserFeedState {

    /** First load in progress — show full-screen spinner. */
    data object Loading : UserFeedState

    /** Data loaded successfully. [items] is never empty. */
    data class Success(
        val items: List<UserFeedItem>,
        /** True while a background refresh is running (stale-while-revalidate). */
        val isRefreshing: Boolean = false
    ) : UserFeedState

    /** First load succeeded but the server returned zero users. */
    data object Empty : UserFeedState

    /**
     * First load failed and no cached data is available.
     * [message] is shown in the error view; retry triggers [UserFeedIntent.Retry].
     */
    data class Error(val message: String) : UserFeedState
}
