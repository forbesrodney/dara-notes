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
    }
}
