package com.sliide.usermanagement.presentation.adduser

/**
 * Identifies a single field on the Add-User form.
 *
 * Used as the key in [AddUserFormState.touched] so the ViewModel knows which
 * fields have been blurred and should show validation errors immediately.
 */
enum class AddUserFormField {
    FirstName,
    LastName,
    Email,
    Username,
    Age,
    Gender,
}
