package com.gesturebridge

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.gesturebridge.data.SettingsStore
import com.gesturebridge.haptics.Haptics
import com.gesturebridge.net.WebhookClient
import com.gesturebridge.net.WebhookResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** What the last trigger did; shown on the config screen. */
data class FireEvent(val trigger: Trigger, val result: WebhookResult, val atMillis: Long)

/**
 * The one place every trigger source ends up: the accessibility service, the
 * launcher shortcuts, the gesture intent receiver and the on-screen test buttons
 * all call [fire].
 *
 * It owns a process-wide coroutine scope, so the request keeps running after
 * the caller (an activity that already closed, a broadcast receiver that
 * already returned) is gone.
 */
object WebhookDispatcher {

    private const val TAG = "GestureBridge"

    /** Ignore a second fire of the same trigger within this window (key bounce, double delivery). */
    private const val DEBOUNCE_MS = 600L

    /** Upper bound for holding the CPU awake; the HTTP timeouts are shorter. */
    private const val WAKE_LOCK_TIMEOUT_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastFiredAt = ConcurrentHashMap<Trigger, Long>()

    private val _lastEvent = MutableStateFlow<FireEvent?>(null)
    val lastEvent: StateFlow<FireEvent?> = _lastEvent.asStateFlow()

    /**
     * Sends the webhook configured for [trigger], then vibrates:
     * one heavy click on success, a double buzz on any failure.
     * Returns immediately; safe to call from any thread.
     */
    fun fire(context: Context, trigger: Trigger) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastFiredAt.put(trigger, now)
        if (previous != null && now - previous < DEBOUNCE_MS) return

        val appContext = context.applicationContext

        // The trigger may arrive while the watch is dozing. Without a wake lock
        // the CPU can go back to sleep before the request has left the radio.
        val wakeLock = appContext.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GestureBridge:fire")
            .apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }

        scope.launch {
            try {
                val url = SettingsStore.current(appContext).urlFor(trigger)
                val result = if (url.isBlank()) {
                    WebhookResult.NetworkError("No URL set")
                } else {
                    WebhookClient.post(url, trigger)
                }

                Log.i(TAG, "${trigger.id} -> ${result.summary}")
                _lastEvent.value = FireEvent(trigger, result, System.currentTimeMillis())

                if (result.isSuccess) Haptics.success(appContext) else Haptics.failure(appContext)
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }
}
