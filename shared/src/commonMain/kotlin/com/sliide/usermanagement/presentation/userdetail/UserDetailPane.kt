package com.sliide.usermanagement.presentation.userdetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.sliide.usermanagement.domain.model.Gender
import com.sliide.usermanagement.presentation.components.AvatarImage
import com.sliide.usermanagement.domain.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 *
 * Animations
 * ----------
 * • Loaded user data crossfades when the active user changes.
 * • Each section in [UserDetailContent] staggers in: fade + 28 dp slide-up,
 *   70 ms later per section.
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
            state.user != null -> AnimatedContent(
                targetState    = state.user!!,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label          = "user_detail_content"
            ) { user ->
                UserDetailContent(user = user)
            }
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
        // Section 0 — avatar + name header
        AnimatedSection(index = 0) {
            Spacer(Modifier.height(16.dp))
            AvatarImage(
                model              = user.avatarUrl,
                contentDescription = user.fullName,
                modifier           = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text       = user.fullName,
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = "@${user.username}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(24.dp))
        }

        // Section 1 — Contact
        AnimatedSection(index = 1) {
            DetailSection("Contact") {
                DetailRow("Email", user.email)
                DetailRow("Phone", user.phone)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Section 2 — Personal
        AnimatedSection(index = 2) {
            DetailSection("Personal") {
                DetailRow("Age",    user.age.toString())
                DetailRow("Gender", user.gender.displayName())
            }
        }

        Spacer(Modifier.height(16.dp))

        // Section 3 — Location
        AnimatedSection(index = 3) {
            DetailSection("Location") {
                DetailRow("Street",  user.address.street)
                DetailRow("City",    user.address.city)
                DetailRow("State",   user.address.state)
                DetailRow("Country", user.address.country)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Section 4 — Work
        AnimatedSection(index = 4) {
            DetailSection("Work") {
                DetailRow("Company",    user.company.name)
                DetailRow("Department", user.company.department)
                DetailRow("Title",      user.company.jobTitle)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Wraps [content] in a staggered fade + slide-up entrance keyed on [index].
 * [LaunchedEffect] fires once when the composable enters composition — which
 * happens fresh for each [AnimatedContent] target, so the stagger replays on
 * every user change.
 */
@Composable
private fun AnimatedSection(index: Int, content: @Composable () -> Unit) {
    val alpha  = remember { Animatable(0f) }
    val slideY = remember { Animatable(28f) }

    LaunchedEffect(Unit) {
        delay(index * 70L)
        launch { alpha.animateTo(1f,  tween(400, easing = FastOutSlowInEasing)) }
        launch { slideY.animateTo(0f, tween(400, easing = FastOutSlowInEasing)) }
    }

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha        = alpha.value
                this.translationY = slideY.value
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
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
        modifier              = Modifier
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
