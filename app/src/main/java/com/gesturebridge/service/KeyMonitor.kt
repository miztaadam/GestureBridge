package com.gesturebridge.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the most recent hardware key the app saw (from the accessibility
 * service or from the config screen while it is open).
 *
 * The config screen shows it, so you can press the Action Button, see which
 * key code this watch really sends, and bind it with one tap. If nothing shows
 * up when you press a button, the system is not delivering that button to apps.
 */
object KeyMonitor {
    private val _lastKeyCode = MutableStateFlow<Int?>(null)
    val lastKeyCode: StateFlow<Int?> = _lastKeyCode.asStateFlow()

    fun record(keyCode: Int) {
        _lastKeyCode.value = keyCode
    }
}
