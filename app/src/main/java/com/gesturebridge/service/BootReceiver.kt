package com.gesturebridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gesturebridge.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Brings the keep-alive service back after a reboot, if the user had it on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Reading DataStore is asynchronous; goAsync() keeps the receiver alive
        // until the read is done.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (SettingsStore.current(context).keepAlive) {
                    BridgeForegroundService.start(context)
                }
            } catch (e: Exception) {
                // Starting can be refused (for example if the user revoked a
                // permission); the service starts again next time the app opens.
                Log.w("GestureBridge", "Could not start keep-alive after boot", e)
            } finally {
                pending.finish()
            }
        }
    }
}
