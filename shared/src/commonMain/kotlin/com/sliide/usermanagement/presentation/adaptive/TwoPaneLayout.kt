package com.sliide.usermanagement.presentation.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sliide.usermanagement.presentation.userdetail.UserDetailPane
import com.sliide.usermanagement.presentation.userlist.UserListPane

/**
 * Two-pane master-detail layout used when [windowWidthClass] is
 * [WindowWidthClass.Medium] or [WindowWidthClass.Expanded].
 *
 * Layout
 * ------
 *  ┌──────────────┬──────────────────────────────────┐
 *  │  List pane   │  Detail pane                     │
 *  │  (35–40 %)   │  (60–65 %)                       │
 *  │              │  Empty placeholder if no user    │
 *  │              │  selected                        │
 *  └──────────────┴──────────────────────────────────┘
 *
 * The list pane highlights the currently selected row. Selecting a row
 * updates [selectedUserId]; the detail pane recomposes in place.
 *
 * A single [Scaffold] wraps both panes — no nested Scaffolds — so
 * window insets are applied exactly once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveTwoPaneScreen(
    windowWidthClass: WindowWidthClass,
    selectedUserId: Int?,
    onUserSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listWeight   = windowWidthClass.listPaneWeight
    val detailWeight = 1f - listWeight

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Users") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── List pane ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(listWeight)
                    .fillMaxHeight()
            ) {
                UserListPane(
                    onUserClick    = onUserSelected,
                    selectedUserId = selectedUserId,
                    modifier       = Modifier.fillMaxSize()
                )
            }

            VerticalDivider()

            // ── Detail pane ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(detailWeight)
                    .fillMaxHeight()
            ) {
                if (selectedUserId != null) {
                    UserDetailPane(
                        userId   = selectedUserId,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    EmptyDetailPane(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Placeholder shown in the detail pane before the user selects any row.
 */
@Composable
fun EmptyDetailPane(modifier: Modifier = Modifier) {
    Box(
        modifier           = modifier,
        contentAlignment   = Alignment.Center
    ) {
        Text(
            text  = "Select a user to view details",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
