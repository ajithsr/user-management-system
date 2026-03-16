package com.sliide.usermanagement.presentation.userdetail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Full-screen user detail with its own [Scaffold] and a back button.
 *
 * Used only in [WindowWidthClass.Compact] (portrait phone). In wider
 * layouts the detail content is rendered by [UserDetailPane] inside the
 * shared Scaffold of [AdaptiveTwoPaneScreen] — no back button needed there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    userId: Int,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserDetailViewModel = koinViewModel(
        key        = "detail-$userId",
        parameters = { parametersOf(userId) }
    )
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UserDetailEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.user?.fullName ?: "User Detail") },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.processIntent(UserDetailIntent.NavigateBack) }
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.processIntent(UserDetailIntent.DeleteUser) }
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Delete,
                            contentDescription = "Delete user"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        UserDetailPane(
            userId   = userId,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            viewModel = viewModel   // share the same instance — no second VM created
        )
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.processIntent(UserDetailIntent.DismissDeleteDialog) },
            title            = { Text("Delete user") },
            text             = { Text("Are you sure you want to delete ${state.user?.fullName ?: "this user"}? This action cannot be undone.") },
            confirmButton    = {
                TextButton(onClick = { viewModel.processIntent(UserDetailIntent.ConfirmDelete) }) {
                    Text("Delete")
                }
            },
            dismissButton    = {
                TextButton(onClick = { viewModel.processIntent(UserDetailIntent.DismissDeleteDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
