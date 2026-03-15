package com.sliide.usermanagement.presentation.adduser

import com.sliide.usermanagement.domain.model.Gender
import com.sliide.usermanagement.domain.validation.AddUserFieldErrors
import com.sliide.usermanagement.domain.validation.FieldError
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [AddUserFormState].
 *
 * No coroutines, no DI — the state is a plain data class with two computed
 * properties ([visibleErrors] and [canSubmit]) that must be correct across
 * all combinations of [touched] fields and [submitAttempted].
 */
class AddUserFormStateTest {

    // ── Test fixtures ─────────────────────────────────────────────────────────

    /** A fully valid form input — [validate] should produce no errors. */
    private val validInput = AddUserFormInput(
        firstName = "Alice",
        lastName  = "Smith",
        email     = "alice@example.com",
        username  = "alice_smith",
        age       = "30",
        gender    = Gender.Female,
    )

    /** A blank form input — all fields required, [validate] fills all errors. */
    private val blankInput = AddUserFormInput()

    /** Pre-computed error bag for a fully blank form. */
    private val allErrors: AddUserFieldErrors = blankInput.validate()

    /** Pre-computed error bag for a fully valid form (all null). */
    private val noErrors: AddUserFieldErrors = validInput.validate()

    // ══════════════════════════════════════════════════════════════════════════
    // visibleErrors — progressive disclosure (blur before submit)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `visibleErrors - no fields touched and not submitted - all errors are hidden`() {
        val state = AddUserFormState(
            input           = blankInput,
            errors          = allErrors,
            touched         = emptySet(),
            submitAttempted = false
        )
        val v = state.visibleErrors
        assertNull(v.firstName, "untouched firstName must not show an error")
        assertNull(v.lastName,  "untouched lastName must not show an error")
        assertNull(v.email,     "untouched email must not show an error")
        assertNull(v.username,  "untouched username must not show an error")
        assertNull(v.age,       "untouched age must not show an error")
        assertNull(v.gender,    "untouched gender must not show an error")
    }

    @Test
    fun `visibleErrors - only touched fields expose their error before submit`() {
        val state = AddUserFormState(
            input           = blankInput,
            errors          = allErrors,
            touched         = setOf(AddUserFormField.FirstName, AddUserFormField.Email),
            submitAttempted = false
        )
        val v = state.visibleErrors
        assertNotNull(v.firstName, "touched firstName must show its error")
        assertNotNull(v.email,     "touched email must show its error")
        assertNull(v.lastName,     "untouched lastName must remain hidden")
        assertNull(v.username,     "untouched username must remain hidden")
        assertNull(v.age,          "untouched age must remain hidden")
        assertNull(v.gender,       "untouched gender must remain hidden")
    }

    @Test
    fun `visibleErrors - submitAttempted reveals all errors regardless of touched`() {
        val state = AddUserFormState(
            input           = blankInput,
            errors          = allErrors,
            touched         = emptySet(),   // nothing touched
            submitAttempted = true
        )
        val v = state.visibleErrors
        assertNotNull(v.firstName, "firstName error must be visible after submit")
        assertNotNull(v.lastName,  "lastName error must be visible after submit")
        assertNotNull(v.email,     "email error must be visible after submit")
        assertNotNull(v.username,  "username error must be visible after submit")
        assertNotNull(v.age,       "age error must be visible after submit")
        assertNotNull(v.gender,    "gender error must be visible after submit")
    }

    @Test
    fun `visibleErrors - submitAttempted returns the full errors object unchanged`() {
        val state = AddUserFormState(
            input           = blankInput,
            errors          = allErrors,
            submitAttempted = true
        )
        assertTrue(state.visibleErrors === state.errors,
            "after submitAttempted, visibleErrors must return the same errors reference")
    }

    @Test
    fun `visibleErrors - single touched field with no error remains null`() {
        val state = AddUserFormState(
            input           = validInput,
            errors          = noErrors,
            touched         = setOf(AddUserFormField.FirstName),
            submitAttempted = false
        )
        assertNull(state.visibleErrors.firstName,
            "touched field with no error must still show null in visibleErrors")
    }

    @Test
    fun `visibleErrors - touching every field before submit shows all errors`() {
        val state = AddUserFormState(
            input           = blankInput,
            errors          = allErrors,
            touched         = AddUserFormField.entries.toSet(),
            submitAttempted = false
        )
        val v = state.visibleErrors
        assertNotNull(v.firstName)
        assertNotNull(v.lastName)
        assertNotNull(v.email)
        assertNotNull(v.username)
        assertNotNull(v.age)
        assertNotNull(v.gender)
    }

    @Test
    fun `visibleErrors - specific field error types are preserved through the filter`() {
        val input = validInput.copy(
            firstName = "A",     // TooShort
            email     = "bad",   // InvalidEmailFormat
            age       = "5",     // AgeBelowMinimum
        )
        val state = AddUserFormState(
            input           = input,
            errors          = input.validate(),
            touched         = setOf(
                AddUserFormField.FirstName,
                AddUserFormField.Email,
                AddUserFormField.Age
            ),
            submitAttempted = false
        )
        val v = state.visibleErrors
        assertTrue(v.firstName is FieldError.TooShort,          "firstName must be TooShort")
        assertTrue(v.email     is FieldError.InvalidEmailFormat, "email must be InvalidEmailFormat")
        assertTrue(v.age       is FieldError.AgeBelowMinimum,   "age must be AgeBelowMinimum")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // canSubmit
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `canSubmit - false when errors exist and not submitting`() {
        val state = AddUserFormState(
            input  = blankInput,
            errors = allErrors,
            isSubmitting = false
        )
        assertFalse(state.canSubmit)
    }

    @Test
    fun `canSubmit - false when no errors but isSubmitting is true`() {
        val state = AddUserFormState(
            input        = validInput,
            errors       = noErrors,
            isSubmitting = true
        )
        assertFalse(state.canSubmit, "in-flight submission must disable the button")
    }

    @Test
    fun `canSubmit - false when both errors and isSubmitting`() {
        val state = AddUserFormState(
            input        = blankInput,
            errors       = allErrors,
            isSubmitting = true
        )
        assertFalse(state.canSubmit)
    }

    @Test
    fun `canSubmit - true when no errors and not submitting`() {
        val state = AddUserFormState(
            input        = validInput,
            errors       = noErrors,
            isSubmitting = false
        )
        assertTrue(state.canSubmit, "submit must be enabled when form is clean and idle")
    }

    @Test
    fun `canSubmit uses errors not visibleErrors - button enabled even before all fields touched`() {
        // Only firstName is touched, but all other fields are valid.
        // visibleErrors would only show firstName's error; canSubmit must check underlying errors.
        val state = AddUserFormState(
            input           = validInput,
            errors          = noErrors,
            touched         = setOf(AddUserFormField.FirstName),
            submitAttempted = false,
            isSubmitting    = false
        )
        assertTrue(state.canSubmit,
            "canSubmit must be based on errors (all valid) not on which fields are visible")
    }

    @Test
    fun `canSubmit reflects live errors - becomes true when last error is fixed`() {
        val partialInput = validInput.copy(email = "bad")
        val stateWithError = AddUserFormState(
            input  = partialInput,
            errors = partialInput.validate()
        )
        assertFalse(stateWithError.canSubmit, "invalid email must block submit")

        val fixedInput = partialInput.copy(email = "alice@example.com")
        val fixedState = AddUserFormState(
            input  = fixedInput,
            errors = fixedInput.validate()
        )
        assertTrue(fixedState.canSubmit, "fixing the email must re-enable submit")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // errors.hasErrors
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `hasErrors - false when all fields are valid`() {
        assertFalse(noErrors.hasErrors)
    }

    @Test
    fun `hasErrors - true when any single field is invalid`() {
        // Each field alone
        assertTrue(validInput.copy(firstName = "").validate().hasErrors)
        assertTrue(validInput.copy(lastName  = "").validate().hasErrors)
        assertTrue(validInput.copy(email     = "bad").validate().hasErrors)
        assertTrue(validInput.copy(username  = "").validate().hasErrors)
        assertTrue(validInput.copy(age       = "0").validate().hasErrors)
        assertTrue(validInput.copy(gender    = null).validate().hasErrors)
    }

    @Test
    fun `hasErrors - true when all fields are invalid`() {
        assertTrue(allErrors.hasErrors)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Default state invariants
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `default AddUserFormState has no visible errors`() {
        val state = AddUserFormState()
        val v = state.visibleErrors
        // No fields touched, not submitted → all hidden
        assertNull(v.firstName)
        assertNull(v.lastName)
        assertNull(v.email)
        assertNull(v.username)
        assertNull(v.age)
        assertNull(v.gender)
    }

    @Test
    fun `default AddUserFormState canSubmit is false because form is blank`() {
        // The default AddUserFormState has an empty AddUserFormInput which produces
        // Required errors for every field — so canSubmit must be false.
        val defaultErrors = AddUserFormInput().validate()
        val state = AddUserFormState(errors = defaultErrors)
        assertFalse(state.canSubmit,
            "a freshly opened form must not be submittable")
    }
}
