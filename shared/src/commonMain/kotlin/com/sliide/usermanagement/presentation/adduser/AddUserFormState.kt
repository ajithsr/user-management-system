package com.sliide.usermanagement.presentation.adduser

import androidx.compose.runtime.Stable
import com.sliide.usermanagement.domain.validation.AddUserFieldErrors

/**
 * Immutable UI state for the Add-User form.
 *
 * ── Real-time validation strategy ────────────────────────────────────────────
 * [errors] is always kept up-to-date: it is recalculated on every keystroke.
 * However, showing errors immediately as the user starts typing is jarring.
 * The [visibleErrors] computed property applies this rule:
 *
 *  • Before [submitAttempted]: only show errors for fields in [touched]
 *    (fields that have received focus and then lost it — "blur").
 *  • After [submitAttempted]: show all errors so the user can see everything
 *    that needs fixing at once.
 *
 * ── Submit button state ───────────────────────────────────────────────────────
 * [canSubmit] is based on [errors] (not [visibleErrors]), so the button becomes
 * enabled the moment all fields are valid, even if some errors are still hidden.
 *
 * ── Stability ────────────────────────────────────────────────────────────────
 * @Stable is required because [touched] is a [Set], and [Set] is not annotated
 * as stable in the Compose runtime. Without it, every keystroke recompose would
 * force all six OutlinedTextField children to recompose regardless of whether
 * their specific field value changed.
 */
@Stable
data class AddUserFormState(
    val input           : AddUserFormInput   = AddUserFormInput(),
    val errors          : AddUserFieldErrors = AddUserFieldErrors(),
    val touched         : Set<AddUserFormField> = emptySet(),
    val submitAttempted : Boolean            = false,
    val isSubmitting    : Boolean            = false,
) {
    /**
     * Subset of [errors] that should currently be shown in the UI.
     *
     * Errors for untouched, un-submitted fields are suppressed so the user
     * doesn't see red text on a field they haven't interacted with yet.
     */
    val visibleErrors: AddUserFieldErrors
        get() {
            if (submitAttempted) return errors
            return AddUserFieldErrors(
                firstName = errors.firstName.takeIf { AddUserFormField.FirstName in touched },
                lastName  = errors.lastName .takeIf { AddUserFormField.LastName  in touched },
                email     = errors.email    .takeIf { AddUserFormField.Email     in touched },
                username  = errors.username .takeIf { AddUserFormField.Username  in touched },
                age       = errors.age      .takeIf { AddUserFormField.Age       in touched },
                gender    = errors.gender   .takeIf { AddUserFormField.Gender    in touched },
            )
        }

    /**
     * The submit button should be enabled when there are no errors and no
     * submission is already in flight.
     */
    val canSubmit: Boolean
        get() = !errors.hasErrors && !isSubmitting
}
