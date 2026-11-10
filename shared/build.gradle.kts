// Platform-independent note logic: Foam templates and config, links and rename/move fixing,
// Index.md editing, checklists, the graph model and Git sync. Nothing here may depend on
// Android.
//
// Kotlin Multiplatform: commonMain holds the code every platform shares, using only
// multiplatform libraries (Okio for files, kotlinx-datetime, kotlinx.serialization for JSON).
// jvmMain holds the JVM pieces: Git sync with JGit, and dates and names via java.time. The
// Android and desktop apps use this JVM target. appleMain holds the iOS (and native macOS) pieces, using Foundation.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

val libgit2Dir = layout.buildDirectory.dir("libgit2")

/** Builds libgit2 for the Apple targets when it's missing (needs Xcode and CMake). */
val buildLibgit2 by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds libgit2 for iOS, the iOS Simulator and macOS."
    commandLine(rootProject.file("scripts/build-libgit2.sh"))
    inputs.file(rootProject.file("scripts/build-libgit2.sh"))
    outputs.dir(libgit2Dir.map { it.dir("ios-arm64") })
    outputs.dir(libgit2Dir.map { it.dir("ios-simulator-arm64") })
    outputs.dir(libgit2Dir.map { it.dir("macos-arm64") })
}
tasks.matching { it.name.startsWith("cinteropLibgit2") }.configureEach { dependsOn(buildLibgit2) }

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // iPhone and iPad, and the simulator on Apple silicon Macs; native macOS only so the Apple
    // code and the shared tests run natively on the Mac (./gradlew :shared:macosArm64Test).
    // Git on these uses libgit2 (scripts/build-libgit2.sh), linked in through cinterop.
    for ((target, platform) in listOf(
        iosArm64() to "ios-arm64",
        iosSimulatorArm64() to "ios-simulator-arm64",
        macosArm64() to "macos-arm64",
    )) {
        target.compilations.getByName("main").cinterops.create("libgit2") {
            definitionFile.set(file("src/nativeInterop/cinterop/libgit2.def"))
            includeDirs(libgit2Dir.map { it.dir("$platform/include") })
            extraOpts("-libraryPath", libgit2Dir.get().dir("$platform/lib").asFile.path)
        }
    }

    compilerOptions {
        // kotlin.time.Clock, for the current date and time.
        optIn.add("kotlin.time.ExperimentalTime")
        // expect/actual objects (PlatformDates); stable in practice, still labeled Beta.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Calling Apple's C and Objective-C APIs.
        matching { s -> listOf("apple", "ios", "macos", "native").any(s.name::startsWith) }.configureEach { languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi") }
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            api("com.squareup.okio:okio:3.18.2")
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("com.squareup.okio:okio-fakefilesystem:3.18.2")
        }
        jvmMain.dependencies {
            // JGit 5.13 is Java 8 compatible and runs on Android API 26+.
            api("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
        }
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}
