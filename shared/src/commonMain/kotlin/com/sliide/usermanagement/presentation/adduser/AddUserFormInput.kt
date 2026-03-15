package com.sliide.usermanagement.presentation.adduser

import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.Gender
import com.sliide.usermanagement.domain.validation.AddUserFieldErrors
import com.sliide.usermanagement.domain.validation.AddUserValidator

/**
 * Raw, unvalidated form inputs as the user typed them.
 *
 * All text fields are [String] — including [age] — so the text field shows
 * exactly what was typed without lossy parse/format round-trips.
 * [gender] is nullable because no option is pre-selected.
 */
data class AddUserFormInput(
    val firstName : String  = "",
    val lastName  : String  = "",
    val email     : String  = "",
    val username  : String  = "",
    val age       : String  = "",
    val gender    : Gender? = null,
)

/**
 * Validates every field in this input and returns an [AddUserFieldErrors] snapshot.
 *
 * This is a convenience combinator that lives in the presentation layer so that
 * [AddUserValidator] (domain) stays free of presentation imports.
 * [AddUserFieldErrors.hasErrors] == `false` means the form is clean.
 */
fun AddUserFormInput.validate(): AddUserFieldErrors = AddUserFieldErrors(
    firstName = AddUserValidator.validateName(firstName),
    lastName  = AddUserValidator.validateName(lastName),
    email     = AddUserValidator.validateEmail(email),
    username  = AddUserValidator.validateUsername(username),
    age       = AddUserValidator.validateAge(age),
    gender    = AddUserValidator.validateGender(gender),
)

/**
 * Converts validated form input to the domain request object.
 *
 * Returns `null` if age cannot be parsed or gender is missing — callers
 * should only invoke this after [AddUserValidator.validate] reports no errors.
 */
fun AddUserFormInput.toCreateUserRequest(): CreateUserRequest? {
    val parsedAge    = age.trim().toIntOrNull()    ?: return null
    val chosenGender = gender                      ?: return null
    return CreateUserRequest(
        firstName = firstName.trim(),
        lastName  = lastName.trim(),
        email     = email.trim(),
        username  = username.trim(),
        age       = parsedAge,
        gender    = chosenGender,
    )
}
