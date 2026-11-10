package app.foamforandroid.core

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
