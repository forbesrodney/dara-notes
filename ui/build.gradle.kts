// The app's screens and their state, shared by every platform: Compose Multiplatform UI on top
// of :shared. The few platform pieces (WebViews for the preview and graph, colors, background
// sync, markdownlint) come from AppPlatform or expect/actual declarations, implemented in
// androidMain and jvmMain (the desktop app).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val javafxVersion = "21.0.13"

/** JavaFX has native code, so its jars are per platform; build on the platform you package for. */
val javafxPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
    when {
        "mac" in os -> if (arm) "mac-aarch64" else "mac"
        "win" in os -> "win"
        else -> if (arm) "linux-aarch64" else "linux"
    }
}

kotlin {
    android {
        namespace = "app.foamforandroid.ui"
        compileSdk = 37
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("kotlin.time.ExperimentalTime")
        // BackHandler in Compose Multiplatform.
        optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api("org.jetbrains.compose.runtime:runtime:1.12.2")
            api("org.jetbrains.compose.foundation:foundation:1.12.2")
            api("org.jetbrains.compose.ui:ui:1.12.2")
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.2")
            api("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
        }
        androidMain.dependencies {
            implementation("androidx.webkit:webkit:1.12.1")
        }
        jvmMain.dependencies {
            // JavaFX's WebView shows the preview and graph in the desktop app.
            for (module in listOf("base", "graphics", "controls", "media", "web", "swing")) {
                implementation("org.openjfx:javafx-$module:$javafxVersion:$javafxPlatform")
            }
        }
    }
}
