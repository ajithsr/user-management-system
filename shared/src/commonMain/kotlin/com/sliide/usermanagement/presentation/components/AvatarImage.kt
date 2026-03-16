package com.sliide.usermanagement.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage

/**
 * Circular avatar image with a shimmer sweep while loading and a neutral
 * filled circle on error. The caller controls size via [modifier]; clipping
 * and content-scale are handled internally.
 *
 * Usage:
 * ```
 * AvatarImage(
 *     model              = user.avatarUrl,
 *     contentDescription = user.fullName,
 *     modifier           = Modifier.size(48.dp)
 * )
 * ```
 */
@Composable
fun AvatarImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    SubcomposeAsyncImage(
        model              = model,
        contentDescription = contentDescription,
        contentScale       = ContentScale.Crop,
        modifier           = modifier.clip(CircleShape),
        loading            = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shimmer()
            )
        },
        error              = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    )
}
