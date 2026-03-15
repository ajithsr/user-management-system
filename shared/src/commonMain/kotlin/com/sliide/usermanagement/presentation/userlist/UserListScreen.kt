package com.sliide.usermanagement.presentation.userlist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Full-screen user list with its own [Scaffold].
 *
 * Used only in [WindowWidthClass.Compact] (portrait phone) where the list
 * occupies the full window. In wider layouts, [UserListPane] is embedded
 * directly inside [AdaptiveTwoPaneScreen]'s shared Scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    onNavigateToDetail: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Users") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        UserListPane(
            onUserClick = onNavigateToDetail,
            modifier    = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}
