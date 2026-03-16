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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sliide.usermanagement.domain.model.User
import com.sliide.usermanagement.presentation.components.FullScreenErrorView
import com.sliide.usermanagement.presentation.components.ShimmerUserList
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
 */
@Composable
fun UserListPane(
    onUserClick: (Int) -> Unit,
    selectedUserId: Int? = null,
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = koinViewModel()
) {
    val state        by viewModel.state.collectAsState()
    val listState     = rememberLazyListState()
    val snackbarHost  = remember { SnackbarHostState() }

    // ── Effect handler ────────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UserListEffect.NavigateToDetail -> { /* handled by onUserClick caller */ }
                is UserListEffect.ShowUndoDelete -> {
                    val result = snackbarHost.showSnackbar(
                        message           = "${effect.userName} deleted",
                        actionLabel       = "Undo",
                        duration          = SnackbarDuration.Long,
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.processIntent(UserListIntent.UndoDelete(effect.userId))
                    }
                }
                is UserListEffect.ShowError ->
                    snackbarHost.showSnackbar(
                        message  = effect.message,
                        duration = SnackbarDuration.Short
                    )
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
                            onClick    = remember(user.id) { { onUserClick(user.id) } },
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

        // ── Snackbar host ─────────────────────────────────────────────────────
        SnackbarHost(
            hostState = snackbarHost,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
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
            .clickable(onClick = onClick),
        leadingContent = {
            AsyncImage(
                model              = user.avatarUrl,
                contentDescription = user.fullName,
                modifier           = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
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
                text     = user.email,
                style    = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Delete ${user.fullName}"
                )
            }
        }
    )
}
