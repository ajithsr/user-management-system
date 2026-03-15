package com.sliide.usermanagement.di

import com.sliide.usermanagement.data.local.UserLocalDataSource
import com.sliide.usermanagement.data.remote.UserApiService
import com.sliide.usermanagement.fake.FakeClock
import com.sliide.usermanagement.fake.FakeUserApiService
import com.sliide.usermanagement.fake.FakeUserLocalDataSource
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module

// ── Test fakes bundle ─────────────────────────────────────────────────────────

/**
 * Groups the three injectable test doubles together so callers can:
 *  1. Share the same fake instances between `testModule()` and test assertions.
 *  2. Override individual fakes without re-specifying the whole bundle.
 *
 * ```kotlin
 * val fakes = TestFakes()
 * fakes.api.getUsersResult = { _, _ -> myPageFixture }
 *
 * withTestKoin(fakes) {
 *     val repo = get<UserRepository>()
 *     // ...
 * }
 * ```
 */
class TestFakes(
    val api   : FakeUserApiService      = FakeUserApiService(),
    val db    : FakeUserLocalDataSource = FakeUserLocalDataSource(),
    val clock : FakeClock               = FakeClock(Instant.fromEpochSeconds(0)),
)

// ── Override module ───────────────────────────────────────────────────────────

/**
 * Returns a Koin [Module] that replaces the three production bindings that
 * tests need to control:
 *
 * | Production binding       | Test replacement            |
 * |--------------------------|-----------------------------|
 * | [UserApiService]         | [FakeUserApiService]        |
 * | [UserLocalDataSource]    | [FakeUserLocalDataSource]   |
 * | [Clock]                  | [FakeClock] (fixed instant) |
 *
 * The module is declared with `override = true` so Koin silently accepts the
 * re-definition of bindings that were already registered by production modules.
 * Always append this **after** the production modules in the load order.
 *
 * Usage — minimal (only the layers under test):
 * ```kotlin
 * startKoin {
 *     modules(repositoryModule, useCaseModule, testModule(fakes))
 * }
 * ```
 *
 * Usage — full stack:
 * ```kotlin
 * startKoin {
 *     modules(appModules(testDriverFactory) + testModule(fakes))
 * }
 * ```
 */
fun testModule(fakes: TestFakes = TestFakes()): Module = module(override = true) {
    single<UserApiService>      { fakes.api   }
    single<UserLocalDataSource> { fakes.db    }
    single<Clock>               { fakes.clock }
}

// ── Lifecycle wrapper ─────────────────────────────────────────────────────────

/**
 * Starts a Koin context scoped to [block], then calls [stopKoin] in a `finally`
 * clause regardless of whether [block] throws.
 *
 * The loaded modules are: [repositoryModule], [useCaseModule], and [testModule]
 * with the provided [fakes] (override). Add extra modules via [extraModules] for
 * tests that also need ViewModels or other layers.
 *
 * The [block] receiver is [Koin] — use `get<T>()` to resolve definitions:
 * ```kotlin
 * val fakes = TestFakes()
 * withTestKoin(fakes) {                          // this: Koin
 *     val repo    = get<UserRepository>()
 *     val useCase = get<GetUsersUseCase>()
 *     // ... assertions on repo / useCase using fakes.api / fakes.db
 * }
 * ```
 *
 * If you need the ViewModel layer as well, add [viewModelModule]:
 * ```kotlin
 * withTestKoin(fakes, extraModules = listOf(viewModelModule)) { ... }
 * ```
 *
 * @param fakes        Shared fake instances for injection and direct assertion.
 * @param extraModules Additional modules (e.g. [viewModelModule]) appended after
 *                     [testModule] so their override flag is honoured.
 * @param block        Test body. Receives [Koin] for on-demand resolution.
 */
fun withTestKoin(
    fakes        : TestFakes    = TestFakes(),
    extraModules : List<Module> = emptyList(),
    block        : Koin.() -> Unit,
) {
    val koin = startKoin {
        modules(
            repositoryModule,
            useCaseModule,
            testModule(fakes),
            *extraModules.toTypedArray(),
        )
    }.koin
    try {
        koin.block()
    } finally {
        stopKoin()
    }
}
