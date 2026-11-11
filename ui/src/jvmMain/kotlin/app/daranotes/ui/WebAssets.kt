package app.daranotes.ui

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLDecoder
import java.net.URLStreamHandler
import java.net.spi.URLStreamHandlerProvider

/**
 * Serves the web pages to the desktop web views under a private `dara:` URL scheme, the way
 * Android's WebViewAssetLoader does: `dara://app/assets/...` from the bundled assets and
 * `dara://app/repo/...` from the notes repository. It is handled inside the app, so nothing
 * listens on the network.
 */
object WebAssets {
    const val ORIGIN = "dara://app"

    /** The notes repository, for `/repo/` URLs (images in notes). */
    @Volatile var repoRoot: File? = null

    private val types = mapOf(
        "html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8",
        "css" to "text/css; charset=utf-8", "json" to "application/json", "svg" to "image/svg+xml",
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "gif" to "image/gif",
        "webp" to "image/webp", "bmp" to "image/bmp", "avif" to "image/avif",
        "woff2" to "font/woff2", "woff" to "font/woff", "ttf" to "font/ttf",
    )

    internal fun load(rawPath: String): Pair<ByteArray, String>? {
        val path = runCatching { URLDecoder.decode(rawPath.replace("+", "%2B"), "UTF-8") }.getOrNull() ?: return null
        val type = types[path.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"
        val bytes = when {
            path.startsWith("/assets/") -> {
                val name = "assets/" + path.removePrefix("/assets/")
                if (".." in name.split('/')) return null
                WebAssets::class.java.classLoader.getResourceAsStream(name)?.use { it.readBytes() }
            }
            path.startsWith("/repo/") -> {
                val root = repoRoot?.canonicalFile ?: return null
                val f = File(root, path.removePrefix("/repo/")).canonicalFile
                if (!f.path.startsWith(root.path + File.separator) || !f.isFile) return null
                f.readBytes()
            }
            else -> null
        } ?: return null
        return bytes to type
    }
}

/** Registers the `dara:` scheme with Java's URL handling (found via META-INF/services). */
class AppUrlHandlerProvider : URLStreamHandlerProvider() {
    override fun createURLStreamHandler(protocol: String): URLStreamHandler? =
        if (protocol == "dara") AppUrlHandler else null
}

private object AppUrlHandler : URLStreamHandler() {
    override fun openConnection(u: URL): URLConnection = object : URLConnection(u) {
        private var data: Pair<ByteArray, String>? = null

        override fun connect() {
            if (data == null) data = WebAssets.load(u.path) ?: throw FileNotFoundException(u.toString())
            connected = true
        }

        override fun getInputStream(): InputStream { connect(); return ByteArrayInputStream(data!!.first) }

        override fun getContentType(): String? = runCatching { connect(); data!!.second }.getOrNull()

        override fun getContentLengthLong(): Long = runCatching { connect(); data!!.first.size.toLong() }.getOrDefault(-1)

        override fun getHeaderField(name: String?): String? = when (name?.lowercase()) {
            "content-type" -> contentType
            "content-length" -> contentLengthLong.takeIf { it >= 0 }?.toString()
            else -> null
        }
    }
}
