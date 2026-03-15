package com.sliide.usermanagement.domain.model

data class PaginationInfo(
    val total: Int,
    val skip: Int,
    val limit: Int
) {
    val hasMore: Boolean get() = skip + limit < total
}
