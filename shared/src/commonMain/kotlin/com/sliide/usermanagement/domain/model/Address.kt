package com.sliide.usermanagement.domain.model

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val country: String,
    val postalCode: String
) {
    /** Single-line representation suitable for list subtitles. */
    val cityRegion: String get() = "$city, $state"

    /** Full postal address as a single string. */
    val singleLine: String get() = "$street, $city, $state $postalCode, $country"
}
