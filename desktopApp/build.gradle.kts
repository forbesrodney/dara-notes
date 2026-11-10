// The desktop app (macOS now; Windows and Linux use the same code): the shared :ui screens in
// a Compose Desktop window. Git uses JGit, as on Android.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    // Dispatchers.Main on the desktop: the Swing/Compose UI thread.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    // The access token goes in the system keychain (macOS Keychain, Windows Credential
    // Manager, Secret Service on Linux).
    implementation("com.github.javakeyring:java-keyring:1.0.4")
    // JGit logs through SLF4J; discard it. (2.x, matching the SLF4J API the desktop libraries use.)
    implementation("org.slf4j:slf4j-nop:2.0.7")
}

// The web pages (preview, graph, markdownlint), served to the web views at /assets/.
tasks.processResources {
    from(rootProject.file("web")) { into("assets") }
}

compose.desktop {
    application {
        mainClass = "app.foamforandroid.desktop.MainKt"

        // Packaging bundles a Java runtime made from this JDK, so it must be self-contained:
        // Homebrew's openjdk links to Homebrew libraries and would only run on Macs that have
        // them. Uses foam.packagingJdk from ~/.gradle/gradle.properties if set, else a Temurin
        // 21 found in the usual places (e.g. from brew install --cask temurin@21).
        packagingJdk()?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // Named for the system it's built on: "Foam for macOS", "Foam for Windows", ...
            packageName = "Foam for " + System.getProperty("os.name").lowercase().let {
                when {
                    "mac" in it -> "macOS"
                    "win" in it -> "Windows"
                    else -> "Linux"
                }
            }
            packageVersion = "1.0.0"
            description = "Markdown notes compatible with Foam, synced with Git"
            vendor = "Foam for Android"
            // The parts of Java the app uses (from ./gradlew :desktopApp:suggestRuntimeModules, plus
            // java.naming and jdk.crypto.ec for HTTPS). java.management: JGit; jdk.jsobject:
            // JavaFX's web view talks to JavaScript through it.
            modules(
                "java.instrument", "java.management", "java.naming", "java.net.http", "java.prefs",
                "java.security.jgss", "java.sql", "jdk.crypto.ec", "jdk.jfr", "jdk.jsobject",
                "jdk.security.auth", "jdk.unsupported", "jdk.unsupported.desktop", "jdk.xml.dom",
            )
            // Drawn from art/foam_icon.svg by scripts/app-icons.py.
            macOS {
                bundleID = "app.foamforandroid"
                iconFile.set(project.file("icons/foam.icns"))
            }
            windows {
                iconFile.set(project.file("icons/foam.ico"))
            }
            linux {
                iconFile.set(project.file("icons/foam.png"))
            }
        }
    }
}

/** The JDK to package with: foam.packagingJdk, else the newest Eclipse Temurin 21 installed. */
fun packagingJdk(): String? {
    (findProperty("foam.packagingJdk") as String?)?.let { return it }
    val home = System.getProperty("user.home")
    val roots = listOf(
        "/Library/Java/JavaVirtualMachines", "$home/Library/Java/JavaVirtualMachines", // macOS
        "C:/Program Files/Eclipse Adoptium", // Windows
        "/usr/lib/jvm", // Linux
    )
    return roots.asSequence()
        .flatMap { File(it).listFiles()?.asSequence() ?: emptySequence() }
        .map { jdk -> File(jdk, "Contents/Home").takeIf { it.isDirectory } ?: jdk }
        .filter { home ->
            val release = File(home, "release").takeIf { it.isFile }?.readText() ?: return@filter false
            "IMPLEMENTOR=\"Eclipse Adoptium\"" in release && Regex("JAVA_VERSION=\"21[.\"]").containsMatchIn(release)
        }
        .maxByOrNull { File(it, "release").readText().substringAfter("JAVA_VERSION=\"").substringBefore('"') }
        ?.path
}
