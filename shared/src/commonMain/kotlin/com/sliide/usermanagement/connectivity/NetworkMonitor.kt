package com.sliide.usermanagement.connectivity

import kotlinx.coroutines.flow.StateFlow

/**
 * Observes the device's internet reachability.
 *
 * [isOnline] is a hot [StateFlow] that emits `true` when the device has an
 * active network with internet capability, and `false` otherwise.  The flow
 * starts eagerly and replays the last known value to new collectors.
 *
 * Platform implementations
 * ------------------------
 * • Android — [ConnectivityManager.registerDefaultNetworkCallback] with
 *   [NET_CAPABILITY_INTERNET] check.
 * • iOS     — NWPathMonitor, path status == satisfied.
 */
expect class NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}
