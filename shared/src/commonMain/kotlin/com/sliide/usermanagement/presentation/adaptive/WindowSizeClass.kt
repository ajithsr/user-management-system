package com.sliide.usermanagement.presentation.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Coarse-grained bucket for available window width.
 *
 * Values align with Material 3 adaptive breakpoints:
 *   Compact  < 600 dp  — portrait phone
 *   Medium   600–839   — landscape phone, small tablet portrait
 *   Expanded ≥ 840     — tablet landscape, desktop
 *
 * Derived from [BoxWithConstraints.maxWidth] in [App], so it is always
 * the *available* width — window insets and nav bars already excluded.
 */
enum class WindowWidthClass { Compact, Medium, Expanded }

fun computeWindowWidthClass(availableWidth: Dp): WindowWidthClass = when {
    availableWidth < 600.dp -> WindowWidthClass.Compact
    availableWidth < 840.dp -> WindowWidthClass.Medium
    else                    -> WindowWidthClass.Expanded
}

/**
 * True for any layout wide enough to show a persistent detail pane.
 * Compact always uses full-screen push navigation.
 */
val WindowWidthClass.isTwoPane: Boolean
    get() = this != WindowWidthClass.Compact

/** Fractional weight of the list pane within the two-pane row. */
val WindowWidthClass.listPaneWeight: Float
    get() = when (this) {
        WindowWidthClass.Medium   -> 0.40f
        WindowWidthClass.Expanded -> 0.35f
        WindowWidthClass.Compact  -> 1.00f   // unused in single-pane mode
    }
