# GestureBridge

A Wear OS app that sends an HTTP POST webhook (Home Assistant, Tasker, anything
that accepts one) from your watch, and confirms the result on your wrist:
one heavy click for success, a double buzz for failure.

Built for the Galaxy Watch Ultra (Wear OS 5 and up). Kotlin, Compose for Wear OS
(Material 3), Ktor, DataStore.

## Ways to fire a webhook

| Trigger | Works when | Notes |
|---|---|---|
| **Launcher shortcuts** "GB Gesture" / "GB Button" | Always | Assign one to a hardware button in the watch's *Settings > Buttons and gestures*. Fires and closes without showing a screen. |
| **Hardware key via accessibility service** | Screen on, dimmed, or in another app | Only for keys the watch delivers to apps. See the limits below. |
| **Hardware key while the app is open** | Config screen showing | No accessibility service needed. |
| **Double pinch** | Config screen showing, Wear OS 7+ | Uses the Wear OS one-handed gesture API on the "Fire Gesture 1" button. |
| **Broadcast intent** | Always | For `adb` and future system gesture intents. |
| **On-screen buttons** | Config screen showing | For testing. |

## Platform limits you should know about

- **Galaxy Watch hardware buttons are system keys.** Samsung does not hand the
  Quick ("Action") Button to third-party apps on current firmware, so the
  accessibility service may never see it. Open the app, press the button, and
  look at the last row of the config screen: if it shows a key name, tap the
  row to bind it. If nothing shows up, use a launcher shortcut instead.
- **There is no background double-pinch intent** on Wear OS 5 or 6. Double pinch
  reaches third-party apps only through the Wear OS 7 one-handed gesture API,
  and only while the app is on screen and awake. `GestureIntentReceiver.kt` is
  the place to wire in a system gesture intent if one is ever published.

## Setup

1. Copy `local.properties.example` to `local.properties`.
2. **Bearer tokens go in `local.properties`**, not in source code:
   `GESTURE_BEARER_TOKEN` and `BUTTON_BEARER_TOKEN`. Leave them empty for Home
   Assistant `/api/webhook/<id>` URLs and for Tasker, which need none.
3. Optionally set `DEFAULT_GESTURE_URL` and `DEFAULT_BUTTON_URL` there too, so
   you don't have to type URLs on the watch. Use the server's IP address.

## Build

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Opening the
project in Android Studio and pressing Run does the same and installs it.

## Install on the watch

1. On the watch: *Settings > About watch > Software information*, tap
   *Software version* seven times. Then *Settings > Developer options*: turn on
   *ADB debugging* and *Wireless debugging*.
2. In *Wireless debugging*, choose *Pair new device* and, on a computer on the
   same Wi-Fi, run the following with the address and code the watch shows:

```bash
adb pair <watch-ip>:<pairing-port>
```

```bash
adb connect <watch-ip>:<port>
```

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Turn on the pieces

- **Stay ready** (in the app): runs the foreground service and Ongoing Activity
  so the app stays alive in the background.
- **Button access** (in the app): opens Accessibility settings; switch
  GestureBridge on under *Installed apps*. If the watch does not list it, enable
  it from the computer:

```bash
adb shell settings put secure enabled_accessibility_services com.gesturebridge/com.gesturebridge.service.KeyInterceptService
```

## Test a trigger from the computer

```bash
adb shell am broadcast -a com.gesturebridge.action.TRIGGER -n com.gesturebridge/.service.GestureIntentReceiver --es trigger gesture_1
```

Use `action_button` for the other webhook. To watch what the app sees:

```bash
adb logcat -s GestureBridge
```

## What gets sent

`POST <your URL>` with `Content-Type: application/json` and, if a token is set,
`Authorization: Bearer <token>`:

```json
{"source":"gesturebridge","trigger":"action_button","timestamp":1731400000000}
```

In a Home Assistant webhook automation the body is available as `trigger.json`.

## Where things are

| File | What it does |
|---|---|
| `app/src/main/AndroidManifest.xml` | Permissions, services, receivers, launcher shortcuts |
| `ui/MainActivity.kt` | Config screen (`ScalingLazyColumn`), saves to DataStore |
| `service/KeyInterceptService.kt` | Background hardware-key listener |
| `service/BridgeForegroundService.kt` | Keep-alive foreground service + Ongoing Activity |
| `service/GestureIntentReceiver.kt` | Intent entry point for gesture triggers |
| `ui/FireActivity.kt` | Invisible activity behind the launcher shortcuts |
| `WebhookDispatcher.kt` | Wake lock, coroutine, haptics; every trigger ends up here |
| `net/WebhookClient.kt` | Ktor singleton that sends the POST |
| `haptics/Haptics.kt` | Success and failure vibration patterns |
| `data/SettingsStore.kt` | DataStore-backed settings |

## Tests

```bash
./gradlew testDebugUnitTest
```

Sends real requests from the network client to a local test server and checks
the method, headers, body and error handling.
