package com.sliide.usermanagement.di

import com.sliide.usermanagement.data.repository.UserRepositoryImpl
import com.sliide.usermanagement.domain.repository.UserRepository
import kotlinx.datetime.Clock
import org.koin.dsl.module

/**
 * Provides the domain repository as a singleton.
 *
 * Singletons
 * ----------
 * • [Clock]          — bound to [Clock.System] (production wall-clock).
 *                      Tests swap this with a [FakeClock] fixed at a known
 *                      [Instant] so time-sensitive assertions are deterministic.
 * • [UserRepository] — bound to [UserRepositoryImpl]; singleton so the in-memory
 *                      pagination cursor and [Mutex] are shared across all callers.
 *                      Use cases receive the repository via constructor injection
 *                      so they share the same cached state.
 *
 * Why [Clock] lives here
 * ----------------------
 * [Clock] is a cross-cutting infrastructure dependency injected into both the
 * repository (for `createdAt` timestamps on optimistic creates) and the feed
 * ViewModel (for relative-time formatting). Declaring it in this module — the
 * lowest-level consumer — keeps [networkModule] free of domain concerns while
 * still making the binding available to any module loaded later (Koin resolves
 * the global container on demand).
 *
 * Test overrides
 * --------------
 * ```kotlin
 * val fakeClock = FakeClock(Instant.parse("2024-01-01T12:00:00Z"))
 * startKoin {
 *     modules(repositoryModule, module(override = true) {
 *         single<UserApiService>      { FakeUserApiService() }
 *         single<UserLocalDataSource> { FakeUserLocalDataSource() }
 *         single<Clock>               { fakeClock }
 *     })
 * }
 * ```
 */
val repositoryModule = module {

    // Production wall-clock. Replaced by FakeClock in tests.
    single<Clock> { Clock.System }

    single<UserRepository> {
        UserRepositoryImpl(
            remoteDataSource = get(),
            localDataSource  = get(),
            clock            = get(),
        )
    }
}
