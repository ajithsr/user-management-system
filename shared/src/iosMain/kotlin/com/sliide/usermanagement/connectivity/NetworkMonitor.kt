package com.sliide.usermanagement.connectivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_serial_t

actual class NetworkMonitor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    actual val isOnline: StateFlow<Boolean> = callbackFlow {
        val monitor = nw_path_monitor_create()
        val queue   = dispatch_queue_create(
            "com.sliide.network_monitor",
            null as dispatch_queue_serial_t?
        )

        nw_path_monitor_set_update_handler(monitor) { path ->
            trySend(nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_start(monitor, queue)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }.stateIn(
        scope        = scope,
        started      = SharingStarted.Eagerly,
        initialValue = true   // NWPathMonitor fires immediately; start optimistic
    )
}
