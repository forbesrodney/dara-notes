package app.daranotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.daranotes.core.LintIssue
import app.daranotes.core.LintResult
import app.daranotes.core.SpellIssue

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

/**
 * Marks the text markdownlint complained about with a background, and misspelled words with
 * an underline (a different shape, not just a different color).
 */
data class LintHighlight(
    val issues: List<LintIssue>,
    val color: Color,
    val spelling: List<SpellIssue> = emptyList(),
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (issues.isEmpty() && spelling.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val s = text.text
        val b = AnnotatedString.Builder(text)
        for (w in spelling) {
            // Checked a moment ago: skip any the latest typing has moved.
            if (w.end <= s.length && s.regionMatches(w.start, w.word, 0, w.word.length)) {
                b.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), w.start, w.end)
            }
        }
        for (issue in issues) {
            val r = issueRange(s, issue)
            // Empty lines have nothing to color; mark their line break instead.
            val end = if (r.collapsed) (r.start + 1).coerceAtMost(s.length) else r.end
            if (end > r.start) b.addStyle(SpanStyle(background = color), r.start, end)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/** The chip under the toolbar with the note's markdownlint issues; tap for the list. */
@Composable
fun LintChip(result: LintResult, onClick: () -> Unit) {
    val n = result.issues.size
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

@Composable
fun LintIssuesDialog(
    result: LintResult,
    onDismiss: () -> Unit,
    onJump: (LintIssue) -> Unit,
    onFixAll: () -> Unit,
    onIgnore: (LintIssue) -> Unit,
    onTurnOff: (LintIssue) -> Unit,
    onDisable: () -> Unit,
) {
    val fixable = result.issues.count { it.fixable }
    // Where Turn off writes: the config the app reads, or a new .markdownlint.json.
    val configName = result.configFile ?: ".markdownlint.json"
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
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable { onJump(issue) }.padding(vertical = 8.dp),
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
                        IssueMenu(issue.rule.substringBefore('/'), configName, { onIgnore(issue) }, { onTurnOff(issue) })
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        // As in the spelling list: Disable at the left, apart from Close. Fix all goes on a line
        // of its own above Close: all three don't fit across a phone.
        confirmButton = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                if (fixable > 0) TextButton(onClick = onFixAll) { Text("Fix all ($fixable)") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDisable) { Text("Disable in current note") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        },
    )
}

/** An issue's menu: turn its rule off in this note, or for the whole repository. */
@Composable
private fun IssueMenu(rule: String, configName: String, onIgnore: () -> Unit, onTurnOff: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Tip("Ignore or turn off $rule") {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options for $rule")
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Ignore $rule in this note") }, onClick = { open = false; onIgnore() })
            DropdownMenuItem(text = { Text("Turn off $rule in $configName") }, onClick = { open = false; onTurnOff() })
        }
    }
}
