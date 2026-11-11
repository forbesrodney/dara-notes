package app.daranotes.ui

import java.io.File
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class WebAssetsTest {
    /**
     * The desktop web views load everything through the app's own URL scheme (WebAssets.ORIGIN):
     * the bundled pages under /assets/ (added by desktopApp's build) and the repository's files
     * under /repo/, which this checks.
     */
    @Test fun servesRepositoryFilesThroughTheAppScheme() {
        val root = createTempDirectory("repo").toFile()
        try {
            File(root, "images").mkdirs()
            File(root, "images/a.svg").writeText("<svg/>")
            WebAssets.repoRoot = root
            val text = URI("${WebAssets.ORIGIN}/repo/images/a.svg").toURL().openStream().use { it.readBytes().decodeToString() }
            assertEquals("<svg/>", text)
        } finally {
            WebAssets.repoRoot = null
            root.deleteRecursively()
        }
    }
}
