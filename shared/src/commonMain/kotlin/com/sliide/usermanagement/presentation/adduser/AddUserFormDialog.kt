package com.sliide.usermanagement.presentation.adduser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import com.sliide.usermanagement.domain.model.CreateUserRequest
import com.sliide.usermanagement.domain.model.Gender
import com.sliide.usermanagement.domain.validation.FieldError

/** Test tag for the dialog root — use to assert dialog visibility. */
const val TAG_ADD_USER_DIALOG  = "add_user_dialog"
/** Test tag for the submit button — use to trigger form submission. */
const val TAG_ADD_USER_SUBMIT  = "add_user_submit"

/**
 * Full-screen-width dialog for creating a new user.
 *
 * Backed by [AddUserFormViewModel] which validates the form on every
 * keystroke and emits [AddUserFormEffect.Submit] once all fields are clean.
 *
 * The caller receives the confirmed [CreateUserRequest] via [onSubmit] and
 * is responsible for forwarding it to [UserFeedViewModel] or another handler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserFormDialog(
    onDismiss: () -> Unit,
    onSubmit : (CreateUserRequest) -> Unit,
    viewModel: AddUserFormViewModel = remember { AddUserFormViewModel() }
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AddUserFormEffect.Dismiss -> onDismiss()
                is AddUserFormEffect.Submit  -> onSubmit(effect.request)
            }
        }
    }

    val v = formState.visibleErrors
    var genderExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        modifier      = Modifier.testTag(TAG_ADD_USER_DIALOG),
        onDismissRequest = { viewModel.onIntent(AddUserFormIntent.Dismiss) },
        title   = { Text("Add User") },
        text    = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value         = formState.input.firstName,
                    onValueChange = { viewModel.onIntent(AddUserFormIntent.UpdateFirstName(it)) },
                    label         = { Text("First name") },
                    isError       = v.firstName != null,
                    supportingText = v.firstName?.let { { Text(it.displayMessage()) } },
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = formState.input.lastName,
                    onValueChange = { viewModel.onIntent(AddUserFormIntent.UpdateLastName(it)) },
                    label         = { Text("Last name") },
                    isError       = v.lastName != null,
                    supportingText = v.lastName?.let { { Text(it.displayMessage()) } },
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = formState.input.email,
                    onValueChange = { viewModel.onIntent(AddUserFormIntent.UpdateEmail(it)) },
                    label         = { Text("Email") },
                    isError       = v.email != null,
                    supportingText = v.email?.let { { Text(it.displayMessage()) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = formState.input.username,
                    onValueChange = { viewModel.onIntent(AddUserFormIntent.UpdateUsername(it)) },
                    label         = { Text("Username") },
                    isError       = v.username != null,
                    supportingText = v.username?.let { { Text(it.displayMessage()) } },
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = formState.input.age,
                    onValueChange = { viewModel.onIntent(AddUserFormIntent.UpdateAge(it)) },
                    label         = { Text("Age") },
                    isError       = v.age != null,
                    supportingText = v.age?.let { { Text(it.displayMessage()) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded        = genderExpanded,
                    onExpandedChange = { genderExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = formState.input.gender?.name ?: "",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Gender") },
                        isError       = v.gender != null,
                        trailingIcon  = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded        = genderExpanded,
                        onDismissRequest = { genderExpanded = false }
                    ) {
                        Gender.entries.forEach { gender ->
                            DropdownMenuItem(
                                text    = { Text(gender.name) },
                                onClick = {
                                    viewModel.onIntent(AddUserFormIntent.UpdateGender(gender))
                                    genderExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { viewModel.onIntent(AddUserFormIntent.Submit) },
                enabled  = formState.canSubmit,
                modifier = Modifier.testTag(TAG_ADD_USER_SUBMIT)
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.onIntent(AddUserFormIntent.Dismiss) }) {
                Text("Cancel")
            }
        }
    )
}

// ── Local display helpers ──────────────────────────────────────────────────────

private fun FieldError.displayMessage(): String = when (this) {
    FieldError.Required          -> "Required"
    FieldError.TooShort          -> "Too short"
    FieldError.TooLong           -> "Too long"
    FieldError.InvalidCharacters -> "Invalid characters"
    FieldError.InvalidEmailFormat -> "Enter a valid email address"
    FieldError.InvalidAge        -> "Enter a whole number"
    FieldError.AgeBelowMinimum   -> "Must be at least 18"
    FieldError.AgeAboveMaximum   -> "Must be 120 or under"
}
