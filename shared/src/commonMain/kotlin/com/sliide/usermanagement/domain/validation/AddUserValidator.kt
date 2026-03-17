package com.sliide.usermanagement.domain.validation

import com.sliide.usermanagement.domain.model.Gender

/**
 * Pure validation functions for the Add-User form.
 *
 * Every function is a total, side-effect-free mapping from input to
 * [FieldError]? — `null` means "no error". No I/O, no DI, no state.
 * This makes the entire object trivially unit-testable.
 *
 * ── Name rules ──────────────────────────────────────────────────────────────
 * Accepts any Unicode letter ([\\p{L}]) or combining mark ([\\p{M}]) plus
 * common connectors: space, ASCII hyphen, straight apostrophe ('), and the
 * Unicode right-single-quotation-mark (U+2019, as in "O\u2019Brien").
 * Digits and punctuation (@ # $ etc.) are rejected.
 *
 * ── Email rules ─────────────────────────────────────────────────────────────
 * Validated against a linear-time, ReDoS-safe pattern built from the RFC 5321
 * allowed characters. No nested quantifiers; domain labels are bounded to
 * max 63 characters ([a-zA-Z0-9-]{0,61} between two mandatory alphanumerics).
 * Length is checked before the regex so the engine never sees more than 254
 * characters.
 *
 * ── Username rules ──────────────────────────────────────────────────────────
 * ASCII-only: letters, digits, underscores, hyphens, and dots.
 * Min [USERNAME_MIN_LENGTH] / max [USERNAME_MAX_LENGTH] characters after trim.
 *
 * ── Age rules ───────────────────────────────────────────────────────────────
 * Parsed as a whole-number integer. Valid range: [AGE_MIN]..[AGE_MAX].
 */
object AddUserValidator {

    // ── Public bounds (exposed for tests and UI hint text) ────────────────────

    const val NAME_MIN_LENGTH     = 2
    const val NAME_MAX_LENGTH     = 50
    const val USERNAME_MIN_LENGTH = 3
    const val USERNAME_MAX_LENGTH = 30
    const val EMAIL_MAX_LENGTH    = 254   // RFC 5321 §4.5.3.1.3
    const val AGE_MIN             = 18
    const val AGE_MAX             = 120

    // ── Compiled patterns ─────────────────────────────────────────────────────

    /**
     * Matches names that contain only Unicode letters, combining marks,
     * spaces, hyphens, and apostrophes (straight or curly).
     *
     * `\p{L}` — any Unicode letter (Latin, Cyrillic, Arabic, CJK, …)
     * `\p{M}` — combining marks / diacritics (e.g. the accent in "é" when
     *           stored as two code points)
     * `\u2019` — RIGHT SINGLE QUOTATION MARK (common in names like O'Brien)
     */
    private val NAME_REGEX = Regex("""^[\p{L}\p{M}' \u2019\-]+$""")

    /**
     * ReDoS-safe email pattern derived from RFC 5321 allowed characters.
     *
     * Structure:  <local>@<domain>.<tld>
     *
     * Local part  — one or more RFC 5321 atext characters, optionally
     *               separated by single dots (no leading/trailing dot).
     * Domain      — labels of 1-63 chars separated by dots; each label
     *               starts and ends with an alnum, with alnum/hyphen in
     *               the middle (bounded `{0,61}` prevents catastrophic
     *               backtracking on malformed input).
     * TLD         — at least 2 ASCII letters.
     *
     * Safety guarantees:
     * • No nested quantifiers.
     * • Domain labels are explicitly bounded.
     * • Total length is pre-checked (≤ 254) so the engine input is finite.
     */
    private val EMAIL_REGEX = Regex(
        // local part: atext chars (RFC 5321 §4.1.2) + optional dot-separated segments
        """^[a-zA-Z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[a-zA-Z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*""" +
        // @ separator
        """@""" +
        // domain: first label (1-63 chars)
        """[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?""" +
        // additional labels (subdomains), each dot-separated
        """(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*""" +
        // mandatory TLD (at least 2 letters)
        """\.[a-zA-Z]{2,}$"""
    )

    /**
     * Username: ASCII letters, digits, underscores, hyphens, dots.
     * Length check is done separately for precise error messages.
     */
    private val USERNAME_CHARS_REGEX = Regex("""^[a-zA-Z0-9_.-]+$""")

    // ── Individual field validators ───────────────────────────────────────────

    /**
     * Validates [firstName] or [lastName].
     *
     * Accepts any Unicode name including accented characters (François),
     * Cyrillic (Иван), Arabic (محمد), CJK (张伟), hyphenated (Mary-Kate),
     * and names with apostrophes (O'Brien).
     */
    fun validateName(value: String): FieldError? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty()              -> FieldError.Required
            trimmed.length < NAME_MIN_LENGTH -> FieldError.TooShort
            trimmed.length > NAME_MAX_LENGTH -> FieldError.TooLong
            !NAME_REGEX.matches(trimmed)   -> FieldError.InvalidCharacters
            else                           -> null
        }
    }

    /**
     * Validates an email address.
     *
     * Trims surrounding whitespace before matching. Rejects addresses longer
     * than 254 characters (RFC 5321 maximum path length) before running the
     * regex so the engine never receives a pathologically long input.
     */
    fun validateEmail(value: String): FieldError? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty()                  -> FieldError.Required
            trimmed.length > EMAIL_MAX_LENGTH  -> FieldError.TooLong
            !EMAIL_REGEX.matches(trimmed)      -> FieldError.InvalidEmailFormat
            else                               -> null
        }
    }

    /**
     * Validates a username.
     *
     * Usernames are case-sensitive and may contain ASCII letters, digits,
     * underscores (`_`), hyphens (`-`), and dots (`.`).
     * No leading/trailing dot enforcement is intentional — the API accepts
     * them and the list display is not affected.
     */
    fun validateUsername(value: String): FieldError? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty()                      -> FieldError.Required
            trimmed.length < USERNAME_MIN_LENGTH   -> FieldError.TooShort
            trimmed.length > USERNAME_MAX_LENGTH   -> FieldError.TooLong
            !USERNAME_CHARS_REGEX.matches(trimmed) -> FieldError.InvalidCharacters
            else                                   -> null
        }
    }

    /**
     * Validates an age entered as a raw string (as typed in the text field).
     *
     * Returns [FieldError.InvalidAge] for non-integer inputs rather than
     * [FieldError.InvalidCharacters] so the UI can show a more helpful message
     * ("please enter a number" vs "invalid characters").
     */
    fun validateAge(value: String): FieldError? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return FieldError.Required
        val age = trimmed.toIntOrNull() ?: return FieldError.InvalidAge
        return when {
            age < AGE_MIN -> FieldError.AgeBelowMinimum
            age > AGE_MAX -> FieldError.AgeAboveMaximum
            else          -> null
        }
    }

    /**
     * Validates that a gender has been selected.
     * A `null` [value] means the user has not yet chosen from the picker.
     */
    fun validateGender(value: Gender?): FieldError? =
        if (value == null) FieldError.Required else null

}
