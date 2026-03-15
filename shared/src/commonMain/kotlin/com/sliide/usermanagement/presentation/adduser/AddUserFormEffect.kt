package com.sliide.usermanagement.presentation.adduser

import com.sliide.usermanagement.domain.model.CreateUserRequest

/**
 * One-shot side-effects emitted by [AddUserFormViewModel].
 *
 * The parent screen observes these to coordinate with the rest of the app:
 * on [Submit], dispatch [com.sliide.usermanagement.presentation.userfeed.UserFeedIntent.CreateUser]
 * to [UserFeedViewModel]; on [Dismiss], close the bottom sheet / dialog.
 */
sealed interface AddUserFormEffect {

    /**
     * The form is valid. The parent should forward [request] to the use case
     * and close the form.
     */
    data class Submit(val request: CreateUserRequest) : AddUserFormEffect

    /** The user cancelled without submitting. */
    data object Dismiss : AddUserFormEffect
}
