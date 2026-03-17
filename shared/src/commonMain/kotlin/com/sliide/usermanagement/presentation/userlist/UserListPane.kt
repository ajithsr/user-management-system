package com.sliide.usermanagement.presentation.userlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sliide.usermanagement.domain.model.User
import com.sliide.usermanagement.presentation.components.AvatarImage
import com.sliide.usermanagement.presentation.components.FullScreenErrorView
import com.sliide.usermanagement.presentation.components.ShimmerUserList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful list pane.
 *
 * Loading states
 * --------------
 * • `users.isEmpty && (isLoading || isLoadingMore)` → full-screen [ShimmerUserList].
 * • `error != null && users.isEmpty()` → [FullScreenErrorView] with Retry.
 * • `isLoadingMore` while users non-empty → list stays fully visible; a slim
 *   [PaginationFooter] spinner appears below the last row — no blank area, no seam.
 *
 * Snackbar
 * --------
 * Callers pass [snackbarHostState] so the snackbar is rendered inside the
 * parent [Scaffold]'s `snackbarHost` slot, which Material3 automatically
 * positions above the FAB.
 */
@Composable
fun UserListPane(
    onUserClick: (Int) -> Unit,
    selectedUserId: Int? = null,
    onAutoSelectUser: ((Int?) -> Unit)? = null,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = koinViewModel(),
) {
    val state        by viewModel.state.collectAsState()
    val listState     = rememberLazyListState()

    // ── Effect handler ────────────────────────────────────────────────────────
    // Each ShowUndoDelete is launched in its own child coroutine so the collect
    // loop is never blocked. When a second deletion arrives while the first
    // snackbar is still showing, the old job is cancelled (snackbar dismissed)
    // and the new message appears immediately.
    LaunchedEffect(Unit) {
        var undoSnackbarJob: Job? = null
        viewModel.effects.collect { effect ->
            when (effect) {
                is UserListEffect.NavigateToDetail -> { /* handled by onUserClick caller */ }
                is UserListEffect.ShowUndoDelete -> {
                    undoSnackbarJob?.cancel()
                    undoSnackbarJob = launch {
                        val result = snackbarHostState.showSnackbar(
                            message           = "${effect.userName} deleted",
                            actionLabel       = "Undo",
                            duration          = SnackbarDuration.Long,
                            withDismissAction = true
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.processIntent(UserListIntent.UndoDelete(effect.userId))
                        }
                    }
                }
                is UserListEffect.ShowError ->
                    snackbarHostState.showSnackbar(
                        message  = effect.message,
                        duration = SnackbarDuration.Short
                    )
                is UserListEffect.ScrollToUser -> {
                    // Wait until the restored user appears in state.users — handles the
                    // timing gap between the DB write completing and state propagating to UI.
                    val index = snapshotFlow { state.users.indexOfFirst { it.id == effect.userId } }
                        .filter { it >= 0 }
                        .first()
                    listState.scrollToItem(index)
                }
            }
        }
    }

    // ── Infinite scroll trigger ───────────────────────────────────────────────
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total       = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !state.isLoadingMore && !state.isLoading) {
            viewModel.processIntent(UserListIntent.LoadNextPage)
        }
    }

    // ── Scroll to newly created user ──────────────────────────────────────────
    // Key on the pending user's id (a unique negative Int) rather than a Boolean.
    // This LaunchedEffect only fires when a NEW pending user appears — i.e. after
    // the optimistic DB insert has landed and the item is already at index 0 in
    // the list. scrollToItem(0) is instant so it cannot be interrupted by a fast
    // API response completing before an animation would finish.
    val pendingUserId = state.users.firstOrNull()?.takeIf { it.isPending }?.id
    LaunchedEffect(pendingUserId) {
        if (pendingUserId != null) {
            listState.scrollToItem(0)
        }
    }

    // ── Auto-select adjacent user when the selected user is deleted ───────────
    // Track the last known list index of the selected user so we can jump to
    // the nearest remaining user when it disappears from the list.
    val lastSelectedIndex = remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.users, selectedUserId) {
        val selected = selectedUserId
        if (selected == null) {
            lastSelectedIndex.intValue = -1
            return@LaunchedEffect
        }
        val idx = state.users.indexOfFirst { it.id == selected }
        when {
            idx >= 0 -> lastSelectedIndex.intValue = idx   // user present — keep index fresh
            !state.isLoading && !state.isLoadingMore -> {  // user gone from a settled list
                val targetIdx = lastSelectedIndex.intValue
                    .coerceIn(0, state.users.lastIndex.coerceAtLeast(0))
                onAutoSelectUser?.invoke(state.users.getOrNull(targetIdx)?.id)
                lastSelectedIndex.intValue = -1
            }
        }
    }

    Box(modifier = modifier) {
        when {
            // ── 1. First-load shimmer ─────────────────────────────────────────
            state.users.isEmpty() && (state.isLoading || state.isLoadingMore) -> {
                ShimmerUserList(
                    count    = 8,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ── 2. Full-screen error (no cached data) ─────────────────────────
            state.error != null && state.users.isEmpty() -> {
                FullScreenErrorView(
                    message  = state.error ?: "Something went wrong",
                    onRetry  = { viewModel.processIntent(UserListIntent.LoadInitial) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ── 3. Normal list ────────────────────────────────────────────────
            else -> {
                LazyColumn(
                    state               = listState,
                    modifier            = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentPadding      = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = state.users,
                        key   = { user -> user.id }
                    ) { user ->
                        UserListItem(
                            user       = user,
                            isSelected = user.id == selectedUserId,
                            // Pending users don't have a stable navigable ID yet —
                            // disable tap until the POST is confirmed and the real
                            // ID is assigned.
                            onClick    = remember(user.id, user.isPending) {
                                if (user.isPending) ({}) else { { onUserClick(user.id) } }
                            },
                            onDelete   = remember(user.id) {
                                {
                                    viewModel.processIntent(
                                        UserListIntent.DeleteUser(user.id, user.fullName)
                                    )
                                }
                            },
                            modifier   = Modifier.animateItem()
                        )
                    }

                    // Pagination footer — single centered spinner, visually flush
                    // with the list background so there is no "second section" seam.
                    if (state.isLoadingMore) {
                        item(key = "pagination_footer") {
                            PaginationFooter()
                        }
                    }
                }
            }
        }
    }
}

// ── Pagination footer ─────────────────────────────────────────────────────────

/**
 * Minimal spinner row shown at the bottom of the list while a next page is
 * in flight. Sits on the same [MaterialTheme.colorScheme.surfaceVariant]
 * background as the gaps between tiles, so it reads as a natural list tail
 * rather than a separate loading section.
 */
@Composable
private fun PaginationFooter() {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier    = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color       = MaterialTheme.colorScheme.primary
        )
    }
}

// ── List item ─────────────────────────────────────────────────────────────────

@Composable
private fun UserListItem(
    user: User,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileColor = if (isSelected)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surface

    ListItem(
        modifier = modifier
            .background(tileColor)
            .clickable(enabled = !user.isPending, onClick = onClick),
        leadingContent = {
            AvatarImage(
                model              = user.avatarUrl,
                contentDescription = user.fullName,
                modifier           = Modifier.size(48.dp)
            )
        },
        headlineContent = {
            Text(
                text     = user.fullName,
                style    = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text     = if (user.isPending) "Creating…" else user.email,
                style    = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = if (user.isPending) MaterialTheme.colorScheme.outline
                           else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (user.isPending) {
                // Show a spinner in the same 48×48 area as the IconButton so
                // the row height doesn't shift when the user is confirmed.
                Box(
                    modifier         = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector        = Icons.Default.Delete,
                        contentDescription = "Delete ${user.fullName}"
                    )
                }
            }
        }
    )
}
