package com.sliide.usermanagement.di

import com.sliide.usermanagement.presentation.adduser.AddUserFormViewModel
import com.sliide.usermanagement.presentation.userdetail.UserDetailViewModel
import com.sliide.usermanagement.presentation.userfeed.UserFeedViewModel
import com.sliide.usermanagement.presentation.userlist.UserListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Provides all ViewModels.
 *
 * Scoping
 * -------
 * ViewModels are **not** singletons. Koin's `viewModel { }` / `viewModelOf`
 * DSL defers to the platform ViewModel lifecycle — on Android the instance is
 * retained across configuration changes by [ViewModelStore] and destroyed only
 * when the owner (Activity/Fragment/NavGraph) is permanently gone. Creating a
 * new instance via Koin does not bypass this; the platform store is checked first.
 *
 * Binding styles used
 * -------------------
 * • `viewModelOf(::Foo)` — used when all constructor parameters are resolvable
 *   from the Koin graph with no special handling. Koin reflects the primary
 *   constructor and calls `get()` for each parameter type automatically.
 *
 * • `viewModel { Foo(param = get(), ...) }` — used when:
 *     (a) the constructor has an injectable [Clock] alongside a use case, or
 *     (b) a parameter requires a Koin [ParametersHolder] (e.g. `params.get<Int>()`
 *         for scoped ViewModels created with `koinViewModel(parameters = ...)`.
 *
 * Test overrides
 * --------------
 * Override [Clock] to control time in ViewModel tests:
 * ```kotlin
 * startKoin {
 *     modules(useCaseModule, viewModelModule, module(override = true) {
 *         single<Clock> { FakeClock(fixedInstant) }
 *     })
 * }
 * ```
 */
val viewModelModule = module {

    // ── No extra parameters — viewModelOf resolves all deps automatically ──────

    /** Inject GetUsersUseCase from the graph. */
    viewModelOf(::UserListViewModel)

    /** No constructor parameters. */
    viewModelOf(::AddUserFormViewModel)

    // ── Explicit lambdas — Clock injection or scoped parameters ───────────────

    /**
     * [UserFeedViewModel] takes both [GetUsersUseCase] and [Clock].
     * Named-argument form is used for readability; order doesn't matter to Koin.
     */
    viewModel {
        UserFeedViewModel(
            getUsersUseCase = get(),
            clock           = get(),
        )
    }

    /**
     * [UserDetailViewModel] is parameterised by userId — supplied by the caller
     * via `koinViewModel(parameters = { parametersOf(userId) })`.
     * `params.get<Int>()` extracts the first parameter from [ParametersHolder].
     */
    viewModel { params ->
        UserDetailViewModel(
            userId               = params.get(),
            getUserDetailUseCase = get(),
        )
    }
}
