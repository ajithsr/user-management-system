package com.sliide.usermanagement.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Skeleton placeholder that mirrors the real [UserListItem] by using the same
 * [ListItem] composable. The container colour is set via [ListItemDefaults.colors]
 * so it is not drawn over by M3's internal Surface.
 */
@Composable
fun ShimmerUserListItem(modifier: Modifier = Modifier) {
    val shimmerMod = Modifier.shimmer()

    ListItem(
        modifier = modifier,
        colors   = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        leadingContent = {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(shimmerMod)
            )
        },
        headlineContent = {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(shimmerMod)
            )
        },
        supportingContent = {
            Box(
                Modifier
                    .fillMaxWidth(0.75f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(shimmerMod)
            )
        },
        trailingContent = {
            Box(
                Modifier
                    .width(48.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(shimmerMod)
            )
        }
    )
}

/**
 * Emits [count] shimmer rows separated by explicit 4 dp [separatorColor] dividers,
 * including one at the very top — mirroring the [LazyColumn] layout in UserListPane
 * (surfaceVariant background, contentPadding vertical = 4 dp, spacedBy 4 dp).
 */
@Composable
fun ShimmerUserList(
    count: Int = 8,
    modifier: Modifier = Modifier
) {
    val separatorColor = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = modifier.background(separatorColor)
    ) {
        // Top separator — mirrors contentPadding(vertical = 4.dp)
        Box(Modifier.fillMaxWidth().height(4.dp).background(separatorColor))

        repeat(count) { index ->
            val alpha = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay((index * 30L).coerceAtMost(300L))
                alpha.animateTo(1f, tween(300))
            }
            ShimmerUserListItem(
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value }
            )
            // Row separator — mirrors Arrangement.spacedBy(4.dp)
            Box(Modifier.fillMaxWidth().height(4.dp).background(separatorColor))
        }
    }
}
