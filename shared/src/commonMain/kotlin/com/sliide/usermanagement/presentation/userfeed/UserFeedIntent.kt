package com.sliide.usermanagement.presentation.userfeed

import com.sliide.usermanagement.domain.model.CreateUserRequest

/** User actions that the feed screen can dispatch to [UserFeedViewModel]. */
sealed interface UserFeedIntent {

    /** Pull-to-refresh or "Try Again" button on [UserFeedState.Error]. */
    data object Retry : UserFeedIntent

    /** User tapped a feed card. */
    data class OpenUser(val userId: Int) : UserFeedIntent

    /**
     * User submitted the "Add user" form.
     * The ViewModel immediately writes a pending row to the DB (optimistic),
     * then fires the network call in the background.
     */
    data class CreateUser(val request: CreateUserRequest) : UserFeedIntent

    /**
     * User swiped or tapped delete on a card.
     *
     * [userName] is passed from the UI so the snackbar can display
     * "Alice deleted — Undo" without an extra DB lookup.
     *
     * The ViewModel soft-deletes immediately (row disappears, animation fires),
     * then starts a 5-second countdown to [confirmDelete][UserFeedIntent].
     */
    data class DeleteUser(val userId: Int, val userName: String) : UserFeedIntent

    /**
     * User tapped "Undo" on the delete snackbar.
     *
     * Must arrive within the 5-second window; the ViewModel cancels the
     * pending confirm job and restores the row.
     */
    data class UndoDelete(val userId: Int) : UserFeedIntent
}
