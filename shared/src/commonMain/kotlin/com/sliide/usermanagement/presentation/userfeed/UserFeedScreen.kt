package com.sliide.usermanagement.presentation.userfeed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import org.koin.compose.viewmodel.koinViewModel
import com.sliide.usermanagement.presentation.adduser.AddUserFormDialog
import com.sliide.usermanagement.presentation.components.FullScreenErrorView

/** Test tag for the Add-User FAB. */
const val TAG_FAB_ADD_USER = "fab_add_user"
/** Test tag prefix for a user row — append the user ID. */
const val TAG_USER_ITEM    = "user_item_"
/** Test tag prefix for a delete icon — append the user ID. */
const val TAG_DELETE_USER  = "delete_user_"

// ══════════════════════════════════════════════════════════════════════════════
// Stateful entry point (production)
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Production entry point. Obtains [UserFeedViewModel] from Koin and delegates
 * to the stateless overload below.
 */
@Composable
fun UserFeedScreen(
    onNavigateToDetail: (Int) -> Unit,
    modifier          : Modifier = Modifier,
    viewModel         : UserFeedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UserFeedScreen(
        state              = state,
        effects            = viewModel.effects,
        onIntent           = viewModel::onIntent,
        onNavigateToDetail = onNavigateToDetail,
        modifier           = modifier
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// Stateless overload (testable)
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Stateless overload — receives [state] and [effects] as plain values and
 * forwards user actions to [onIntent].
 *
 * Tests inject fake state/effects directly; the screen itself does not know
 * about ViewModels or Koin, making it trivially composable in UI tests via
 * [runComposeUiTest].
 *
 * UI contract
 * -----------
 * • FAB (`contentDescription = "Add User"`, `testTag = TAG_FAB_ADD_USER`)
 *   opens [AddUserFormDialog].
 * • Each item row carries a delete [IconButton]
 *   (`contentDescription = "Delete <fullName>"`, `testTag = TAG_DELETE_USER + id`).
 * • [UserFeedEffect.ShowUndoDelete] shows a snackbar: "<userName> deleted / Undo".
 *   Tapping Undo dispatches [UserFeedIntent.UndoDelete].
 * • [UserFeedEffect.UserCreated] dismisses the add-user dialog automatically.
 */
@Composable
fun UserFeedScreen(
    state             : UserFeedState,
    effects           : Flow<UserFeedEffect>,
    onIntent          : (UserFeedIntent) -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    modifier          : Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    // ── Effect handler ────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        effects.collect { effect ->
            when (effect) {
                is UserFeedEffect.NavigateToDetail ->
                    onNavigateToDetail(effect.userId)

                is UserFeedEffect.ShowError ->
                    snackbarHostState.showSnackbar(
                        message  = effect.message,
                        duration = SnackbarDuration.Short
                    )

                is UserFeedEffect.UserCreated ->
                    showAddDialog = false

                is UserFeedEffect.ShowUndoDelete -> {
                    val result = snackbarHostState.showSnackbar(
                        message      = "${effect.userName} deleted",
                        actionLabel  = "Undo",
                        duration     = SnackbarDuration.Long,
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onIntent(UserFeedIntent.UndoDelete(effect.userId))
                    }
                }
            }
        }
    }

    Scaffold(
        modifier     = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick  = { showAddDialog = true },
                modifier = Modifier.testTag(TAG_FAB_ADD_USER)
            ) {
                Icon(
                    imageVector        = Icons.Default.Add,
                    contentDescription = "Add User"
                )
            }
        }
    ) { padding ->
        UserFeedContent(
            state    = state,
            onIntent = onIntent,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

    if (showAddDialog) {
        AddUserFormDialog(
            onDismiss = { showAddDialog = false },
            onSubmit  = { request ->
                showAddDialog = false
                onIntent(UserFeedIntent.CreateUser(request))
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Content — stateless, no Scaffold
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun UserFeedContent(
    state   : UserFeedState,
    onIntent: (UserFeedIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (state) {
            is UserFeedState.Loading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is UserFeedState.Empty ->
                Text(
                    text     = "No users yet",
                    modifier = Modifier.align(Alignment.Center)
                )

            is UserFeedState.Error ->
                FullScreenErrorView(
                    message  = state.message,
                    onRetry  = { onIntent(UserFeedIntent.Retry) },
                    modifier = Modifier.fillMaxSize()
                )

            is UserFeedState.Success ->
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = state.items,
                        key   = { it.id }
                    ) { item ->
                        FeedItemRow(item = item, onIntent = onIntent)
                    }
                }
        }
    }
}

// ── Item row ─────────────────────────────────────────────────────────────────

@Composable
private fun FeedItemRow(
    item    : UserFeedItem,
    onIntent: (UserFeedIntent) -> Unit
) {
    ListItem(
        modifier = Modifier.testTag("$TAG_USER_ITEM${item.id}"),
        headlineContent   = { Text(item.fullName) },
        supportingContent = { Text(item.email) },
        overlineContent   = { if (item.isPending) Text("Pending…") },
        trailingContent   = {
            IconButton(
                onClick  = { onIntent(UserFeedIntent.DeleteUser(item.id, item.fullName)) },
                modifier = Modifier.testTag("$TAG_DELETE_USER${item.id}")
            ) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Delete ${item.fullName}"
                )
            }
        }
    )
}
