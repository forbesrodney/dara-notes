import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.process.ExecOperations

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
        // Debug builds only: release builds get the build number (releaseVersionCode below).
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

/**
 * The versionCode of release builds, which must rise with every Play upload: the minutes since
 * 1970-01-01 UTC at the last commit's committer time, the same number the iOS and Mac App Store
 * builds of that commit get (scripts/build-number.sh, which says how to read one as a date).
 * Play allows up to 2100000000, which this reaches in the year 5962. Worked out afresh each
 * build, even from the configuration cache. BUILD_NUMBER or -Pdaranotes.versionCode=N sets it
 * instead, for a second upload of the same commit.
 */
abstract class CommitTimeVersionCode : ValueSource<Int, ValueSourceParameters.None> {
    @get:Inject abstract val exec: ExecOperations

    override fun obtain(): Int {
        val out = ByteArrayOutputStream()
        val seconds = runCatching {
            exec.exec {
                commandLine("git", "log", "-1", "--format=%ct")
                standardOutput = out
            }
            out.toString().trim().toLong()
        }.getOrElse { System.currentTimeMillis() / 1000 } // not a git checkout: the time now
        return (seconds / 60).toInt()
    }
}

val releaseVersionCode: Provider<Int> = providers.gradleProperty("daranotes.versionCode")
    .orElse(providers.environmentVariable("BUILD_NUMBER"))
    .map { it.trim().toInt() }
    .orElse(providers.of(CommitTimeVersionCode::class.java) {})

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { it.versionCode.set(releaseVersionCode) }
    }
}

// Release builds end by printing the version, the build number and the files made, as the
// iOS and macOS release scripts do.
val releaseVersionName = android.defaultConfig.versionName
val releaseOutputs = mapOf("assembleRelease" to "outputs/apk/release", "bundleRelease" to "outputs/bundle/release")
tasks.configureEach {
    val dir = releaseOutputs[name] ?: return@configureEach
    val outDir = layout.buildDirectory.dir(dir)
    val root = rootDir
    val code = releaseVersionCode
    val shownVersion = releaseVersionName
    doLast {
        println("== Done")
        println("Version: $shownVersion")
        println("Build:   ${code.get()}")
        outDir.get().asFile.listFiles { f -> f.extension == "apk" || f.extension == "aab" }.orEmpty()
            .sorted().forEach { println("Output:  ${it.relativeTo(root)}") }
    }
}
