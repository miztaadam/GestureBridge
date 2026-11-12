package com.gesturebridge

/**
 * The two things that can fire a webhook. Each has its own URL (and optional
 * bearer token).
 *
 * @property id    sent in the POST body, and used as the intent extra value
 * @property label shown on the watch
 */
enum class Trigger(val id: String, val label: String) {
    GESTURE("gesture_1", "Gesture 1"),
    ACTION_BUTTON("action_button", "Action Button");

    companion object {
        fun fromId(id: String?): Trigger? = entries.firstOrNull { it.id == id }
    }
}
