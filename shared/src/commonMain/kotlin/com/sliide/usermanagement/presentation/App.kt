package com.sliide.usermanagement.presentation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sliide.usermanagement.presentation.adaptive.AdaptiveTwoPaneScreen
import com.sliide.usermanagement.presentation.adaptive.WindowWidthClass
import com.sliide.usermanagement.presentation.adaptive.computeWindowWidthClass
import com.sliide.usermanagement.presentation.adaptive.isTwoPane
import com.sliide.usermanagement.presentation.theme.AppTheme
import com.sliide.usermanagement.presentation.userdetail.UserDetailScreen
import com.sliide.usermanagement.presentation.userlist.UserListScreen

/**
 * Root composable. Measures available width with [BoxWithConstraints] and
 * routes to either a compact single-pane layout or an adaptive two-pane
 * master-detail layout.
 *
 * Navigation state
 * ----------------
 * A single nullable [selectedUserId] drives all navigation:
 *
 *  null + Compact   → UserListScreen (full window)
 *  non-null + Compact → UserDetailScreen (full window, push)
 *  any + Medium/Expanded → AdaptiveTwoPaneScreen (both panes always visible;
 *                          null = empty detail placeholder)
 *
 * Configuration-change safety
 * ---------------------------
 * [rememberSaveable] persists [selectedUserId] across process death and
 * configuration changes. On compact the user returns to the detail screen
 * they left; on wide layouts the correct user is pre-selected.
 *
 * Responsive rotation transitions
 * --------------------------------
 * - Compact detail → rotate to wide: [selectedUserId] is non-null → the
 *   two-pane screen appears with the detail already loaded in the right pane.
 * - Wide with selection → rotate to compact portrait: [selectedUserId] is
 *   non-null → [UserDetailScreen] is shown immediately (no list flash).
 * - Wide with no selection → rotate to compact: lands on [UserListScreen].
 */
@Composable
fun App() {
    AppTheme {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val windowWidthClass = computeWindowWidthClass(maxWidth)

            // rememberSaveable survives configuration changes and process death.
            var selectedUserId by rememberSaveable { mutableStateOf<Int?>(null) }

            when {
                // ── Wide: persistent two-pane ──────────────────────────────────
                windowWidthClass.isTwoPane -> {
                    AdaptiveTwoPaneScreen(
                        windowWidthClass = windowWidthClass,
                        selectedUserId   = selectedUserId,
                        onUserSelected   = { selectedUserId = it },
                        modifier         = Modifier.fillMaxSize()
                    )
                }

                // ── Compact: user selected → full-screen detail ────────────────
                selectedUserId != null -> {
                    UserDetailScreen(
                        userId         = selectedUserId!!,
                        onNavigateBack = { selectedUserId = null },
                        modifier       = Modifier.fillMaxSize()
                    )
                }

                // ── Compact: no selection → full-screen list ───────────────────
                else -> {
                    UserListScreen(
                        onNavigateToDetail = { selectedUserId = it },
                        modifier           = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
