package com.sliide.usermanagement.domain.model

data class Company(
    val name: String,
    val department: String,
    val jobTitle: String
) {
    val roleDescription: String get() = "$jobTitle · $department at $name"
}
