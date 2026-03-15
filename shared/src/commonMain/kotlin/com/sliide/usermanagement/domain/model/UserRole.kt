package com.sliide.usermanagement.domain.model

sealed class UserRole {
    data object Admin : UserRole()
    data object Moderator : UserRole()
    data object User : UserRole()

    /**
     * Preserves unrecognised role values from the API rather than silently
     * discarding them. Callers can pattern-match and treat Unknown as a
     * read-only, non-privileged role.
     */
    data class Unknown(val raw: String) : UserRole()
}
