package com.sliide.usermanagement.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Applies an animated shimmer sweep to any composable.
 *
 * The highlight travels left-to-right across the component's bounds over
 * [durationMs] milliseconds. Colours are surface-relative so they work
 * in both light and dark themes.
 *
 * Usage:
 * ```
 * Box(modifier = Modifier.fillMaxWidth().height(16.dp).shimmer())
 * ```
 */
fun Modifier.shimmer(
    durationMs: Int = 1_200,
    baseColor: Color = Color(0xFFE0E0E0),
    highlightColor: Color = Color(0xFFF5F5F5)
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue  = -1f,
        targetValue   = 2f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    drawWithContent {
        drawContent()
        val width = size.width
        val startX = width * progress
        val brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start  = Offset(startX, 0f),
            end    = Offset(startX + width * 0.4f, 0f)
        )
        drawRect(brush = brush)
    }
}
