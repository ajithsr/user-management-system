package com.sliide.usermanagement.di

import com.sliide.usermanagement.data.local.DatabaseDriverFactory
import com.sliide.usermanagement.data.local.SQLDelightUserLocalDataSource
import com.sliide.usermanagement.data.local.UserLocalDataSource
import com.sliide.usermanagement.data.local.db.UserDatabase
import org.koin.dsl.module

/**
 * Provides the local persistence stack as singletons.
 *
 * Singletons
 * ----------
 * • [UserDatabase]        — one SQLDelight database per process. The underlying
 *                           [SqlDriver] is created by [DatabaseDriverFactory]
 *                           which is registered by [initKoin] before this module
 *                           is loaded.
 * • [UserLocalDataSource] — bound to [SQLDelightUserLocalDataSource]; tests
 *                           swap this with an in-memory [FakeUserLocalDataSource]
 *                           using `module(override = true)`.
 *
 * Platform drivers
 * ----------------
 * [DatabaseDriverFactory] is an `expect class` whose `actual` implementations
 * live in `androidMain` (AndroidSqliteDriver) and `iosMain` (NativeSqliteDriver).
 * The factory is provided by the platform entry-point ([initKoin]) so this
 * module never has a compile-time dependency on Android or iOS APIs.
 *
 * Test overrides
 * --------------
 * ```kotlin
 * startKoin {
 *     modules(databaseModule, module(override = true) {
 *         single<UserLocalDataSource> { FakeUserLocalDataSource() }
 *     })
 * }
 * ```
 */
val databaseModule = module {

    single {
        val driver = get<DatabaseDriverFactory>().createDriver()
        UserDatabase(driver)
    }

    single<UserLocalDataSource> { SQLDelightUserLocalDataSource(get()) }
}
