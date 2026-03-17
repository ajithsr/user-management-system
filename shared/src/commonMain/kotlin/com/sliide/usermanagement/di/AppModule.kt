package com.sliide.usermanagement.di

import com.sliide.usermanagement.connectivity.NetworkMonitor
import com.sliide.usermanagement.data.local.DatabaseDriverFactory
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Returns the ordered list of production Koin modules for the given platform
 * [driverFactory].
 *
 * Exposing the list (rather than only exposing [initKoin]) lets tests compose
 * their own subsets and inject test-double modules after the production ones:
 *
 * ```kotlin
 * // Integration test — full stack with fakes swapped in
 * startKoin {
 *     modules(appModules(testDriverFactory) + testModule())
 * }
 *
 * // Unit test — only repository + use-case layer
 * startKoin {
 *     modules(repositoryModule, useCaseModule, testModule())
 * }
 * ```
 *
 * Module load order is meaningful for Koin 4.x: each module's [single] / [factory]
 * definitions are registered in the order the modules appear.  Override modules
 * (created with `module(override = true)`) must come **after** the modules whose
 * bindings they replace, so append test modules at the end of the list.
 */
fun appModules(
    driverFactory  : DatabaseDriverFactory,
    networkMonitor : NetworkMonitor,
): List<Module> = listOf(
    // Platform entry-points: expect classes whose actual implementations carry
    // platform-specific dependencies (Android Context, iOS NWPathMonitor, etc.).
    // Binding them here keeps all other modules free of platform-specific imports.
    module {
        single { driverFactory }
        single { networkMonitor }
    },

    networkModule,    // Json, HttpClient (singleton), UserApiService
    databaseModule,   // UserDatabase, UserLocalDataSource
    repositoryModule, // Clock, UserRepository
    useCaseModule,    // GetUsersUseCase, GetUserDetailUseCase
    viewModelModule,  // all ViewModels
)

/**
 * Starts the Koin container with the full production module graph.
 *
 * @param driverFactory  Platform-specific SQLite driver factory. On Android,
 *                       pass `DatabaseDriverFactory(context)`; on iOS the
 *                       no-arg `DatabaseDriverFactory()` is used.
 * @param networkMonitor Platform-specific connectivity monitor. On Android,
 *                       pass `NetworkMonitor(context)`; on iOS `NetworkMonitor()`.
 * @param appDeclaration Optional lambda for platform-specific Koin configuration
 *                       (e.g. `androidContext(this)` on Android). Executed before
 *                       the module list is loaded.
 */
fun initKoin(
    driverFactory  : DatabaseDriverFactory,
    networkMonitor : NetworkMonitor,
    appDeclaration : KoinAppDeclaration = {},
) = startKoin {
    appDeclaration()
    modules(appModules(driverFactory, networkMonitor))
}
