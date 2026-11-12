package com.gesturebridge.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gesturebridge.Trigger
import com.gesturebridge.WebhookDispatcher
import com.gesturebridge.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * THE EVENT LISTENER SERVICE.
 *
 * An accessibility service is the only way an ordinary app can see hardware
 * key presses while it is NOT on screen. Once the user switches it on
 * (Settings > Accessibility > Installed apps > GestureBridge) the system keeps
 * it bound for as long as the watch is on, including with the screen dimmed,
 * and hands it key events through [onKeyEvent] before any app sees them.
 *
 * When the bound key is released, it fires the Action Button webhook through
 * [WebhookDispatcher], which runs the network request in a coroutine.
 *
 * IMPORTANT, GALAXY WATCH: Samsung treats its hardware buttons as system keys.
 * On current Galaxy Watch Ultra firmware the Quick ("Action") Button is
 * reported NOT to reach third-party apps at all, in which case [onKeyEvent] is
 * never called for it and nothing an app does can change that. Use the
 * "Last key seen" row on the config screen to find out what your watch
 * delivers. If the button never shows up there, use the "GB Button" launcher
 * shortcut instead (see README).
 */
class KeyInterceptService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * [onKeyEvent] must answer immediately, so the bound key code is kept in
     * memory and refreshed whenever the setting changes.
     */
    @Volatile
    private var boundKeyCode: Int = SettingsStore.DEFAULT_KEY_CODE

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Key interception connected")
        scope.launch {
            SettingsStore.settings(applicationContext).collect { boundKeyCode = it.buttonKeyCode }
        }
    }

    /**
     * Called for every hardware key event the system lets through.
     *
     * @return true to consume the event (the system and other apps never see
     *         it), false to let it continue as normal.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "key ${KeyEvent.keyCodeToString(event.keyCode)} action=${event.action}")
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            KeyMonitor.record(event.keyCode)
        }

        // Only ever touch the one bound key. Everything else, and every
        // protected system key, passes straight through.
        if (event.keyCode != boundKeyCode || !SettingsStore.canBind(event.keyCode)) return false

        // Fire on release, once, and not for a press the system cancelled.
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            WebhookDispatcher.fire(applicationContext, Trigger.ACTION_BUTTON)
        }
        return true // consume both the press and the release
    }

    // No accessibility events are requested (see accessibility_service_config.xml).
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "GestureBridge"
    }
}
