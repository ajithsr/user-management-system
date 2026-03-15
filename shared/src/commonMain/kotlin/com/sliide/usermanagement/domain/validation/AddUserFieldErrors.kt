package com.sliide.usermanagement.domain.validation

/**
 * Container for per-field validation errors on the Add-User form.
 *
 * A `null` slot means the corresponding field has no error.
 * An all-null instance means the form is valid and ready to submit.
 *
 * This class is pure domain — no presentation imports. The presentation
 * layer is responsible for deciding which errors are currently *visible*
 * to the user (see [com.sliide.usermanagement.presentation.adduser.AddUserFormState.visibleErrors]).
 */
data class AddUserFieldErrors(
    val firstName : FieldError? = null,
    val lastName  : FieldError? = null,
    val email     : FieldError? = null,
    val username  : FieldError? = null,
    val age       : FieldError? = null,
    val gender    : FieldError? = null
) {
    /** True when at least one field carries an error. */
    val hasErrors: Boolean
        get() = firstName != null || lastName != null || email   != null ||
                username  != null || age      != null || gender  != null
}
