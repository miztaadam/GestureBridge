// Top-level build file. Versions live here; modules apply the plugins without a version.
plugins {
    id("com.android.application") version "9.5.1" apply false
    // Kotlin itself is built into the Android Gradle plugin (AGP 9+); only the Compose
    // compiler plugin is applied separately.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
