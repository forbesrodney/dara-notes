plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

base {
    archivesName.set("daranotes")
}

android {
    namespace = "app.foamforandroid"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.foamforandroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so the release APK installs directly.
            // Replace with your own signing config before distributing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        // The web pages (preview, graph, markdownlint) are shared by every app; Android serves
        // its assets folder to the WebViews at /assets/.
        getByName("main") { assets.srcDir(rootProject.file("web")) }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA",
                "about.html", "plugin.properties", "META-INF/eclipse.inf",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // The screens (Compose Multiplatform) come from :ui; note logic and Git sync from :shared.
    implementation(project(":ui"))
    // JGit logs through SLF4J; discard it.
    implementation("org.slf4j:slf4j-nop:1.7.36")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Unit tests for the note logic live in :shared.
    testImplementation("junit:junit:4.13.2")
}
