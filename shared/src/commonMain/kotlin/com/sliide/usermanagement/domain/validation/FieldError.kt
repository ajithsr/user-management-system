package com.sliide.usermanagement.domain.validation

/**
 * A typed violation for a single form field.
 *
 * Using distinct objects (not a string enum) keeps the validator pure and
 * lets the UI layer decide on the exact copy without touching validation logic.
 *
 * Shared across all fields so the same [Required] / [TooShort] / [TooLong]
 * case can be reused; field-specific cases are prefixed with the field name.
 */
sealed interface FieldError {

    // ── Generic ───────────────────────────────────────────────────────────────

    /** Field was left blank or contains only whitespace. */
    data object Required : FieldError

    /** Trimmed value is shorter than the field's minimum. */
    data object TooShort : FieldError

    /** Value exceeds the field's maximum length. */
    data object TooLong : FieldError

    /** Value contains characters not permitted for this field. */
    data object InvalidCharacters : FieldError

    // ── Email-specific ────────────────────────────────────────────────────────

    /** The value is non-empty but does not match a valid email structure. */
    data object InvalidEmailFormat : FieldError

    // ── Age-specific ──────────────────────────────────────────────────────────

    /** The value is non-empty but cannot be parsed as a whole number. */
    data object InvalidAge : FieldError

    /** Parsed age is below [AddUserValidator.AGE_MIN]. */
    data object AgeBelowMinimum : FieldError

    /** Parsed age is above [AddUserValidator.AGE_MAX]. */
    data object AgeAboveMaximum : FieldError
}
