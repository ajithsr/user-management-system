package com.sliide.usermanagement.presentation.userfeed

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sliide.usermanagement.presentation.adduser.TAG_ADD_USER_DIALOG
import com.sliide.usermanagement.presentation.theme.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Compose UI tests for [UserFeedScreen].
 *
 * Uses the **stateless** [UserFeedScreen] overload so each test controls state
 * and effects directly — no ViewModel, no Koin, no network.
 *
 * Scenarios
 * ---------
 * 1. FAB tap opens the Add-User dialog.
 * 2. A newly-created user appears in the list as soon as the state updates.
 * 3. The [UserFeedEffect.ShowUndoDelete] effect shows a snackbar with the
 *    user's name and an "Undo" action.
 * 4. Tapping "Undo" dispatches [UserFeedIntent.UndoDelete] and the item
 *    reappears in the list once the state is restored.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class UserFeedScreenTest {

    // ── Test fixtures ──────────────────────────────────────────────────────────

    private fun stubItem(
        id       : Int    = 1,
        fullName : String = "Alice Smith",
        email    : String = "alice@example.com"
    ) = UserFeedItem(
        id        = id,
        initials  = fullName.split(" ")
                            .mapNotNull { it.firstOrNull()?.toString() }
                            .joinToString(""),
        fullName  = fullName,
        email     = email,
        role      = "Engineer",
        location  = "London, UK",
        avatarUrl = "",
        createdAt = "just now",
        isPending = false,
        isAdmin   = false
    )

    // ══════════════════════════════════════════════════════════════════════════
    // 1. FAB opens form
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun fab_click_opens_add_user_dialog() = runAndroidComposeUiTest {
        setContent {
            AppTheme {
                UserFeedScreen(
                    state              = UserFeedState.Success(items = emptyList()),
                    effects            = emptyFlow(),
                    onIntent           = {},
                    onNavigateToDetail = {}
                )
            }
        }

        // Dialog must be absent before FAB tap
        onNodeWithTag(TAG_ADD_USER_DIALOG).assertDoesNotExist()

        // Tap the FAB
        onNodeWithContentDescription("Add User").performClick()

        // Dialog title "Add User" is now visible
        onNodeWithText("Add User").assertIsDisplayed()
        onNodeWithTag(TAG_ADD_USER_DIALOG).assertIsDisplayed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. User added appears instantly
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun newly_created_user_appears_immediately_on_state_update() = runAndroidComposeUiTest {
        val stateFlow = MutableStateFlow<UserFeedState>(UserFeedState.Empty)

        setContent {
            AppTheme {
                val state by stateFlow.collectAsState()
                UserFeedScreen(
                    state              = state,
                    effects            = emptyFlow(),
                    onIntent           = {},
                    onNavigateToDetail = {}
                )
            }
        }

        onNodeWithText("Alice Smith").assertDoesNotExist()

        // Optimistic row hits the DB; usersStream emits; ViewModel builds new state
        stateFlow.value = UserFeedState.Success(
            items = listOf(stubItem(id = 42, fullName = "Alice Smith"))
        )
        waitForIdle()

        onNodeWithText("Alice Smith").assertIsDisplayed()
    }

    @Test
    fun multiple_users_all_appear_after_state_update() = runAndroidComposeUiTest {
        val stateFlow = MutableStateFlow<UserFeedState>(UserFeedState.Empty)

        setContent {
            AppTheme {
                val state by stateFlow.collectAsState()
                UserFeedScreen(
                    state              = state,
                    effects            = emptyFlow(),
                    onIntent           = {},
                    onNavigateToDetail = {}
                )
            }
        }

        stateFlow.value = UserFeedState.Success(
            items = listOf(
                stubItem(id = 1, fullName = "Alice Smith"),
                stubItem(id = 2, fullName = "Bob Jones")
            )
        )
        waitForIdle()

        onNodeWithText("Alice Smith").assertIsDisplayed()
        onNodeWithText("Bob Jones").assertIsDisplayed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. Delete shows snackbar
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun delete_icon_dispatches_delete_user_intent() = runAndroidComposeUiTest {
        val alice = stubItem(id = 7, fullName = "Alice Smith")
        val captured = mutableListOf<UserFeedIntent>()

        setContent {
            AppTheme {
                UserFeedScreen(
                    state              = UserFeedState.Success(items = listOf(alice)),
                    effects            = emptyFlow(),
                    onIntent           = { captured += it },
                    onNavigateToDetail = {}
                )
            }
        }

        onNodeWithContentDescription("Delete Alice Smith").performClick()
        waitForIdle()

        assertTrue(
            captured.any { it is UserFeedIntent.DeleteUser && it.userId == 7 },
            "Delete icon click must dispatch DeleteUser(7)"
        )
    }

    @Test
    fun show_undo_delete_effect_displays_snackbar_with_user_name() = runAndroidComposeUiTest {
        val effects = Channel<UserFeedEffect>(Channel.BUFFERED)

        setContent {
            AppTheme {
                UserFeedScreen(
                    state              = UserFeedState.Success(items = listOf(stubItem())),
                    effects            = effects.receiveAsFlow(),
                    onIntent           = {},
                    onNavigateToDetail = {}
                )
            }
        }

        effects.trySend(UserFeedEffect.ShowUndoDelete(userId = 1, userName = "Alice Smith"))
        waitForIdle()

        onNodeWithText("Alice Smith deleted").assertIsDisplayed()
        onNodeWithText("Undo").assertIsDisplayed()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. Undo restores item
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun undo_button_fires_undo_delete_intent() = runAndroidComposeUiTest {
        val effects  = Channel<UserFeedEffect>(Channel.BUFFERED)
        val captured = mutableListOf<UserFeedIntent>()

        setContent {
            AppTheme {
                UserFeedScreen(
                    state              = UserFeedState.Success(items = listOf(stubItem())),
                    effects            = effects.receiveAsFlow(),
                    onIntent           = { captured += it },
                    onNavigateToDetail = {}
                )
            }
        }

        effects.trySend(UserFeedEffect.ShowUndoDelete(userId = 1, userName = "Alice Smith"))
        waitForIdle()

        onNodeWithText("Undo").performClick()
        waitForIdle()

        assertTrue(
            captured.any { it is UserFeedIntent.UndoDelete && it.userId == 1 },
            "Undo tap must dispatch UndoDelete(1)"
        )
    }

    @Test
    fun undo_restores_user_row_in_list() = runAndroidComposeUiTest {
        val effects   = Channel<UserFeedEffect>(Channel.BUFFERED)
        val alice     = stubItem(id = 1, fullName = "Alice Smith")
        val stateFlow = MutableStateFlow<UserFeedState>(
            UserFeedState.Success(items = listOf(alice))
        )

        setContent {
            AppTheme {
                val state by stateFlow.collectAsState()
                UserFeedScreen(
                    state              = state,
                    effects            = effects.receiveAsFlow(),
                    onIntent           = { intent ->
                        if (intent is UserFeedIntent.UndoDelete) {
                            // Simulate the ViewModel restoring the soft-deleted row
                            stateFlow.value = UserFeedState.Success(items = listOf(alice))
                        }
                    },
                    onNavigateToDetail = {}
                )
            }
        }

        // Soft-delete removes the row from the list
        stateFlow.value = UserFeedState.Success(items = emptyList())
        effects.trySend(UserFeedEffect.ShowUndoDelete(userId = 1, userName = "Alice Smith"))
        waitForIdle()

        onNodeWithText("Alice Smith").assertDoesNotExist()
        onNodeWithText("Alice Smith deleted").assertIsDisplayed()

        // Tap Undo → onIntent(UndoDelete) → state lambda restores the user
        onNodeWithText("Undo").performClick()
        waitForIdle()

        onNodeWithText("Alice Smith").assertIsDisplayed()
    }
}
