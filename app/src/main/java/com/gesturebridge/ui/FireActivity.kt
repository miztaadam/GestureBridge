package com.gesturebridge.ui

import android.app.Activity
import android.os.Bundle
import com.gesturebridge.Trigger
import com.gesturebridge.WebhookDispatcher

/**
 * Invisible "fire and close" activity behind the two launcher shortcuts
 * ("GB Gesture" and "GB Button").
 *
 * This is the trigger path that does not depend on key interception: the
 * watch's own settings can assign an app to a hardware button
 * (Settings > Buttons and gestures), and opening that "app" lands here. It
 * sends the webhook and closes again without showing anything; the haptic tells
 * you the result.
 */
class FireActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The intent's component is the alias that was launched, which tells us
        // which webhook to send.
        val trigger = when (intent?.component?.className) {
            ALIAS_BUTTON -> Trigger.ACTION_BUTTON
            else -> Trigger.GESTURE
        }
        WebhookDispatcher.fire(this, trigger)

        // Theme.NoDisplay requires finishing before onCreate returns.
        finish()
    }

    private companion object {
        const val ALIAS_BUTTON = "com.gesturebridge.FireButton"
    }
}
