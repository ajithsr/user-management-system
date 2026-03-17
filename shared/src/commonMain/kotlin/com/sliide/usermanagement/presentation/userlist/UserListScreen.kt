package com.sliide.usermanagement.presentation.userlist

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sliide.usermanagement.presentation.adduser.AddUserFormDialog
import org.koin.compose.viewmodel.koinViewModel

/**
 * Full-screen user list with its own [Scaffold].
 *
 * Used only in [WindowWidthClass.Compact] (portrait phone) where the list
 * occupies the full window. In wider layouts, [UserListPane] is embedded
 * directly inside [AdaptiveTwoPaneScreen]'s shared Scaffold.
 *
 * The [SnackbarHostState] is created here and passed to both the Scaffold's
 * `snackbarHost` slot (so Material3 positions it above the FAB automatically)
 * and to [UserListPane] (so the pane's effect handler can post messages to it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    onNavigateToDetail: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = koinViewModel()
) {
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
        UserListPane(
            onUserClick       = onNavigateToDetail,
            snackbarHostState = snackbarHostState,
            viewModel         = viewModel,
            modifier          = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

    if (showAddUserDialog) {
        AddUserFormDialog(
            onDismiss = { showAddUserDialog = false },
            onSubmit  = { request ->
                showAddUserDialog = false
                viewModel.processIntent(UserListIntent.CreateUser(request))
            }
        )
    }
}
