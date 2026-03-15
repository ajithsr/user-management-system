package com.sliide.usermanagement.domain.model

import kotlinx.datetime.Instant

/**
 * Pure domain model. Zero coupling to API shape or database schema.
 *
 * Intentional differences from the API:
 *  - [fullName] replaces separate firstName/lastName — the split is a wire detail.
 *  - [avatarUrl] replaces [image] — the name conveys intent, not the API field.
 *  - [gender] and [role] are typed values — raw strings are a serialisation detail.
 *  - [createdAt] does not exist in the API — it is generated locally on first cache.
 *  - [address] and [company] are value objects — flattening is a DB detail.
 */
data class User(
    val id: Int,
    val fullName: String,
    val username: String,
    val email: String,
    val phone: String,
    val avatarUrl: String,
    val age: Int,
    val gender: Gender,
    val role: UserRole,
    val address: Address,
    val company: Company,
    val createdAt: Instant,
    /**
     * True when this user was created locally and the POST /users/add call
     * has not yet returned. The row exists in the DB with a negative temp ID.
     * The UI can show a loading indicator for pending rows.
     * Default false — only set to true by the mapper for isPendingCreate rows.
     */
    val isPending: Boolean = false
) {
    /** Two-letter initials derived from fullName, e.g. "Emily Johnson" → "EJ". */
    val initials: String
        get() = fullName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }

    val isAdmin: Boolean get() = role is UserRole.Admin
}
