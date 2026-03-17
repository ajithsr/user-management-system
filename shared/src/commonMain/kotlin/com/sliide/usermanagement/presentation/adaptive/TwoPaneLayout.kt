package com.sliide.usermanagement.presentation.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sliide.usermanagement.presentation.adduser.AddUserFormDialog
import com.sliide.usermanagement.presentation.userdetail.UserDetailPane
import com.sliide.usermanagement.presentation.userlist.UserListIntent
import com.sliide.usermanagement.presentation.userlist.UserListPane
import com.sliide.usermanagement.presentation.userlist.UserListViewModel
import org.koin.compose.viewmodel.koinViewModel

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
 * A single [Scaffold] wraps both panes — no nested Scaffolds — so
 * window insets are applied exactly once. The [SnackbarHostState] is owned
 * here and passed into the Scaffold's `snackbarHost` slot so it is
 * automatically rendered above the FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveTwoPaneScreen(
    windowWidthClass: WindowWidthClass,
    selectedUserId: Int?,
    onUserSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    listViewModel: UserListViewModel = koinViewModel()
) {
    val listWeight        = windowWidthClass.listPaneWeight
    val detailWeight      = 1f - listWeight
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddUserDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Users") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddUserDialog = true },
                icon    = { Icon(Icons.Default.Add, contentDescription = null) },
                text    = { Text("Add User") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    onUserClick       = { onUserSelected(it) },
                    selectedUserId    = selectedUserId,
                    onAutoSelectUser  = onUserSelected,
                    snackbarHostState = snackbarHostState,
                    viewModel         = listViewModel,
                    modifier          = Modifier.fillMaxSize()
                )
            }

            VerticalDivider()

            // ── Detail pane — animated on selection change ────────────────────
            AnimatedContent(
                targetState    = selectedUserId,
                transitionSpec = {
                    val appearing = initialState == null && targetState != null
                    if (appearing) {
                        // First selection: slide up gently + fade in
                        (slideInVertically(tween(350)) { it / 12 } +
                                fadeIn(tween(350))) togetherWith fadeOut(tween(200))
                    } else {
                        // User swap or deselect: crossfade
                        fadeIn(tween(280)) togetherWith fadeOut(tween(200))
                    }
                },
                modifier       = Modifier
                    .weight(detailWeight)
                    .fillMaxHeight(),
                label          = "detail_pane"
            ) { uid ->
                if (uid != null) {
                    UserDetailPane(
                        userId   = uid,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    EmptyDetailPane(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    if (showAddUserDialog) {
        AddUserFormDialog(
            onDismiss = { showAddUserDialog = false },
            onSubmit  = { request ->
                showAddUserDialog = false
                listViewModel.processIntent(UserListIntent.CreateUser(request))
            }
        )
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
