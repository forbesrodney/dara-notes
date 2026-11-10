// Platform-independent note logic: Foam templates and config, links and rename/move fixing,
// Index.md editing, checklists, the graph model and Git sync. Nothing here may depend on
// Android.
//
// Kotlin Multiplatform: commonMain holds the code every platform shares, using only
// multiplatform libraries (Okio for files, kotlinx-datetime, kotlinx.serialization for JSON).
// jvmMain holds the JVM pieces: Git sync with JGit, and dates and names via java.time. The
// Android app and a desktop app use this JVM target.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        // kotlin.time.Clock, for the current date and time.
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
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
