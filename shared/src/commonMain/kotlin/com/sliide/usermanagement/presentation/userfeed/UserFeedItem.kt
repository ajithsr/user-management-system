package com.sliide.usermanagement.presentation.userfeed

import com.sliide.usermanagement.domain.model.User

/**
 * Presentation-layer wrapper around [User].
 *
 * Pre-computes display strings so the composable is pure layout code.
 * Re-created every 60 s by the ViewModel's tick flow to keep [createdAt]
 * relative timestamps current without issuing any network calls.
 */
data class UserFeedItem(
    val id: Int,
    val initials: String,
    val fullName: String,
    val email: String,
    val role: String,
    val location: String,
    val avatarUrl: String,
    /** e.g. "3 hours ago", "just now" */
    val createdAt: String,
    val isPending: Boolean,
    val isAdmin: Boolean
)
