package com.sliide.usermanagement.presentation.userdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImage
import com.sliide.usermanagement.domain.model.Gender
import com.sliide.usermanagement.domain.model.User
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful detail pane. Creates its own [UserDetailViewModel] keyed on
 * [userId] so the ViewModel is swapped when the selected user changes.
 *
 * Used in two places:
 *  - [UserDetailScreen] — wraps this in a Scaffold with a back button.
 *  - [AdaptiveTwoPaneScreen] — embeds this directly in the right pane.
 *
 * No [Scaffold] or navigation controls inside — those belong to the caller.
 */
@Composable
fun UserDetailPane(
    userId: Int,
    modifier: Modifier = Modifier,
    viewModel: UserDetailViewModel = koinViewModel(
        key        = "detail-$userId",
        parameters = { parametersOf(userId) }
    )
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = modifier) {
        when {
            state.isLoading  -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error != null -> Text(
                text     = "Error: ${state.error}",
                modifier = Modifier.align(Alignment.Center),
                color    = MaterialTheme.colorScheme.error
            )
            state.user != null -> UserDetailContent(user = state.user!!)
        }
    }
}

// ── Shared content composables ────────────────────────────────────────────────

@Composable
internal fun UserDetailContent(user: User) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        AsyncImage(
            model              = user.avatarUrl,
            contentDescription = user.fullName,
            modifier           = Modifier
                .size(96.dp)
                .clip(CircleShape)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text        = user.fullName,
            style       = MaterialTheme.typography.headlineMedium,
            fontWeight  = FontWeight.Bold
        )
        Text(
            text  = "@${user.username}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(24.dp))

        DetailSection("Contact") {
            DetailRow("Email", user.email)
            DetailRow("Phone", user.phone)
        }

        Spacer(Modifier.height(16.dp))

        DetailSection("Personal") {
            DetailRow("Age",    user.age.toString())
            DetailRow("Gender", user.gender.displayName())
        }

        Spacer(Modifier.height(16.dp))

        DetailSection("Location") {
            DetailRow("Street",  user.address.street)
            DetailRow("City",    user.address.city)
            DetailRow("State",   user.address.state)
            DetailRow("Country", user.address.country)
        }

        Spacer(Modifier.height(16.dp))

        DetailSection("Work") {
            DetailRow("Company",    user.company.name)
            DetailRow("Department", user.company.department)
            DetailRow("Title",      user.company.jobTitle)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(
        modifier             = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text     = value,
            style    = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

// ── Domain helpers ────────────────────────────────────────────────────────────

private fun Gender.displayName(): String = when (this) {
    Gender.Male   -> "Male"
    Gender.Female -> "Female"
    Gender.Other  -> "Other"
}
