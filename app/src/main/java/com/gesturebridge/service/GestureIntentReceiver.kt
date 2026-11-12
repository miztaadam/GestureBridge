package com.gesturebridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gesturebridge.Trigger
import com.gesturebridge.WebhookDispatcher

/**
 * Intent entry point for gesture triggers.
 *
 * READ THIS FIRST: there is no public "double pinch" broadcast or intent on
 * Wear OS 5 or 6. Samsung's double pinch is handled inside the system and only
 * drives fixed actions (answer a call, dismiss an alarm, play/pause media...).
 * An app cannot subscribe to it in the background.
 *
 * What does exist:
 *  1. Wear OS 7+ one-handed gestures. Double pinch is delivered to the app
 *     that is ON SCREEN through `Modifier.oneHandedGesture`. The "Fire
 *     Gesture 1" button on the config screen already uses it (see
 *     MainActivity.kt). It does nothing on older watches, and a watch maker
 *     has to enable the framework on its devices.
 *  2. This receiver: one fixed action that anything able to send a broadcast
 *     can use. If Samsung or Google ever publish a background gesture intent,
 *     add its action name to this receiver's <intent-filter> in the manifest
 *     and map it to a [Trigger] in [onReceive]; nothing else has to change.
 *
 * Test it from a computer:
 *
 *     adb shell am broadcast -a com.gesturebridge.action.TRIGGER \
 *         -n com.gesturebridge/.service.GestureIntentReceiver --es trigger gesture_1
 *
 * (use `action_button` to fire the other webhook)
 *
 * The receiver is guarded by android.permission.DUMP in the manifest, which
 * only the system and the adb shell hold.
 */
class GestureIntentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            ACTION_TRIGGER -> Trigger.fromId(intent.getStringExtra(EXTRA_TRIGGER)) ?: Trigger.GESTURE
            // Future system gesture actions go here, for example:
            // "com.example.system.action.DOUBLE_PINCH" -> Trigger.GESTURE
            else -> return
        }
        // fire() returns at once and takes its own wake lock, so the request
        // survives this receiver returning.
        WebhookDispatcher.fire(context, trigger)
    }

    companion object {
        const val ACTION_TRIGGER = "com.gesturebridge.action.TRIGGER"
        const val EXTRA_TRIGGER = "trigger"
    }
}
