package com.sliide.usermanagement.di

import com.sliide.usermanagement.data.remote.KtorUserApiService
import com.sliide.usermanagement.data.remote.UserApiService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Provides the HTTP stack as singletons.
 *
 * Singletons
 * ----------
 * • [Json]       — shared serializer; KMP-safe, stateless after construction.
 * • [HttpClient] — one instance per process. Ktor's engine layer is thread-safe
 *                  and multiplexes requests internally; creating multiple clients
 *                  wastes socket pools and connection caches.
 * • [UserApiService] — bound to [KtorUserApiService]; tests swap this binding
 *                      with `module(override = true)`.
 *
 * Timeouts
 * --------
 * [HttpTimeout] guards against three distinct failure modes:
 *  • [HttpTimeout.HttpTimeoutCapabilityConfiguration.connectTimeoutMillis] —
 *    TCP handshake with the server took too long (bad network / wrong host).
 *  • [HttpTimeout.HttpTimeoutCapabilityConfiguration.requestTimeoutMillis] —
 *    The full request–response cycle took too long (slow server / large body).
 *  • [HttpTimeout.HttpTimeoutCapabilityConfiguration.socketTimeoutMillis]  —
 *    No bytes arrived on an established connection within the window (stalled
 *    transfer / half-open TCP).
 *
 * Test overrides
 * --------------
 * Replace any binding by including a `module(override = true)` after this one:
 * ```kotlin
 * startKoin {
 *     modules(networkModule, module(override = true) {
 *         single<UserApiService> { FakeUserApiService() }
 *     })
 * }
 * ```
 */
val networkModule = module {

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient          = true
            coerceInputValues  = true
        }
    }

    /**
     * Singleton [HttpClient].
     *
     * The engine is resolved automatically per platform:
     *  • Android → `ktor-client-android` (OkHttp wrapper)
     *  • iOS     → `ktor-client-darwin`  (NSURLSession wrapper)
     *
     * [ContentNegotiation] receives the shared [Json] instance via [get()] so
     * both the client and any manual serialization use identical settings.
     */
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(get())
            }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis  = SOCKET_TIMEOUT_MS
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }

    single<UserApiService> { KtorUserApiService(get()) }
}

// Timeout constants — extracted so they can be referenced in tests and docs.
private const val CONNECT_TIMEOUT_MS = 15_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
private const val SOCKET_TIMEOUT_MS  = 30_000L
