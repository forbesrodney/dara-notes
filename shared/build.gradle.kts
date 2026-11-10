// Platform-independent note logic: Foam templates and config, links and rename/move fixing,
// Index.md editing, checklists, the graph model and Git sync. Nothing here may depend on
// Android.
//
// Kotlin Multiplatform: commonMain holds code that uses only the Kotlin standard library, so it
// can be compiled for every target. jvmMain holds code that still needs Java libraries (JGit,
// java.io.File, org.json, java.time); the Android app and a desktop app use this JVM target.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            // JGit 5.13 is Java 8 compatible and runs on Android API 26+.
            api("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
            // org.json is built into Android. Compile against it here without bundling a
            // second copy into the APK; a desktop app adds it as a normal dependency.
            compileOnly("org.json:json:20240303")
        }
        jvmTest.dependencies {
            implementation("junit:junit:4.13.2")
            implementation("org.json:json:20240303")
        }
    }
}
