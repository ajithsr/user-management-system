package com.sliide.usermanagement.presentation.userlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sliide.usermanagement.domain.model.User
import com.sliide.usermanagement.presentation.components.FullScreenErrorView
import com.sliide.usermanagement.presentation.components.ShimmerUserList
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful list pane. Manages its own [UserListViewModel]; the caller
 * only receives click callbacks and can optionally highlight a selected row.
 *
 * Used in two places:
 *  - [UserListScreen] — wraps this in a Scaffold for compact / single-pane.
 *  - [AdaptiveTwoPaneScreen] — embeds this directly in the left pane.
 *
 * No [Scaffold] or [TopAppBar] inside — those belong to the caller so that
 * insets are applied exactly once per layout.
 *
 * Loading states
 * --------------
 * • `isLoading && users.isEmpty()` → [ShimmerUserList] skeleton (8 rows).
 * • `error != null && users.isEmpty()` → [FullScreenErrorView] with Retry.
 * • `error != null && users.isNotEmpty()` → inline Snackbar (stale data visible).
 * • `isLoadingMore` → spinner appended after last real item.
 * • Each newly-arrived item fades + slides in via [AnimatedVisibility].
 */
@Composable
fun UserListPane(
    onUserClick: (Int) -> Unit,
    selectedUserId: Int? = null,
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = koinViewModel()
) {
    val state     by viewModel.state.collectAsStateWithLifecycle()
    val listState  = rememberLazyListState()

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
            state.isLoading && state.users.isEmpty() -> {
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

            // ── 3. Normal list (with optional stale-data error snackbar) ──────
            else -> {
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = state.users,
                        key   = { _, user -> user.id }
                    ) { index, user ->
                        AnimatedUserListItem(
                            user       = user,
                            index      = index,
                            isSelected = user.id == selectedUserId,
                            onClick    = { onUserClick(user.id) }
                        )
                        HorizontalDivider()
                    }

                    // ── Load-more spinner ─────────────────────────────────────
                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier         = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }

        // ── Inline error snackbar (stale data visible beneath) ────────────────
        if (state.error != null && state.users.isNotEmpty()) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(
                        onClick = { viewModel.processIntent(UserListIntent.DismissError) }
                    ) { Text("Dismiss") }
                }
            ) { Text(state.error ?: "") }
        }
    }
}

// ── Animated list item ────────────────────────────────────────────────────────

/**
 * Wraps [UserListItem] in an [AnimatedVisibility] that fades + slides up
 * when the item first enters composition. Items stagger by [index] * 30 ms
 * so the list appears to cascade in from the top.
 */
@Composable
private fun AnimatedUserListItem(
    user: User,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(durationMillis = 300, delayMillis = (index * 30).coerceAtMost(300))) +
                  slideInVertically(
                      animationSpec = tween(durationMillis = 300, delayMillis = (index * 30).coerceAtMost(300)),
                      initialOffsetY = { it / 4 }
                  )
    ) {
        UserListItem(user = user, isSelected = isSelected, onClick = onClick)
    }
}

// ── List item ─────────────────────────────────────────────────────────────────

@Composable
private fun UserListItem(
    user: User,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val background = if (isSelected)
        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
    else
        Modifier

    ListItem(
        modifier = Modifier
            .then(background)
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
            Text(
                text  = user.company.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    )
}
