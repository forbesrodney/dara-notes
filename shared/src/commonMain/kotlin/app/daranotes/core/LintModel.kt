package app.daranotes.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import kotlinx.serialization.json.Json as JsonParser

data class LintIssue(
    /** 1-based line number. */
    val line: Int,
    /** e.g. "MD009/no-trailing-spaces". */
    val rule: String,
    val description: String,
    val detail: String,
    val context: String,
    /** 1-based column of the problem within the line, or 0 for the whole line. */
    val column: Int,
    val length: Int,
    val fixable: Boolean,
)

data class LintResult(val issues: List<LintIssue>, val configFile: String?, val configError: String?) {
    companion object {
        /** Reads lint.html's `lintNote` output: `{"issues": [...], "configError": ...}`. */
        fun parse(json: String, config: LintConfig): LintResult {
            val obj = JsonParser.parseToJsonElement(json).jsonObject
            fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
            fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull ?: 0
            val issues = (obj["issues"] as? JsonArray).orEmpty().map { it.jsonObject }.map { e ->
                LintIssue(
                    line = e.int("line"),
                    rule = e.str("rule") ?: "",
                    description = e.str("description") ?: "",
                    detail = e.str("detail") ?: "",
                    context = e.str("context") ?: "",
                    column = e.int("column"),
                    length = e.int("length"),
                    fixable = (e["fixable"] as? JsonPrimitive)?.booleanOrNull ?: false,
                )
            }
            return LintResult(
                issues.sortedWith(compareBy({ it.line }, { it.column })),
                config.file,
                obj.str("configError")?.takeIf { it.isNotEmpty() && it != "null" },
            )
        }
    }
}

/** markdownlint config found in the repository, in the order the VS Code extension uses. */
data class LintConfig(val file: String?, val raw: String, val format: String, val kind: String) {
    fun toJson(): String = buildJsonObject {
        put("raw", raw)
        put("format", format)
        put("kind", kind)
    }.toString()

    companion object {
        private val candidates = listOf(
            Triple(".markdownlint-cli2.jsonc", "json", "cli2"),
            Triple(".markdownlint-cli2.yaml", "yaml", "cli2"),
            Triple(".markdownlint.jsonc", "json", "markdownlint"),
            Triple(".markdownlint.json", "json", "markdownlint"),
            Triple(".markdownlint.yaml", "yaml", "markdownlint"),
            Triple(".markdownlint.yml", "yaml", "markdownlint"),
        )

        fun find(fs: FileSystem, root: Path): LintConfig {
            fun text(name: String): String? = (root / name).takeIf { fs.metadataOrNull(it)?.isRegularFile == true }
                ?.let { runCatching { fs.read(it) { readUtf8() } }.getOrNull() }
            for ((name, format, kind) in candidates) {
                text(name)?.let { return LintConfig(name, it, format, kind) }
            }
            val vscode = text(".vscode/settings.json")
            if (vscode != null && vscode.contains("\"markdownlint.config\"")) {
                return LintConfig(".vscode/settings.json", vscode, "json", "vscode")
            }
            return LintConfig(null, "", "json", "markdownlint")
        }
    }
}

/**
 * markdownlint's own comments in a note, which it (and VS Code's extension) follows:
 * `<!-- markdownlint-disable-file -->` turns it off for the note, and
 * `<!-- markdownlint-disable-file MD013 MD033 -->` turns off just those rules. Added at the
 * top, below any front matter (above it, the comment would trip MD041).
 */
object LintComments {
    private val DISABLE_FILE = Regex("""^(\s*<!--\s*markdownlint-disable-file)((?:\s+[^\s>-][^\s>]*)*)(\s*-->\s*)$""")

    /** [text] with [rule] (such as `MD013`) turned off in it, added to an existing comment if any. */
    fun ignoreInNote(text: String, rule: String): String = edit(text) { lines ->
        val i = lines.indexOfFirst { DISABLE_FILE.matches(it) }
        if (i < 0) {
            lines.add(SpellComments.topLine(lines), "<!-- markdownlint-disable-file $rule -->")
            return@edit
        }
        val m = DISABLE_FILE.find(lines[i])!!
        val rules = m.groupValues[2].trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // No rules listed: the whole note is already off.
        if (rules.isEmpty() || rules.any { it.equals(rule, ignoreCase = true) }) return@edit
        lines[i] = m.groupValues[1] + m.groupValues[2] + " " + rule + m.groupValues[3]
    }

    /** [text] with markdownlint off for the whole note; a comment listing rules becomes this one. */
    fun disableInNote(text: String): String = edit(text) { lines ->
        val i = lines.indexOfFirst { DISABLE_FILE.matches(it) }
        if (i < 0) {
            lines.add(SpellComments.topLine(lines), "<!-- markdownlint-disable-file -->")
            return@edit
        }
        val m = DISABLE_FILE.find(lines[i])!!
        lines[i] = m.groupValues[1] + m.groupValues[3]
    }

    private fun edit(text: String, change: (MutableList<String>) -> Unit): String {
        val crlf = "\r\n" in text
        val lines = text.replace("\r\n", "\n").split("\n").toMutableList()
        change(lines)
        val out = lines.joinToString("\n")
        val result = if (crlf) out.replace("\n", "\r\n") else out
        return if (result.replace("\r\n", "\n") == text.replace("\r\n", "\n")) text else result
    }
}

/**
 * Turns a markdownlint rule off for the whole repository, in the config file the app reads
 * (see [LintConfig.find]), changing only that rule's entry so the rest of the file stays as
 * it was. Without a config file, `.markdownlint.json` is created.
 */
object LintRuleOff {
    /**
     * Sets [rule] (as markdownlint reports it, `MD013/line-length`) to false. An existing entry
     * under either name is changed; otherwise the rule's number is added. Returns the file
     * changed; throws with a message for the user if the file can't safely be edited.
     */
    fun apply(fs: FileSystem, root: Path, rule: String): String {
        val names = rule.split('/').filter { it.isNotBlank() }
        require(names.isNotEmpty()) { "no rule" }
        val config = LintConfig.find(fs, root)
        val file = config.file ?: ".markdownlint.json"
        val old = config.file?.let { config.raw }
        val new = when {
            old == null -> "{\n  ${Json.quote(names.first())}: false\n}\n"
            config.format == "yaml" && config.kind == "markdownlint" -> yaml(old, names)
            config.format == "yaml" -> throw IllegalStateException("Edit $file yourself: rules there are under config:")
            else -> Jsonc.setFalse(
                old,
                when (config.kind) { "cli2" -> listOf("config"); "vscode" -> listOf("markdownlint.config"); else -> emptyList() },
                names,
            ) ?: throw IllegalStateException("Couldn't edit $file; turn ${names.first()} off there yourself")
        }
        if (new != old) {
            val path = root / file
            path.parent?.let(fs::createDirectories)
            fs.write(path) { writeUtf8(new) }
        }
        return file
    }

    /** A .markdownlint.yaml: a one-line entry for the rule becomes `false`, or one is added. */
    private fun yaml(text: String, names: List<String>): String {
        val crlf = "\r\n" in text
        val lines = text.replace("\r\n", "\n").split("\n").toMutableList()
        val key = Regex("""^(["']?)([A-Za-z0-9-]+)\1\s*:(.*)$""")
        for ((i, line) in lines.withIndex()) {
            val m = key.find(line) ?: continue
            if (names.none { it.equals(m.groupValues[2], ignoreCase = true) }) continue
            val rest = m.groupValues[3].substringBefore(" #").trim()
            // Settings on the lines below (line_length: 100, ...): too easy to get wrong here.
            if (rest.isEmpty() || rest.startsWith("{")) {
                throw IllegalStateException("Edit the ${m.groupValues[2]} entry in the config yourself: it has settings")
            }
            lines[i] = line.substring(0, line.indexOf(':') + 1) + " false"
            return join(lines, crlf)
        }
        if (lines.lastOrNull()?.isEmpty() == true) lines.add(lines.lastIndex, "${names.first()}: false")
        else lines.add("${names.first()}: false")
        return join(lines, crlf)
    }

    private fun join(lines: List<String>, crlf: Boolean): String =
        lines.joinToString("\n").let { if (crlf) it.replace("\n", "\r\n") else it }
}
