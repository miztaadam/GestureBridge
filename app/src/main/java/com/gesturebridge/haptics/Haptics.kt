package com.gesturebridge.haptics

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Wrist feedback, so you know what happened without looking at the screen:
 *  - [success]: one heavy click
 *  - [failure]: two short buzzes
 */
object Haptics {

    private val SUCCESS: VibrationEffect =
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    // off 0 ms, buzz 90 ms, pause 110 ms, buzz 90 ms; -1 = do not repeat.
    private val FAILURE: VibrationEffect =
        VibrationEffect.createWaveform(
            longArrayOf(0, 90, 110, 90),
            intArrayOf(0, 255, 0, 255),
            -1,
        )

    // HARDWARE_FEEDBACK is chosen on purpose: Android drops most vibrations
    // from apps that are not on screen, but allows this usage, and it is not
    // silenced by Do Not Disturb / bedtime mode the way notification
    // vibrations are. It still follows the watch's system-vibration setting.
    private val ATTRIBUTES: VibrationAttributes =
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)

    fun success(context: Context) = vibrate(context, SUCCESS)

    fun failure(context: Context) = vibrate(context, FAILURE)

    private fun vibrate(context: Context, effect: VibrationEffect) {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (vibrator.hasVibrator()) vibrator.vibrate(effect, ATTRIBUTES)
    }
}
