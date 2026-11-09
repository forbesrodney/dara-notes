package app.foamforandroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.foamforandroid.core.LintIssue
import app.foamforandroid.core.LintResult

private fun lineStart(text: String, line: Int): Int? {
    var start = 0
    repeat(line - 1) {
        val nl = text.indexOf('\n', start)
        if (nl < 0) return null
        start = nl + 1
    }
    return start
}

/** The text range an issue points at: its column range, else the whole line. */
fun issueRange(text: String, issue: LintIssue): TextRange {
    val start = lineStart(text, issue.line) ?: return TextRange(text.length)
    val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
    if (issue.column > 0 && issue.length > 0) {
        val a = (start + issue.column - 1).coerceIn(start, end)
        return TextRange(a, (a + issue.length).coerceIn(a, end))
    }
    return TextRange(start, end)
}

/** Highlights the text markdownlint complained about. */
data class LintHighlight(val issues: List<LintIssue>, val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (issues.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val s = text.text
        val b = AnnotatedString.Builder(text)
        for (issue in issues) {
            val r = issueRange(s, issue)
            // Empty lines have nothing to colour; mark their line break instead.
            val end = if (r.collapsed) (r.start + 1).coerceAtMost(s.length) else r.end
            if (end > r.start) b.addStyle(SpanStyle(background = color), r.start, end)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun LintBar(result: LintResult, onClick: () -> Unit) {
    val n = result.issues.size
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = onClick,
            label = {
                Text(
                    when {
                        result.configError != null && n == 0 -> "markdownlint config error"
                        n == 1 -> "1 markdownlint issue"
                        else -> "$n markdownlint issues"
                    },
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Warning, null, Modifier.padding(0.dp), tint = MaterialTheme.colorScheme.error)
            },
            colors = AssistChipDefaults.assistChipColors(),
        )
    }
}

@Composable
fun LintIssuesDialog(
    result: LintResult,
    onDismiss: () -> Unit,
    onJump: (LintIssue) -> Unit,
    onFixAll: () -> Unit,
) {
    val fixable = result.issues.count { it.fixable }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("markdownlint") },
        text = {
            Column {
                Text(
                    result.configFile?.let { "Config: $it" } ?: "No config file found; default rules",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                result.configError?.let {
                    Text(
                        "Couldn't read the config: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(result.issues) { issue ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onJump(issue) }.padding(vertical = 8.dp),
                        ) {
                            Text(
                                "Line ${issue.line} · ${issue.rule}" + if (issue.fixable) " · fixable" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(issue.description, style = MaterialTheme.typography.bodyMedium)
                            val extra = listOf(issue.detail, issue.context).filter { it.isNotBlank() }
                            if (extra.isNotEmpty()) {
                                Text(
                                    extra.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            if (fixable > 0) TextButton(onClick = onFixAll) { Text("Fix all ($fixable)") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
