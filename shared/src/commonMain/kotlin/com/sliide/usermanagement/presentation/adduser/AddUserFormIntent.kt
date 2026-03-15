package com.sliide.usermanagement.presentation.adduser

import com.sliide.usermanagement.domain.model.Gender

/** Actions that the Add-User form UI can dispatch to [AddUserFormViewModel]. */
sealed interface AddUserFormIntent {

    // ── Field changes (fired on every keystroke) ──────────────────────────────

    data class UpdateFirstName(val value: String) : AddUserFormIntent
    data class UpdateLastName (val value: String) : AddUserFormIntent
    data class UpdateEmail    (val value: String) : AddUserFormIntent
    data class UpdateUsername (val value: String) : AddUserFormIntent
    data class UpdateAge      (val value: String) : AddUserFormIntent
    data class UpdateGender   (val value: Gender) : AddUserFormIntent

    // ── Focus events ──────────────────────────────────────────────────────────

    /**
     * Fired when a field loses focus. Adds the field to [AddUserFormState.touched]
     * so its error becomes visible if the field is invalid.
     */
    data class BlurField(val field: AddUserFormField) : AddUserFormIntent

    // ── Form actions ──────────────────────────────────────────────────────────

    /** User tapped the "Add" / "Save" button. */
    data object Submit : AddUserFormIntent

    /** User dismissed the form (back button or outside tap on a bottom sheet). */
    data object Dismiss : AddUserFormIntent
}
