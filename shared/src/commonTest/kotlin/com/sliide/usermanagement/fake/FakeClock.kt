package com.sliide.usermanagement.fake

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Deterministic [Clock] for tests.
 *
 * [now] always returns [fixed] so any code that calls `clock.now()` is
 * fully reproducible — no timer, no system dependency.
 */
class FakeClock(val fixed: Instant) : Clock {
    override fun now(): Instant = fixed
}
