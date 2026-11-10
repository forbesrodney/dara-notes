package app.foamforandroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import okio.Path

// Desktop versions. The preview and graph need an embedded browser, which the desktop app
// adds later; until then they show a notice.

@Composable
private fun NotAvailable(what: String, modifier: Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text("$what isn't available in the desktop app yet.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
actual fun MarkdownPreview(
    markdown: String,
    notePath: String,
    repoRoot: Path,
    callbacks: PreviewCallbacks,
    modifier: Modifier,
) = NotAvailable("The preview", modifier)

@Composable
actual fun GraphWebView(
    page: GraphPage,
    background: Color,
    onSelected: (String) -> Unit,
    onLayout: (String) -> Unit,
    modifier: Modifier,
) = NotAvailable("The graph", modifier)

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme() else lightColorScheme()
