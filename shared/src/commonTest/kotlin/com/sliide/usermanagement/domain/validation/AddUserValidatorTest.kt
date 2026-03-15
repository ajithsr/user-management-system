package com.sliide.usermanagement.domain.validation

import com.sliide.usermanagement.domain.model.Gender
import com.sliide.usermanagement.presentation.adduser.AddUserFormInput
import com.sliide.usermanagement.presentation.adduser.validate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exhaustive unit tests for [AddUserValidator].
 *
 * Every test invokes a pure function — no coroutines, no mocks, no DI.
 * Groups mirror the validator's own sections: name, email, username, age,
 * gender, and the full-form [AddUserValidator.validate] combinator.
 */
class AddUserValidatorTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // validateName
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `name - blank string returns Required`() {
        assertEquals(FieldError.Required, AddUserValidator.validateName(""))
    }

    @Test fun `name - whitespace-only returns Required`() {
        assertEquals(FieldError.Required, AddUserValidator.validateName("   "))
    }

    @Test fun `name - single character returns TooShort`() {
        assertEquals(FieldError.TooShort, AddUserValidator.validateName("A"))
    }

    @Test fun `name - exactly min length is valid`() {
        assertNull(AddUserValidator.validateName("Al"))
    }

    @Test fun `name - exactly max length is valid`() {
        assertNull(AddUserValidator.validateName("A".repeat(AddUserValidator.NAME_MAX_LENGTH)))
    }

    @Test fun `name - one over max length returns TooLong`() {
        val tooLong = "A".repeat(AddUserValidator.NAME_MAX_LENGTH + 1)
        assertEquals(FieldError.TooLong, AddUserValidator.validateName(tooLong))
    }

    @Test fun `name - plain ASCII first name is valid`() {
        assertNull(AddUserValidator.validateName("Alice"))
    }

    @Test fun `name - name with space (compound first name) is valid`() {
        assertNull(AddUserValidator.validateName("Anne Marie"))
    }

    @Test fun `name - hyphenated name is valid`() {
        assertNull(AddUserValidator.validateName("Mary-Kate"))
    }

    @Test fun `name - straight apostrophe is valid`() {
        assertNull(AddUserValidator.validateName("O'Brien"))
    }

    @Test fun `name - curly apostrophe U+2019 is valid`() {
        assertNull(AddUserValidator.validateName("O\u2019Malley"))
    }

    @Test fun `name - accented Latin characters are valid`() {
        assertNull(AddUserValidator.validateName("François"))
        assertNull(AddUserValidator.validateName("Ángel"))
        assertNull(AddUserValidator.validateName("Müller"))
        assertNull(AddUserValidator.validateName("Ñoño"))
    }

    @Test fun `name - Cyrillic characters are valid`() {
        assertNull(AddUserValidator.validateName("Иван"))
    }

    @Test fun `name - Arabic characters are valid`() {
        assertNull(AddUserValidator.validateName("محمد"))
    }

    @Test fun `name - CJK characters are valid`() {
        assertNull(AddUserValidator.validateName("张伟"))
    }

    @Test fun `name - leading or trailing whitespace is trimmed before validation`() {
        // Trimmed to "Al" which is exactly NAME_MIN — should be valid, not TooShort.
        assertNull(AddUserValidator.validateName("  Al  "))
    }

    @Test fun `name - digit in name returns InvalidCharacters`() {
        assertEquals(FieldError.InvalidCharacters, AddUserValidator.validateName("Alice2"))
    }

    @Test fun `name - at-sign in name returns InvalidCharacters`() {
        assertEquals(FieldError.InvalidCharacters, AddUserValidator.validateName("Alice@"))
    }

    @Test fun `name - numeric string returns InvalidCharacters`() {
        assertEquals(FieldError.InvalidCharacters, AddUserValidator.validateName("12345"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // validateEmail
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `email - blank string returns Required`() {
        assertEquals(FieldError.Required, AddUserValidator.validateEmail(""))
    }

    @Test fun `email - whitespace-only returns Required`() {
        assertEquals(FieldError.Required, AddUserValidator.validateEmail("   "))
    }

    @Test fun `email - exceeds 254 chars returns TooLong`() {
        val localPart = "a".repeat(AddUserValidator.EMAIL_MAX_LENGTH - "@example.com".length + 1)
        assertEquals(FieldError.TooLong, AddUserValidator.validateEmail("$localPart@example.com"))
    }

    @Test fun `email - simple valid address is accepted`() {
        assertNull(AddUserValidator.validateEmail("alice@example.com"))
    }

    @Test fun `email - dots in local part are valid`() {
        assertNull(AddUserValidator.validateEmail("alice.b.smith@example.com"))
    }

    @Test fun `email - plus in local part is valid`() {
        assertNull(AddUserValidator.validateEmail("alice+tag@example.com"))
    }

    @Test fun `email - subdomain in domain is valid`() {
        assertNull(AddUserValidator.validateEmail("alice@mail.example.com"))
    }

    @Test fun `email - long TLD is valid`() {
        assertNull(AddUserValidator.validateEmail("alice@example.photography"))
    }

    @Test fun `email - leading whitespace is trimmed before matching`() {
        assertNull(AddUserValidator.validateEmail("  alice@example.com  "))
    }

    @Test fun `email - missing at-sign returns InvalidEmailFormat`() {
        assertEquals(FieldError.InvalidEmailFormat, AddUserValidator.validateEmail("aliceexample.com"))
    }

    @Test fun `email - double at-sign returns InvalidEmailFormat`() {
        assertEquals(FieldError.InvalidEmailFormat, AddUserValidator.validateEmail("alice@@example.com"))
    }

    @Test fun `email - missing TLD returns InvalidEmailFormat`() {
        assertEquals(FieldError.InvalidEmailFormat, AddUserValidator.validateEmail("alice@example"))
    }

    @Test fun `email - single-char TLD returns InvalidEmailFormat`() {
        assertEquals(FieldError.InvalidEmailFormat, AddUserValidator.validateEmail("alice@example.c"))
    }

    @Test fun `email - local part starting with dot returns InvalidEmailFormat`() {
        assertEquals(FieldError.InvalidEmailFormat, AddUserValidator.validateEmail(".alice@example.com"))
    }

    @Test fun `email - local part ending with dot returns InvalidEmailFormat`() {
        assertEquals(FieldError.InvalidEmailFormat, AddUserValidator.validateEmail("alice.@example.com"))
    }

    @Test fun `email - space inside email returns InvalidEmailFormat`() {
        assertEquals(FieldError.InvalidEmailFormat, AddUserValidator.validateEmail("ali ce@example.com"))
    }

    @Test fun `email - only at-sign returns InvalidEmailFormat`() {
        assertEquals(FieldError.InvalidEmailFormat, AddUserValidator.validateEmail("@"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // validateUsername
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `username - blank string returns Required`() {
        assertEquals(FieldError.Required, AddUserValidator.validateUsername(""))
    }

    @Test fun `username - whitespace-only returns Required`() {
        assertEquals(FieldError.Required, AddUserValidator.validateUsername("  "))
    }

    @Test fun `username - two characters returns TooShort`() {
        assertEquals(FieldError.TooShort, AddUserValidator.validateUsername("ab"))
    }

    @Test fun `username - exactly min length is valid`() {
        assertNull(AddUserValidator.validateUsername("abc"))
    }

    @Test fun `username - exactly max length is valid`() {
        assertNull(AddUserValidator.validateUsername("a".repeat(AddUserValidator.USERNAME_MAX_LENGTH)))
    }

    @Test fun `username - one over max length returns TooLong`() {
        val tooLong = "a".repeat(AddUserValidator.USERNAME_MAX_LENGTH + 1)
        assertEquals(FieldError.TooLong, AddUserValidator.validateUsername(tooLong))
    }

    @Test fun `username - alphanumeric only is valid`() {
        assertNull(AddUserValidator.validateUsername("alice123"))
    }

    @Test fun `username - underscore is valid`() {
        assertNull(AddUserValidator.validateUsername("alice_bob"))
    }

    @Test fun `username - hyphen is valid`() {
        assertNull(AddUserValidator.validateUsername("alice-bob"))
    }

    @Test fun `username - dot is valid`() {
        assertNull(AddUserValidator.validateUsername("alice.bob"))
    }

    @Test fun `username - space returns InvalidCharacters`() {
        assertEquals(FieldError.InvalidCharacters, AddUserValidator.validateUsername("alice bob"))
    }

    @Test fun `username - at-sign returns InvalidCharacters`() {
        assertEquals(FieldError.InvalidCharacters, AddUserValidator.validateUsername("alice@bob"))
    }

    @Test fun `username - Unicode letter returns InvalidCharacters`() {
        assertEquals(FieldError.InvalidCharacters, AddUserValidator.validateUsername("àlice"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // validateAge
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `age - blank string returns Required`() {
        assertEquals(FieldError.Required, AddUserValidator.validateAge(""))
    }

    @Test fun `age - whitespace-only returns Required`() {
        assertEquals(FieldError.Required, AddUserValidator.validateAge("  "))
    }

    @Test fun `age - letters return InvalidAge`() {
        assertEquals(FieldError.InvalidAge, AddUserValidator.validateAge("abc"))
    }

    @Test fun `age - decimal number returns InvalidAge`() {
        assertEquals(FieldError.InvalidAge, AddUserValidator.validateAge("25.5"))
    }

    @Test fun `age - one below minimum returns AgeBelowMinimum`() {
        assertEquals(FieldError.AgeBelowMinimum, AddUserValidator.validateAge("${AddUserValidator.AGE_MIN - 1}"))
    }

    @Test fun `age - exactly minimum is valid`() {
        assertNull(AddUserValidator.validateAge("${AddUserValidator.AGE_MIN}"))
    }

    @Test fun `age - exactly maximum is valid`() {
        assertNull(AddUserValidator.validateAge("${AddUserValidator.AGE_MAX}"))
    }

    @Test fun `age - one above maximum returns AgeAboveMaximum`() {
        assertEquals(FieldError.AgeAboveMaximum, AddUserValidator.validateAge("${AddUserValidator.AGE_MAX + 1}"))
    }

    @Test fun `age - negative number returns AgeBelowMinimum`() {
        assertEquals(FieldError.AgeBelowMinimum, AddUserValidator.validateAge("-1"))
    }

    @Test fun `age - valid mid-range value is accepted`() {
        assertNull(AddUserValidator.validateAge("30"))
    }

    @Test fun `age - leading and trailing whitespace is trimmed`() {
        assertNull(AddUserValidator.validateAge("  30  "))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // validateGender
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `gender - null returns Required`() {
        assertEquals(FieldError.Required, AddUserValidator.validateGender(null))
    }

    @Test fun `gender - Male is valid`() {
        assertNull(AddUserValidator.validateGender(Gender.Male))
    }

    @Test fun `gender - Female is valid`() {
        assertNull(AddUserValidator.validateGender(Gender.Female))
    }

    @Test fun `gender - Other is valid`() {
        assertNull(AddUserValidator.validateGender(Gender.Other))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // validate(AddUserFormInput) — full-form combinator
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `validate - clean input produces no errors`() {
        val input = AddUserFormInput(
            firstName = "Alice",
            lastName  = "Smith",
            email     = "alice@example.com",
            username  = "alice_smith",
            age       = "30",
            gender    = Gender.Female,
        )
        val result = input.validate()
        assertNull(result.firstName, "firstName should have no error")
        assertNull(result.lastName,  "lastName should have no error")
        assertNull(result.email,     "email should have no error")
        assertNull(result.username,  "username should have no error")
        assertNull(result.age,       "age should have no error")
        assertNull(result.gender,    "gender should have no error")
        assertEquals(false, result.hasErrors, "hasErrors should be false")
    }

    @Test fun `validate - all-blank input populates all fields with Required`() {
        val result = AddUserValidator.validate(AddUserFormInput())
        assertEquals(FieldError.Required, result.firstName)
        assertEquals(FieldError.Required, result.lastName)
        assertEquals(FieldError.Required, result.email)
        assertEquals(FieldError.Required, result.username)
        assertEquals(FieldError.Required, result.age)
        assertEquals(FieldError.Required, result.gender)
        assertEquals(true, result.hasErrors, "hasErrors should be true")
    }

    @Test fun `validate - mixed errors are reported independently per field`() {
        val input = AddUserFormInput(
            firstName = "A",                   // TooShort
            lastName  = "Smith",               // valid
            email     = "not-an-email",        // InvalidEmailFormat
            username  = "ok_user",             // valid
            age       = "5",                   // AgeBelowMinimum
            gender    = Gender.Male,           // valid
        )
        val result = input.validate()
        assertEquals(FieldError.TooShort,            result.firstName)
        assertNull(result.lastName)
        assertEquals(FieldError.InvalidEmailFormat,  result.email)
        assertNull(result.username)
        assertEquals(FieldError.AgeBelowMinimum,     result.age)
        assertNull(result.gender)
        assertEquals(true, result.hasErrors)
    }
}
