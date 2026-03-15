package com.sliide.usermanagement.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.abs

/**
 * Converts an [Instant] to a human-readable relative string ("5 minutes ago").
 *
 * Design constraints
 * ------------------
 * - Pure function — no side effects, no singletons, no system-clock access.
 * - Deterministic — [now] is always an explicit parameter so any caller can
 *   inject a fixed value and get a reproducible result. This is the only way
 *   to make relative-time logic unit-testable without mocking statics.
 * - KMP-compatible — depends only on `kotlinx-datetime` and `kotlin.math`,
 *   both of which compile to every KMP target.
 *
 * Usage
 * -----
 * ```
 * // Production — pass the current clock
 * RelativeTimeFormatter.format(user.createdAt, Clock.System)
 *
 * // Test — pass a fixed reference point
 * RelativeTimeFormatter.format(timestamp, fixedNow)
 * ```
 */
object RelativeTimeFormatter {

    // ── Primary entry point ───────────────────────────────────────────────────

    /**
     * Formats [timestamp] relative to [now].
     *
     * Ranges and labels:
     * ```
     *  0 – 59 s          → "just now"
     *  1 – 59 min         → "N minute(s) ago"
     *  1 – 23 h           → "N hour(s) ago"
     *  1 – 6 days         → "N day(s) ago"
     *  1 – 3 weeks        → "N week(s) ago"
     *  1 – 11 months      → "N month(s) ago"
     *  ≥ 1 year           → "N year(s) ago"
     * ```
     * Future timestamps mirror the above with "in N ..." phrasing.
     */
    fun format(timestamp: Instant, now: Instant): String {
        val diffSeconds = (now - timestamp).inWholeSeconds
        val isPast = diffSeconds >= 0
        val absDiff = abs(diffSeconds)

        return when {
            absDiff < MINUTE  -> if (isPast) "just now" else "in a moment"
            absDiff < HOUR    -> formatUnit(absDiff / MINUTE,  "minute", isPast)
            absDiff < DAY     -> formatUnit(absDiff / HOUR,    "hour",   isPast)
            absDiff < WEEK    -> formatUnit(absDiff / DAY,     "day",    isPast)
            absDiff < MONTH   -> formatUnit(absDiff / WEEK,    "week",   isPast)
            absDiff < YEAR    -> formatUnit(absDiff / MONTH,   "month",  isPast)
            else              -> formatUnit(absDiff / YEAR,    "year",   isPast)
        }
    }

    /** Convenience overload that reads the current time from [clock]. */
    fun format(timestamp: Instant, clock: Clock): String =
        format(timestamp, clock.now())

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun formatUnit(value: Long, unit: String, isPast: Boolean): String {
        val label = if (value == 1L) unit else "${unit}s"
        return if (isPast) "$value $label ago" else "in $value $label"
    }

    // ── Thresholds (seconds) ──────────────────────────────────────────────────

    private const val MINUTE = 60L
    private const val HOUR   = 3_600L
    private const val DAY    = 86_400L
    private const val WEEK   = 604_800L
    private const val MONTH  = 2_592_000L   // 30 days
    private const val YEAR   = 31_536_000L  // 365 days
}
