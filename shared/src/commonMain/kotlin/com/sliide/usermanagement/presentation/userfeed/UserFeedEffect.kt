package com.sliide.usermanagement.presentation.userfeed

import com.sliide.usermanagement.domain.model.User

/**
 * One-shot side effects emitted by [UserFeedViewModel].
 *
 * Delivered via a [kotlinx.coroutines.channels.Channel] so they are never
 * replayed on re-subscription (unlike [StateFlow]).
 */
sealed interface UserFeedEffect {

    /** Navigate to the user detail screen. */
    data class NavigateToDetail(val userId: Int) : UserFeedEffect

    /** Show a transient snackbar/toast for non-fatal errors. */
    data class ShowError(val message: String) : UserFeedEffect

    /**
     * User was successfully created (optimistic promotion complete).
     * [user] carries the confirmed domain object with the server-assigned ID
     * so the UI can scroll to or highlight the new row.
     */
    data class UserCreated(val user: User) : UserFeedEffect

    /**
     * A user was soft-deleted and the undo window is now open.
     *
     * The UI should show a snackbar: "[userName] deleted  ·  Undo"
     * and dispatch [UserFeedIntent.UndoDelete] if the action is tapped.
     *
     * The snackbar duration should match [UserFeedViewModel.UNDO_WINDOW_MS]
     * so it disappears exactly when the undo window closes.
     */
    data class ShowUndoDelete(val userId: Int, val userName: String) : UserFeedEffect
}
