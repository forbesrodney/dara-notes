package app.daranotes.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import okio.Path
import kotlinx.serialization.json.Json as JsonParser

/**
 * The repository's cSpell settings (the Code Spell Checker extension in VS Code), from
 * `cspell.json` and the `cSpell.*` keys in `.vscode/settings.json`, combined: words from
 * either are accepted, and paths from either are skipped.
 */
data class CSpellConfig(
    /** Accepted words, lowercase: `words`, `userWords` and `ignoreWords`. */
    val words: Set<String> = emptySet(),
    /** Words always flagged, lowercase, each with the replacement written as `hte->the`, if any. */
    val flagWords: Map<String, String?> = emptyMap(),
    /** Globs for files not to check (`ignorePaths`). */
    val ignorePaths: List<String> = emptyList(),
) {
    /**
     * Whether [path] (repository-relative) is in [ignorePaths]. As in cSpell (and .gitignore),
     * a pattern without a slash matches at any depth: `*.tmp.md` or `drafts`.
     */
    fun ignores(path: String): Boolean = ignorePaths.any { glob ->
        val g = glob.trim().removePrefix("./")
        Globs.matches(g, path) || (!g.trimEnd('/').contains('/') && Globs.matches("**/$g", path))
    }

    companion object {
        const val CSPELL_JSON = "cspell.json"
        const val VSCODE_SETTINGS = ".vscode/settings.json"

        fun load(fs: FileSystem, root: Path): CSpellConfig {
            fun text(name: String): String? = (root / name).takeIf { fs.metadataOrNull(it)?.isRegularFile == true }
                ?.let { runCatching { fs.read(it) { readUtf8() } }.getOrNull() }
            return parse(text(CSPELL_JSON), text(VSCODE_SETTINGS))
        }

        fun parse(cspellJson: String?, settingsJson: String?): CSpellConfig {
            fun obj(text: String?): JsonObject = text?.let {
                runCatching { JsonParser.parseToJsonElement(FoamConfig.stripJsonc(it)).jsonObject }.getOrNull()
            } ?: JsonObject(emptyMap())
            val cspell = obj(cspellJson)
            val vscode = obj(settingsJson)
            fun strings(key: String): List<String> =
                listOf(cspell[key], vscode["cSpell.$key"]).flatMap { v ->
                    (v as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim() }
                }.filter { it.isNotEmpty() }
            val words = (strings("words") + strings("userWords") + strings("ignoreWords"))
                .map { Spelling.fold(it) }.toSet()
            val flags = strings("flagWords").associate { entry ->
                val parts = entry.split("->", limit = 2)
                Spelling.fold(parts[0].trim()) to parts.getOrNull(1)?.trim()?.ifEmpty { null }
            }
            return CSpellConfig(words, flags, strings("ignorePaths"))
        }

        /**
         * Adds [word] to the repository's dictionary, as VS Code's "Add to workspace
         * dictionary" does: to `words` in cspell.json if the repository has one, otherwise to
         * `cSpell.words` in .vscode/settings.json (created if needed). Returns the file changed.
         */
        fun addWord(fs: FileSystem, root: Path, word: String): String {
            val cspell = root / CSPELL_JSON
            val (file, key) = if (fs.metadataOrNull(cspell)?.isRegularFile == true) CSPELL_JSON to "words"
            else VSCODE_SETTINGS to "cSpell.words"
            val path = root / file
            val old = if (fs.exists(path)) fs.read(path) { readUtf8() } else null
            val new = Jsonc.addToArray(old, key, word)
            if (new != old) {
                path.parent?.let(fs::createDirectories)
                fs.write(path) { writeUtf8(new) }
            }
            return file
        }
    }
}

/** Small edits to VS Code's JSON with comments, leaving the rest of the file as it was. */
object Jsonc {
    /**
     * [text] with [value] added to the array at the top-level [key] (made if missing), or
     * unchanged if it's already there. A null or blank [text] becomes a new object.
     */
    fun addToArray(text: String?, key: String, value: String): String {
        val quoted = Json.quote(value)
        if (text.isNullOrBlank()) return "{\n  ${Json.quote(key)}: [\n    $quoted\n  ]\n}\n"
        val crlf = "\r\n" in text
        val src = text.replace("\r\n", "\n")
        val keyAt = findKey(src, key)
        val out = if (keyAt == null) {
            insertKey(src, key, quoted)
        } else {
            val open = nextCode(src, keyAt.second)?.takeIf { src[it] == ':' }?.let { nextCode(src, it + 1) }
            if (open == null || src[open] != '[') return text // not an array: leave it alone
            val close = matching(src, open) ?: return text
            val inner = src.substring(open + 1, close)
            val existing = runCatching {
                (JsonParser.parseToJsonElement(FoamConfig.stripJsonc("[$inner]")) as JsonArray)
                    .mapNotNull { (it as? JsonPrimitive)?.content }
            }.getOrDefault(emptyList())
            if (value in existing) return text
            insertItem(src, open, close, quoted)
        }
        return if (crlf) out.replace("\n", "\r\n") else out
    }

    /** Adds an item before the array's closing bracket at [close], in the array's style. */
    private fun insertItem(src: String, open: Int, close: Int, quoted: String): String {
        val last = lastCode(src, open + 1, close)
        if (last == null) {
            // Empty: one item, on its own line if the brackets already are.
            val multiline = src.substring(open, close).contains('\n')
            if (!multiline) return src.substring(0, open + 1) + quoted + src.substring(close)
            val closeIndent = lineIndent(src, close)
            return src.substring(0, open + 1) + "\n" + closeIndent + "  " + quoted + "\n" + closeIndent + src.substring(close)
        }
        val comma = if (src[last] == ',') "" else ","
        val itemsMultiline = src.substring(open, close).contains('\n')
        val insert = if (itemsMultiline) {
            val indent = lineIndent(src, last)
            "$comma\n$indent$quoted"
        } else {
            "$comma $quoted"
        }
        return src.substring(0, last + 1) + insert + src.substring(last + 1)
    }

    /** Adds `"key": [value]` as the object's first entry. */
    private fun insertKey(src: String, key: String, quoted: String): String {
        val brace = nextCode(src, 0)?.takeIf { src[it] == '{' } ?: return src
        val first = nextCode(src, brace + 1)
        val entry = "${Json.quote(key)}: [$quoted]"
        if (first == null || src[first] == '}') {
            val close = first ?: return src
            return src.substring(0, brace + 1) + "\n  " + entry + "\n" + src.substring(close)
        }
        val braceLineEnd = src.indexOf('\n', brace)
        return if (braceLineEnd in (brace + 1) until first) {
            // On its own line straight after the brace, so it doesn't come between the first
            // key and a comment above it.
            val indent = lineIndent(src, first)
            src.substring(0, braceLineEnd + 1) + indent + entry + ",\n" + src.substring(braceLineEnd + 1)
        } else {
            src.substring(0, first) + entry + ", " + src.substring(first)
        }
    }

    private fun lineIndent(src: String, at: Int): String {
        val start = src.lastIndexOf('\n', at - 1) + 1
        return src.substring(start).takeWhile { it == ' ' || it == '\t' }
    }

    /** The position just after the top-level key [key] (its closing quote), with its start. */
    private fun findKey(src: String, key: String): Pair<Int, Int>? {
        var depth = 0
        var i = 0
        while (i < src.length) {
            when {
                src.startsWith("//", i) -> i = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                src.startsWith("/*", i) -> i = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                src[i] == '"' -> {
                    val end = stringEnd(src, i)
                    if (depth == 1 && src.substring(i + 1, end) == key) {
                        val colon = nextCode(src, end + 1)
                        if (colon != null && src[colon] == ':') return i to end + 1
                    }
                    i = end + 1
                }
                src[i] == '{' || src[i] == '[' -> { depth++; i++ }
                src[i] == '}' || src[i] == ']' -> { depth--; i++ }
                else -> i++
            }
        }
        return null
    }

    /** The index of the closing quote of the string starting at [start]. */
    private fun stringEnd(src: String, start: Int): Int {
        var i = start + 1
        while (i < src.length) {
            when (src[i]) {
                '\\' -> i += 2
                '"' -> return i
                else -> i++
            }
        }
        return src.length - 1
    }

    /** The next character from [from] that isn't space or a comment. */
    private fun nextCode(src: String, from: Int): Int? {
        var i = from
        while (i < src.length) {
            when {
                src[i].isWhitespace() -> i++
                src.startsWith("//", i) -> i = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                src.startsWith("/*", i) -> i = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                else -> return i
            }
        }
        return null
    }

    /** The last character in [from] until [until] that isn't space or a comment. */
    private fun lastCode(src: String, from: Int, until: Int): Int? {
        var last: Int? = null
        var i = from
        while (i < until) {
            when {
                src[i].isWhitespace() -> i++
                src.startsWith("//", i) -> i = src.indexOf('\n', i).let { if (it < 0) until else it }
                src.startsWith("/*", i) -> i = src.indexOf("*/", i + 2).let { if (it < 0) until else it + 2 }
                src[i] == '"' -> { val e = stringEnd(src, i); last = e; i = e + 1 }
                else -> { last = i; i++ }
            }
        }
        return last
    }

    /** The bracket closing the one at [open]. */
    private fun matching(src: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < src.length) {
            when {
                src.startsWith("//", i) -> i = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                src.startsWith("/*", i) -> i = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                src[i] == '"' -> i = stringEnd(src, i) + 1
                src[i] == '[' || src[i] == '{' -> { depth++; i++ }
                src[i] == ']' || src[i] == '}' -> { depth--; if (depth == 0) return i; i++ }
                else -> i++
            }
        }
        return null
    }
}
