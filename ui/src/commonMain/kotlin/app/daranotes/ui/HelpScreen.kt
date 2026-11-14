package app.daranotes.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import app.daranotes.MainViewModel
import app.daranotes.Screen
import app.daranotes.core.Toc

/**
 * The help pages: docs/ and PRIVACY.md from the app's source, built in by the
 * generateHelpPages Gradle task. Pages link to each other with ordinary relative Markdown
 * links (`[Templates](templates.md)`, `[Privacy](../PRIVACY.md)`), which work on GitHub too.
 */
object Help {
    const val INDEX = "index"

    /** The page's Markdown, by name ([INDEX], "templates", "privacy"...), if there is one. */
    fun page(name: String): String? = HelpPages.pages[name]

    /** Every page's name. */
    val names: Set<String> get() = HelpPages.pages.keys

    /**
     * The page a link points to: its file name, without folders, `.md` or a `#heading`, in
     * lowercase, if that's a help page. So `templates.md`, `docs/templates` and
     * `../PRIVACY.md#contact` all work.
     */
    fun pageFor(link: String): String? {
        val name = link.substringBefore('#').substringBefore('?').trimEnd('/')
            .substringAfterLast('/').removeSuffix(".md").lowercase()
        return name.takeIf { it in HelpPages.pages }
    }

    /** The page's title: its first `#` heading, else [fallback]. */
    fun title(markdown: String, fallback: String = "Help"): String =
        Toc.headings(markdown).firstOrNull { it.level == 1 }?.text ?: fallback

    /**
     * [markdown] with a table of contents (links to its `##` and lower headings) under its
     * title, unless it has one already or has too few sections to need one.
     */
    fun withToc(markdown: String): String {
        if (Toc.find(markdown) != null) return markdown
        if (Toc.headings(markdown).count { it.level >= 2 && !it.omit } < 2) return markdown
        val toc = Toc.build(markdown)
        val lines = markdown.split('\n')
        val title = Toc.headings(markdown).firstOrNull { it.level == 1 }?.line ?: return "$toc\n\n$markdown"
        val before = lines.take(title + 1).joinToString("\n")
        val after = lines.drop(title + 1).joinToString("\n").trimStart('\n')
        return "$before\n\n$toc\n\n$after"
    }
}

/** A help page in the preview. Links to other help pages open them here; Back returns. */
@Composable
fun HelpScreen(vm: MainViewModel, screen: Screen.Help, snackbar: SnackbarHostState) {
    val raw = Help.page(screen.page) ?: Help.page(Help.INDEX).orEmpty()
    val markdown = remember(raw) { Help.withToc(raw) }
    val callbacks = remember { PreviewCallbacks() }
    // Help pages aren't notes: no wikilinks to resolve, no notes to embed, no tasks to toggle.
    fun open(link: String) {
        val page = Help.pageFor(link) ?: return
        if (page != screen.page) vm.navigate(Screen.Help(page))
    }
    callbacks.openPath = ::open
    callbacks.openWikilink = ::open

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Tip("Back") {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = { Text(Help.title(raw), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (screen.page != Help.INDEX) {
                        Tip("Help contents") {
                            IconButton(onClick = { vm.navigate(Screen.Help(Help.INDEX)) }) {
                                Icon(HelpIcon, contentDescription = "Help contents")
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        MarkdownPreview(
            markdown = markdown,
            // Relative links resolve from docs/, as they do on GitHub.
            notePath = if (screen.page == "privacy") "PRIVACY.md" else "docs/${screen.page}.md",
            repoRoot = vm.repo.root,
            callbacks = callbacks,
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}

/** Material's "help_outline" icon: a question mark in a circle. */
val HelpIcon: ImageVector by lazy {
    menuIcon(
        "Help",
        "M11,18h2v-2h-2v2z" +
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
            "M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z" +
            "M12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z",
    )
}
