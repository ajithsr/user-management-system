package com.sliide.usermanagement.util

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * All tests use a fixed reference point (EPOCH) so results are fully
 * deterministic — no dependency on the system clock, no flakiness.
 *
 * Convention: positive offsets are in the PAST ("N ago"),
 *             negative offsets are in the FUTURE ("in N").
 */
class RelativeTimeFormatterTest {

    // Fixed "now" for all tests
    private val now = Instant.fromEpochSeconds(0)

    private fun past(seconds: Long)   = Instant.fromEpochSeconds(-seconds)
    private fun future(seconds: Long) = Instant.fromEpochSeconds(seconds)

    private fun fmt(timestamp: Instant) = RelativeTimeFormatter.format(timestamp, now)

    // ── "just now" / "in a moment" ────────────────────────────────────────────

    @Test fun `0 seconds ago returns just now`() =
        assertEquals("just now", fmt(now))

    @Test fun `30 seconds ago returns just now`() =
        assertEquals("just now", fmt(past(30)))

    @Test fun `59 seconds ago returns just now`() =
        assertEquals("just now", fmt(past(59)))

    @Test fun `30 seconds in the future returns in a moment`() =
        assertEquals("in a moment", fmt(future(30)))

    // ── Minutes ───────────────────────────────────────────────────────────────

    @Test fun `exactly 1 minute ago is singular`() =
        assertEquals("1 minute ago", fmt(past(60)))

    @Test fun `89 seconds rounds down to 1 minute ago`() =
        assertEquals("1 minute ago", fmt(past(89)))

    @Test fun `90 seconds rounds down to 1 minute ago`() =
        assertEquals("1 minute ago", fmt(past(90)))

    @Test fun `2 minutes ago is plural`() =
        assertEquals("2 minutes ago", fmt(past(120)))

    @Test fun `59 minutes ago`() =
        assertEquals("59 minutes ago", fmt(past(59 * 60)))

    @Test fun `5 minutes in the future`() =
        assertEquals("in 5 minutes", fmt(future(5 * 60)))

    // ── Hours ─────────────────────────────────────────────────────────────────

    @Test fun `exactly 1 hour ago is singular`() =
        assertEquals("1 hour ago", fmt(past(3_600)))

    @Test fun `2 hours ago is plural`() =
        assertEquals("2 hours ago", fmt(past(7_200)))

    @Test fun `23 hours ago stays in hours`() =
        assertEquals("23 hours ago", fmt(past(23 * 3_600)))

    @Test fun `3 hours in the future`() =
        assertEquals("in 3 hours", fmt(future(3 * 3_600)))

    // ── Days ──────────────────────────────────────────────────────────────────

    @Test fun `exactly 1 day ago is singular`() =
        assertEquals("1 day ago", fmt(past(86_400)))

    @Test fun `3 days ago is plural`() =
        assertEquals("3 days ago", fmt(past(3 * 86_400)))

    @Test fun `6 days ago stays in days`() =
        assertEquals("6 days ago", fmt(past(6 * 86_400)))

    // ── Weeks ─────────────────────────────────────────────────────────────────

    @Test fun `exactly 1 week ago is singular`() =
        assertEquals("1 week ago", fmt(past(604_800)))

    @Test fun `2 weeks ago is plural`() =
        assertEquals("2 weeks ago", fmt(past(2 * 604_800)))

    @Test fun `3 weeks ago stays in weeks`() =
        assertEquals("3 weeks ago", fmt(past(3 * 604_800)))

    // ── Months ────────────────────────────────────────────────────────────────

    @Test fun `exactly 1 month ago is singular`() =
        assertEquals("1 month ago", fmt(past(2_592_000)))

    @Test fun `6 months ago is plural`() =
        assertEquals("6 months ago", fmt(past(6 * 2_592_000)))

    @Test fun `11 months ago stays in months`() =
        assertEquals("11 months ago", fmt(past(11 * 2_592_000)))

    // ── Years ─────────────────────────────────────────────────────────────────

    @Test fun `exactly 1 year ago is singular`() =
        assertEquals("1 year ago", fmt(past(31_536_000)))

    @Test fun `2 years ago is plural`() =
        assertEquals("2 years ago", fmt(past(2 * 31_536_000)))

    @Test fun `5 years in the future`() =
        assertEquals("in 5 years", fmt(future(5 * 31_536_000)))

    // ── Boundary: each threshold is inclusive for the lower unit ──────────────

    @Test fun `one second before hour boundary is still in minutes`() =
        assertEquals("59 minutes ago", fmt(past(3_599)))

    @Test fun `one second before day boundary is still in hours`() =
        assertEquals("23 hours ago", fmt(past(86_399)))

    @Test fun `one second before week boundary is still in days`() =
        assertEquals("6 days ago", fmt(past(604_799)))

    @Test fun `one second before month boundary is still in weeks`() =
        assertEquals("4 weeks ago", fmt(past(2_591_999)))

    @Test fun `one second before year boundary is still in months`() =
        assertEquals("11 months ago", fmt(past(31_535_999)))
}
