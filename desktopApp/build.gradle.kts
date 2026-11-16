// The desktop app (macOS, Windows and Linux, from the same code): the shared :ui screens in
// a Compose Desktop window. Git uses JGit, as on Android.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.util.Properties
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
    // The access token goes in the system's secret store: the macOS Keychain (through JNA),
    // Windows Credential Manager or the Secret Service on Linux (java-keyring).
    implementation("com.github.javakeyring:java-keyring:1.0.4") {
        // Its macOS helper is an unsigned native library, which notarization rejects; the app
        // talks to the macOS Keychain through JNA instead (Secrets.kt).
        exclude(group = "pt.davidafsilva.apple", module = "jkeychain")
    }
    implementation("net.java.dev.jna:jna:5.13.0")
    // JGit logs through SLF4J; discard it. (2.x, matching the SLF4J API the desktop libraries use.)
    implementation("org.slf4j:slf4j-nop:2.0.7")
}

// The web pages (preview, graph, markdownlint), served to the web views at /assets/.
tasks.processResources {
    from(rootProject.file("web")) { into("assets") }
}

/** Whether this is the Mac App Store build (-Pdaranotes.appStore=true; scripts/macos-appstore.sh). */
val isAppStoreBuild = findProperty("daranotes.appStore")?.toString() == "true"

/** CFBundleVersion for App Store uploads, which must rise each time: -Pdaranotes.buildNumber (MAJOR.MINOR.PATCH). */
val buildNumber = findProperty("daranotes.buildNumber")?.toString() ?: "1"

compose.desktop {
    application {
        mainClass = "app.daranotes.desktop.MainKt"

        if (isAppStoreBuild) {
            // A sandboxed app can't load native code it unpacks at run time, so the App Store
            // build has JNA's library next to the jars (scripts/macos-appstore.sh puts it, and
            // JavaFX's, there), instead of unpacking it from its jar.
            jvmArgs("-Djna.boot.library.path=${'$'}APPDIR", "-Djna.nounpack=true", "-Djna.nosys=true")
        }

        // Packaging bundles a Java runtime made from this JDK, so it must be self-contained:
        // Homebrew's openjdk links to Homebrew libraries and would only run on Macs that have
        // them. Uses daranotes.packagingJdk from ~/.gradle/gradle.properties if set, else a Temurin
        // 21 found in the usual places (e.g. from brew install --cask temurin@21).
        packagingJdk()?.let { javaHome = it }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Dara Notes"
            packageVersion = "1.0.0"
            description = "Markdown notes synced with Git; works with Foam workspaces"
            vendor = "Snakepit Software"
            // The parts of Java the app uses (from ./gradlew :desktopApp:suggestRuntimeModules, plus
            // java.naming and jdk.crypto.ec for HTTPS). java.management: JGit; jdk.jsobject:
            // JavaFX's web view talks to JavaScript through it.
            modules(
                "java.instrument", "java.management", "java.naming", "java.net.http", "java.prefs",
                "java.security.jgss", "java.sql", "jdk.crypto.ec", "jdk.jfr", "jdk.jsobject",
                "jdk.security.auth", "jdk.unsupported", "jdk.unsupported.desktop", "jdk.xml.dom",
            )
            // Drawn by scripts/app-icons.py (the design is in scripts/KnotIcon.java).
            macOS {
                bundleID = "app.daranotes"
                iconFile.set(project.file("icons/app.icns"))
                appCategory = "public.app-category.productivity"
                minimumSystemVersion = "12.0"
                // Uses only exempt encryption (HTTPS for sync), so App Store Connect doesn't ask
                // about export compliance on each upload (the iOS Info.plist says the same).
                infoPlist {
                    extraKeysRawXml = """
                        <key>ITSAppUsesNonExemptEncryption</key>
                        <false/>
                    """.trimIndent()
                }
                if (isAppStoreBuild) {
                    // The Mac App Store / TestFlight build (scripts/macos-appstore.sh):
                    // sandboxed, signed with the Mac App Distribution certificate, with the
                    // provisioning profiles from desktopApp/appstore/ (not committed).
                    appStore = true
                    packageBuildVersion = buildNumber
                    val team = appleTeamId() ?: error("Set apple.teamId in local.properties for the App Store build.")
                    signing {
                        sign.set(true)
                        identity.set(signingIdentity("3rd Party Mac Developer Application", team)
                            ?: error("No \"3rd Party Mac Developer Application\" (Mac App Distribution) certificate for team $team in the keychain."))
                    }
                    provisioningProfile.set(project.file("appstore/embedded.provisionprofile"))
                    runtimeProvisioningProfile.set(project.file("appstore/runtime.provisionprofile"))
                    entitlementsFile.set(teamEntitlements("entitlements.plist", team))
                    runtimeEntitlementsFile.set(teamEntitlements("runtime-entitlements.plist", team))
                } else {
                    // Signed with your Developer ID certificate when local.properties has
                    // apple.teamId (scripts/macos-release.sh then notarizes the .dmg).
                    developerIdName()?.let { name ->
                        signing {
                            sign.set(true)
                            identity.set(name)
                        }
                    }
                }
            }
            windows {
                iconFile.set(project.file("icons/app.ico"))
            }
            linux {
                iconFile.set(project.file("icons/app.png"))
                // Package names can't have spaces; installed as /opt/dara-notes, with a menu entry.
                packageName = "dara-notes"
                debMaintainer = "Snakepit Software <https://github.com/forbesrodney/dara-notes>"
                menuGroup = "Office"
                appCategory = "Office"
                rpmLicenseType = "MIT"
                shortcut = true
            }
        }
    }
}

/** The JDK to package with: daranotes.packagingJdk, else the newest Eclipse Temurin 21 installed. */
fun packagingJdk(): String? {
    (findProperty("daranotes.packagingJdk") as String?)?.let { return it }
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

/** The Apple Developer team ID from local.properties (apple.teamId), if set. */
fun appleTeamId(): String? {
    val props = Properties()
    val local = rootProject.file("local.properties")
    if (local.isFile) local.reader().use { props.load(it) }
    return props.getProperty("apple.teamId")?.trim()?.ifEmpty { null }
}

/** The full name of the [kind] certificate for [team] in the keychain, e.g. "3rd Party Mac Developer Application: Jane Doe (ABCDE12345)". */
fun signingIdentity(kind: String, team: String): String? {
    val out = runCatching {
        ProcessBuilder("security", "find-identity", "-v", "-p", "codesigning")
            .redirectErrorStream(true).start().inputStream.bufferedReader().readText()
    }.getOrDefault("")
    return Regex("\"(${Regex.escape(kind)}: .+ \\(${Regex.escape(team)}\\))\"").find(out)?.groupValues?.get(1)
}

/** desktopApp/appstore/[name] with TEAM_ID replaced by [team], written to the build directory. */
fun teamEntitlements(name: String, team: String): File {
    val out = layout.buildDirectory.file("appstore/$name").get().asFile
    out.parentFile.mkdirs()
    out.writeText(project.file("appstore/$name").readText().replace("TEAM_ID", team))
    return out
}

/**
 * The name on the "Developer ID Application" certificate in your keychain for the team in
 * local.properties (apple.teamId), e.g. "Jane Doe (ABCDE12345)"; null if either is missing.
 */
fun developerIdName(): String? {
    val props = Properties()
    val local = rootProject.file("local.properties")
    if (local.isFile) local.reader().use { props.load(it) }
    val team = props.getProperty("apple.teamId")?.trim()?.ifEmpty { null } ?: return null
    if ("mac" !in System.getProperty("os.name").lowercase()) return null
    val out = runCatching {
        ProcessBuilder("security", "find-identity", "-v", "-p", "codesigning")
            .redirectErrorStream(true).start().inputStream.bufferedReader().readText()
    }.getOrDefault("")
    return Regex("\"Developer ID Application: (.+ \\($team\\))\"").find(out)?.groupValues?.get(1)
}
