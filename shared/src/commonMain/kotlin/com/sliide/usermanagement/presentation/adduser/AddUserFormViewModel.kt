package com.sliide.usermanagement.presentation.adduser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sliide.usermanagement.domain.model.Gender
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Add-User form.
 *
 * Responsibilities
 * ----------------
 * 1. Hold [AddUserFormState] — the single source of truth for the form UI.
 * 2. Re-validate the **entire** form on every keystroke so [AddUserFormState.canSubmit]
 *    is always accurate. Showing errors is a separate concern handled by
 *    [AddUserFormState.visibleErrors] based on [AddUserFormState.touched] and
 *    [AddUserFormState.submitAttempted].
 * 3. On [AddUserFormIntent.BlurField]: mark the field as touched so its error
 *    becomes visible immediately (if it has one).
 * 4. On [AddUserFormIntent.Submit]:
 *    a. Mark [AddUserFormState.submitAttempted] = true so ALL errors become visible.
 *    b. If the form has errors, stop — the UI will display them.
 *    c. If clean, set [AddUserFormState.isSubmitting] = true and emit
 *       [AddUserFormEffect.Submit] so the parent screen can delegate to
 *       [UserFeedViewModel] and close the form.
 *
 * No use-case dependencies — this ViewModel only validates locally and
 * delegates the actual API call to the caller via the effect.
 */
class AddUserFormViewModel : ViewModel() {

    private val _formState = MutableStateFlow(AddUserFormState())
    val formState: StateFlow<AddUserFormState> = _formState.asStateFlow()

    private val _effects = Channel<AddUserFormEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    // ── Intent handler ────────────────────────────────────────────────────────

    fun onIntent(intent: AddUserFormIntent) {
        when (intent) {
            is AddUserFormIntent.UpdateFirstName -> updateInput { copy(firstName = intent.value) }
            is AddUserFormIntent.UpdateLastName  -> updateInput { copy(lastName  = intent.value) }
            is AddUserFormIntent.UpdateEmail     -> updateInput { copy(email     = intent.value) }
            is AddUserFormIntent.UpdateUsername  -> updateInput { copy(username  = intent.value) }
            is AddUserFormIntent.UpdateAge       -> updateInput { copy(age       = intent.value) }
            is AddUserFormIntent.UpdateGender    -> updateInput { copy(gender    = intent.value) }
            is AddUserFormIntent.BlurField       -> onBlur(intent.field)
            is AddUserFormIntent.Submit          -> onSubmit()
            is AddUserFormIntent.Dismiss         -> viewModelScope.launch {
                _effects.send(AddUserFormEffect.Dismiss)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Applies [transform] to the current input and immediately re-validates the
     * whole form so [AddUserFormState.errors] and [AddUserFormState.canSubmit]
     * are always in sync with what's on screen.
     */
    private fun updateInput(transform: AddUserFormInput.() -> AddUserFormInput) {
        _formState.update { state ->
            val newInput  = transform(state.input)
            val newErrors = newInput.validate()
            state.copy(input = newInput, errors = newErrors)
        }
    }

    /**
     * Marks a field as touched. From this point on the field's error (if any)
     * is included in [AddUserFormState.visibleErrors] even before submit.
     */
    private fun onBlur(field: AddUserFormField) {
        _formState.update { it.copy(touched = it.touched + field) }
    }

    /**
     * Validates the whole form, reveals all errors, and emits [AddUserFormEffect.Submit]
     * iff there are no violations.
     */
    private fun onSubmit() {
        val current = _formState.value

        // Force-validate to catch any field that was never touched.
        val errors = current.input.validate()
        _formState.update { it.copy(errors = errors, submitAttempted = true) }

        if (errors.hasErrors) return   // UI will show the errors; nothing else to do.

        val request = current.input.toCreateUserRequest() ?: return  // guarded by hasErrors

        _formState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch { _effects.send(AddUserFormEffect.Submit(request)) }
    }
}
