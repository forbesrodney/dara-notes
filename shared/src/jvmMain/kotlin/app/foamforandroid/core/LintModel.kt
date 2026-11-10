package app.foamforandroid.core

import org.json.JSONObject
import java.io.File

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

data class LintResult(val issues: List<LintIssue>, val configFile: String?, val configError: String?)

/** markdownlint config found in the repository, in the order the VS Code extension uses. */
data class LintConfig(val file: String?, val raw: String, val format: String, val kind: String) {
    fun toJson(): String = JSONObject()
        .put("raw", raw).put("format", format).put("kind", kind).toString()

    companion object {
        private val candidates = listOf(
            Triple(".markdownlint-cli2.jsonc", "json", "cli2"),
            Triple(".markdownlint-cli2.yaml", "yaml", "cli2"),
            Triple(".markdownlint.jsonc", "json", "markdownlint"),
            Triple(".markdownlint.json", "json", "markdownlint"),
            Triple(".markdownlint.yaml", "yaml", "markdownlint"),
            Triple(".markdownlint.yml", "yaml", "markdownlint"),
        )

        fun find(root: File): LintConfig {
            for ((name, format, kind) in candidates) {
                val f = File(root, name)
                if (f.isFile) return LintConfig(name, f.readText(), format, kind)
            }
            val vscode = File(root, ".vscode/settings.json")
            if (vscode.isFile && vscode.readText().contains("\"markdownlint.config\"")) {
                return LintConfig(".vscode/settings.json", vscode.readText(), "json", "vscode")
            }
            return LintConfig(null, "", "json", "markdownlint")
        }
    }
}
