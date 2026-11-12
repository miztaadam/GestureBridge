package com.gesturebridge.data

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gesturebridge.BuildConfig
import com.gesturebridge.Trigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Everything the user can configure. */
data class BridgeSettings(
    val gestureUrl: String,
    val buttonUrl: String,
    /** Run the foreground service / Ongoing Activity. */
    val keepAlive: Boolean,
    /** The hardware key code that counts as "the action button". */
    val buttonKeyCode: Int,
) {
    fun urlFor(trigger: Trigger): String = when (trigger) {
        Trigger.GESTURE -> gestureUrl
        Trigger.ACTION_BUTTON -> buttonUrl
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gesturebridge_settings")

/** Reads and writes [BridgeSettings] in Jetpack DataStore. */
object SettingsStore {

    private val GESTURE_URL = stringPreferencesKey("gesture_url")
    private val BUTTON_URL = stringPreferencesKey("button_url")
    private val KEEP_ALIVE = booleanPreferencesKey("keep_alive")
    private val BUTTON_KEY_CODE = intPreferencesKey("button_key_code")

    /** Standard Wear OS code for the first extra hardware button. */
    const val DEFAULT_KEY_CODE = KeyEvent.KEYCODE_STEM_1

    /**
     * Keys that must never be swallowed: binding one of these would take away
     * the user's way to go back, go home or turn the watch off.
     */
    private val PROTECTED_KEYS = setOf(
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_STEM_PRIMARY,
        KeyEvent.KEYCODE_SLEEP,
        KeyEvent.KEYCODE_WAKEUP,
    )

    fun canBind(keyCode: Int): Boolean = keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode !in PROTECTED_KEYS

    fun settings(context: Context): Flow<BridgeSettings> =
        context.applicationContext.dataStore.data.map { prefs ->
            BridgeSettings(
                // Until the user edits a URL on the watch, the value from
                // local.properties (DEFAULT_*_URL) is used.
                gestureUrl = prefs[GESTURE_URL] ?: BuildConfig.DEFAULT_GESTURE_URL,
                buttonUrl = prefs[BUTTON_URL] ?: BuildConfig.DEFAULT_BUTTON_URL,
                keepAlive = prefs[KEEP_ALIVE] ?: false,
                buttonKeyCode = prefs[BUTTON_KEY_CODE] ?: DEFAULT_KEY_CODE,
            )
        }

    suspend fun current(context: Context): BridgeSettings = settings(context).first()

    suspend fun setUrl(context: Context, trigger: Trigger, url: String) {
        context.applicationContext.dataStore.edit { prefs ->
            val key = if (trigger == Trigger.GESTURE) GESTURE_URL else BUTTON_URL
            prefs[key] = url.trim()
        }
    }

    suspend fun setKeepAlive(context: Context, enabled: Boolean) {
        context.applicationContext.dataStore.edit { it[KEEP_ALIVE] = enabled }
    }

    /** @return false (and stores nothing) if [keyCode] is a protected system key. */
    suspend fun setButtonKeyCode(context: Context, keyCode: Int): Boolean {
        if (!canBind(keyCode)) return false
        context.applicationContext.dataStore.edit { it[BUTTON_KEY_CODE] = keyCode }
        return true
    }
}
