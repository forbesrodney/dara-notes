plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

base {
    archivesName.set("daranotes")
}

android {
    namespace = "app.daranotes"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.daranotes"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // The Play upload key, from ~/.gradle/gradle.properties (never committed):
    //   DARA_KEYSTORE, DARA_KEYSTORE_PASSWORD, DARA_KEY_ALIAS, DARA_KEY_PASSWORD
    // Without them (CI, other machines), release builds use the debug key, which installs
    // directly but which Play rejects.
    val uploadKey = listOf("DARA_KEYSTORE", "DARA_KEYSTORE_PASSWORD", "DARA_KEY_ALIAS", "DARA_KEY_PASSWORD")
        .map { providers.gradleProperty(it).orNull }
    signingConfigs {
        if (uploadKey.all { it != null }) {
            create("upload") {
                storeFile = file(uploadKey[0]!!)
                storePassword = uploadKey[1]
                keyAlias = uploadKey[2]
                keyPassword = uploadKey[3]
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
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
