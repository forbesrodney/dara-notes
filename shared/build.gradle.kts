// Platform-independent note logic: Foam templates and config, links and rename/move fixing,
// Index.md editing, checklists, the graph model and Git sync. Plain Kotlin/JVM, so it can be
// used by the Android app and, later, a desktop app. Nothing here may depend on Android.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    // JGit 5.13 is Java 8 compatible and runs on Android API 26+.
    api("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    // org.json is built into Android. Compile against it here without bundling a second copy
    // into the APK; a desktop app adds it as a normal dependency.
    compileOnly("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
