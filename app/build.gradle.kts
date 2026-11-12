import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Tokens and default URLs come from the git-ignored local.properties file
// (see local.properties.example), so they never live in source code.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** Reads [key] from local.properties and returns it as a quoted Java string literal. */
fun localString(key: String): String {
    val raw = localProps.getProperty(key, "").trim()
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.gesturebridge"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gesturebridge"
        minSdk = 34      // Wear OS 5, which the Galaxy Watch Ultra shipped with.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GESTURE_BEARER_TOKEN", localString("GESTURE_BEARER_TOKEN"))
        buildConfigField("String", "BUTTON_BEARER_TOKEN", localString("BUTTON_BEARER_TOKEN"))
        buildConfigField("String", "DEFAULT_GESTURE_URL", localString("DEFAULT_GESTURE_URL"))
        buildConfigField("String", "DEFAULT_BUTTON_URL", localString("DEFAULT_BUTTON_URL"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // UI: Compose for Wear OS, Material 3
    implementation("androidx.wear.compose:compose-material3:1.7.1")
    implementation("androidx.wear.compose:compose-foundation:1.7.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")

    // Text entry on the watch (system keyboard / voice) and the Ongoing Activity chip
    implementation("androidx.wear:wear-input:1.2.0")
    implementation("androidx.wear:wear-ongoing:1.1.0")

    // Settings storage
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    // Networking: Ktor client on the platform's built-in HttpURLConnection engine,
    // so no OkHttp and no serialization library are pulled in.
    implementation("io.ktor:ktor-client-core:3.6.1")
    implementation("io.ktor:ktor-client-android:3.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
}
