package com.gesturebridge.ui

import android.Manifest
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureAction
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureClickIndicator
import androidx.wear.compose.material3.onehandedgesture.OneHandedGestureClickIndicatorState
import androidx.wear.compose.material3.onehandedgesture.oneHandedGesture
import androidx.wear.compose.material3.onehandedgesture.rememberOneHandedGestureConfiguration
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.gesturebridge.Trigger
import com.gesturebridge.WebhookDispatcher
import com.gesturebridge.data.BridgeSettings
import com.gesturebridge.data.SettingsStore
import com.gesturebridge.service.BridgeForegroundService
import com.gesturebridge.service.KeyInterceptService
import com.gesturebridge.service.KeyMonitor
import kotlinx.coroutines.launch

/**
 * The configuration screen: two webhook URLs, test buttons, and the switches
 * for the background pieces. Everything is one scrolling list built for a
 * round screen.
 */
class MainActivity : ComponentActivity() {

    @Volatile
    private var boundKeyCode: Int = SettingsStore.DEFAULT_KEY_CODE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            SettingsStore.settings(applicationContext).collect { boundKeyCode = it.buttonKeyCode }
        }
        setContent { ConfigScreen() }
    }

    /**
     * KeyEvent.Callback path: while this screen is open, Wear OS delivers the
     * extra hardware buttons (KEYCODE_STEM_1..3) straight to the activity, with
     * no accessibility service needed. It also feeds the "Last key seen" row.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) KeyMonitor.record(keyCode)
        if (keyCode == boundKeyCode && SettingsStore.canBind(keyCode)) {
            if (event.repeatCount == 0) WebhookDispatcher.fire(this, Trigger.ACTION_BUTTON)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun ConfigScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings: BridgeSettings? by remember { SettingsStore.settings(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    val lastEvent by WebhookDispatcher.lastEvent.collectAsStateWithLifecycle()
    val lastKey by KeyMonitor.lastKeyCode.collectAsStateWithLifecycle()

    // Re-checked every time the screen comes back, e.g. from system Settings.
    var keyServiceOn by remember { mutableStateOf(isKeyServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        keyServiceOn = isKeyServiceEnabled(context)
        onPauseOrDispose { }
    }

    // The keep-alive service follows the saved switch.
    LaunchedEffect(settings?.keepAlive) {
        when (settings?.keepAlive) {
            true -> BridgeForegroundService.start(context)
            false -> BridgeForegroundService.stop(context)
            null -> Unit // settings not loaded yet
        }
    }
    // The service runs with or without this permission; it only decides whether
    // its notification / watch-face indicator is visible.
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val listState = rememberScalingLazyListState()

    MaterialTheme {
        // AppScaffold draws the curved time text; ScreenScaffold draws the curved
        // scroll indicator and gives the list padding that clears the round edges.
        AppScaffold {
            ScreenScaffold(scrollState = listState) { contentPadding ->
                ScalingLazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { ListHeader { Text("GestureBridge") } }

                    // ---- Webhook URLs (saved to DataStore) ------------------
                    item {
                        UrlButton(
                            trigger = Trigger.GESTURE,
                            url = settings?.gestureUrl.orEmpty(),
                            onUrl = { scope.launch { SettingsStore.setUrl(context, Trigger.GESTURE, it) } },
                        )
                    }
                    item {
                        UrlButton(
                            trigger = Trigger.ACTION_BUTTON,
                            url = settings?.buttonUrl.orEmpty(),
                            onUrl = { scope.launch { SettingsStore.setUrl(context, Trigger.ACTION_BUTTON, it) } },
                        )
                    }

                    // ---- Send now -------------------------------------------
                    item { ListHeader { Text("Send now") } }
                    item { GestureFireButton(onFire = { WebhookDispatcher.fire(context, Trigger.GESTURE) }) }
                    item {
                        FilledTonalButton(
                            onClick = { WebhookDispatcher.fire(context, Trigger.ACTION_BUTTON) },
                            label = { Text("Fire Action Button") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Text(
                            text = lastEvent?.let { "${it.trigger.label}: ${it.result.summary}" } ?: "Nothing sent yet",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }

                    // ---- Background -----------------------------------------
                    item { ListHeader { Text("Background") } }
                    item {
                        SwitchButton(
                            checked = settings?.keepAlive == true,
                            onCheckedChange = { on ->
                                scope.launch { SettingsStore.setKeepAlive(context, on) }
                                if (on && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            label = { Text("Stay ready") },
                            secondaryLabel = { Text("Keeps the app alive") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Button(
                            onClick = { openAccessibilitySettings(context) },
                            label = { Text("Button access") },
                            secondaryLabel = { Text(if (keyServiceOn) "On" else "Off · tap to turn on") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        // Press a hardware button, then tap here to make it "the action button".
                        val seen = lastKey
                        val bindable = seen != null && SettingsStore.canBind(seen) && seen != settings?.buttonKeyCode
                        Button(
                            onClick = { seen?.let { scope.launch { SettingsStore.setButtonKeyCode(context, it) } } },
                            enabled = bindable,
                            label = { Text("Key: ${keyName(settings?.buttonKeyCode)}") },
                            secondaryLabel = {
                                Text(
                                    when {
                                        seen == null -> "Press a button to detect"
                                        bindable -> "Saw ${keyName(seen)} · tap to use"
                                        else -> "Last seen: ${keyName(seen)}"
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Shows one webhook URL; tapping opens the watch's keyboard / voice input to replace it. */
@Composable
private fun UrlButton(trigger: Trigger, url: String, onUrl: (String) -> Unit) {
    val title = "${trigger.label} URL"

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val typed = result.data
            ?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(KEY_URL)
            ?.toString()
            // Voice input likes to add spaces; a URL has none.
            ?.filterNot { it.isWhitespace() }
        if (!typed.isNullOrEmpty()) onUrl(typed)
    }

    Button(
        onClick = {
            val remoteInput = RemoteInput.Builder(KEY_URL)
                .setLabel(title)
                .wearableExtender {
                    setEmojisAllowed(false)
                    setInputActionType(EditorInfo.IME_ACTION_DONE)
                }
                .build()
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
            launcher.launch(intent)
        },
        label = { Text(title) },
        secondaryLabel = {
            Text(url.ifBlank { "Tap to set" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * "Fire Gesture 1", which is also the app's real double-pinch hook.
 *
 * `Modifier.oneHandedGesture` registers this button as the screen's primary
 * action with the Wear OS one-handed gesture framework (Wear OS 7 / API 37 and
 * up, on watches whose maker has enabled it). While this screen is showing and
 * awake, a double pinch then runs [onFire] without touching the watch. On older
 * watches the modifier does nothing and the button is a normal button.
 */
@Composable
private fun GestureFireButton(onFire: () -> Unit) {
    val gestureConfig = rememberOneHandedGestureConfiguration(action = OneHandedGestureAction.Primary)
    val indicatorState = remember { OneHandedGestureClickIndicatorState() }
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    FilledTonalButton(
        onClick = onFire,
        interactionSource = interactionSource,
        label = {
            // Briefly shows the system's "pinch" hint when the gesture is available.
            OneHandedGestureClickIndicator(gestureConfig, indicatorState) {
                Text("Fire Gesture 1")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .oneHandedGesture(
                gestureConfiguration = gestureConfig,
                interactionSource = interactionSource,
                onGestureLabel = "send webhook",
                onGestureAvailable = { scope.launch { indicatorState.showIndicator() } },
                onGesture = { onFire() },
            ),
    )
}

private const val KEY_URL = "url"

private fun keyName(keyCode: Int?): String =
    if (keyCode == null) "…" else KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

/** True when the user has switched [KeyInterceptService] on in system Settings. */
private fun isKeyServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val mine = ComponentName(context, KeyInterceptService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
}

private fun openAccessibilitySettings(context: Context) {
    // Not every watch exposes this screen to apps; fall back to main Settings.
    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
}
