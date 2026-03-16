package com.sliide.usermanagement.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sliide.usermanagement.presentation.adaptive.AdaptiveTwoPaneScreen
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
 *  null + Compact     → UserListScreen (full window)
 *  non-null + Compact → UserDetailScreen (full window, push animation)
 *  any + Medium/Expanded → AdaptiveTwoPaneScreen (both panes always visible)
 *
 * Transitions
 * -----------
 * Compact list → detail : iOS-style push  (new screen slides from right; old retreats ⅓ left)
 * Compact detail → list : iOS-style pop   (screen exits right; previous advances from ⅓ left)
 * Any width-class change : crossfade 300 ms
 */
@Composable
fun App() {
    AppTheme {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val windowWidthClass = computeWindowWidthClass(maxWidth)
            var selectedUserId by rememberSaveable { mutableStateOf<Int?>(null) }

            // Stable screen key for AnimatedContent.
            // TwoPane is a singleton so internal selection changes don't trigger
            // a full-screen transition — they animate inside the two-pane layout.
            val navScreen: NavScreen = when {
                windowWidthClass.isTwoPane -> NavScreen.TwoPane
                selectedUserId != null     -> NavScreen.Detail(selectedUserId!!)
                else                       -> NavScreen.List
            }

            AnimatedContent(
                targetState    = navScreen,
                transitionSpec = {
                    val forward  = initialState == NavScreen.List   && targetState is NavScreen.Detail
                    val backward = initialState is NavScreen.Detail && targetState == NavScreen.List
                    when {
                        // Push: new screen enters from right; current retreats ⅓ left
                        forward  ->
                            (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } +
                                    fadeIn(tween(380))) togetherWith
                                    (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 3 } +
                                            fadeOut(tween(180)))

                        // Pop: screen exits right; previous advances from ⅓ left
                        backward ->
                            (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 3 } +
                                    fadeIn(tween(380))) togetherWith
                                    (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } +
                                            fadeOut(tween(180)))

                        // Rotation / width-class change: crossfade
                        else     -> fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                },
                modifier       = Modifier.fillMaxSize(),
                label          = "root_nav"
            ) { screen ->
                when (screen) {
                    NavScreen.TwoPane -> AdaptiveTwoPaneScreen(
                        windowWidthClass = windowWidthClass,
                        selectedUserId   = selectedUserId,
                        onUserSelected   = { selectedUserId = it },
                        modifier         = Modifier.fillMaxSize()
                    )
                    is NavScreen.Detail -> UserDetailScreen(
                        userId         = screen.userId,
                        onNavigateBack = { selectedUserId = null },
                        modifier       = Modifier.fillMaxSize()
                    )
                    NavScreen.List -> UserListScreen(
                        onNavigateToDetail = { selectedUserId = it },
                        modifier           = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ── Screen key ────────────────────────────────────────────────────────────────

private sealed interface NavScreen {
    data object List    : NavScreen
    data class  Detail(val userId: Int) : NavScreen
    data object TwoPane : NavScreen
}
