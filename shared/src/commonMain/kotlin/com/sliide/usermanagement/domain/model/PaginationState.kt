package com.sliide.usermanagement.domain.model

/**
 * Snapshot of pagination loading state. Consumed by the UI to drive
 * progress indicators and error banners without coupling to the repository.
 */
data class PaginationState(
    val isLoading: Boolean = false,       // full refresh in progress
    val isLoadingMore: Boolean = false,   // appending next page
    val hasMore: Boolean = true,          // false once skip >= total
    val error: String? = null             // last load/refresh error message
)
