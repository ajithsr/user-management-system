package com.sliide.usermanagement.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Skeleton placeholder that mirrors the geometry of the real [UserListItem]:
 * - 48 dp circle avatar on the left
 * - Two text lines (name + email) in the centre
 * - Short trailing label on the right
 *
 * All shapes are filled with [shimmer] so they sweep in unison.
 */
@Composable
fun ShimmerUserListItem(modifier: Modifier = Modifier) {
    val shimmerMod = Modifier.shimmer()

    Row(
        modifier          = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(shimmerMod)
        )

        Spacer(Modifier.width(16.dp))

        // Name + email lines
        Column(
            modifier      = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(shimmerMod)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(shimmerMod)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Trailing company label
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .then(shimmerMod)
        )
    }
    HorizontalDivider()
}

/**
 * Emits [count] shimmer rows inside a [Column] — handy for the initial
 * loading state where we have no real items yet.
 */
@Composable
fun ShimmerUserList(
    count: Int = 8,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        repeat(count) {
            ShimmerUserListItem()
        }
    }
}
