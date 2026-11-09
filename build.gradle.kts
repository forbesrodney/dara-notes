plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Resolves every dependency of every module, so everything needed to build can be downloaded
// up front (scripts/fetch-deps.sh) and later builds can run offline.
tasks.register("downloadDependencies") {
    group = "build setup"
    description = "Downloads every dependency needed to build this project."
    doLast {
        var count = 0
        allprojects.forEach { p ->
            p.configurations.filter { it.isCanBeResolved }.forEach { c ->
                // Lenient: some configurations only resolve inside a specific build variant.
                count += c.incoming.artifactView { lenient(true) }.files.files.size
            }
        }
        println("Resolved $count dependency files.")
    }
}
