package app.daranotes.ui

import app.daranotes.core.Json
import app.daranotes.core.LintConfig
import app.daranotes.core.LintResult
import javafx.concurrent.Worker
import javafx.scene.web.WebEngine
import kotlinx.coroutines.CompletableDeferred

/** markdownlint on the desktop: lint.html in a hidden JavaFX web engine. */
object DesktopLinter {
    private var engine: WebEngine? = null
    private val loaded = CompletableDeferred<Unit>()

    private suspend fun ensure(): WebEngine {
        val e = Fx.call {
            engine ?: WebEngine().also { e ->
                e.loadWorker.stateProperty().addListener { _, _, state ->
                    when (state) {
                        Worker.State.SUCCEEDED -> loaded.complete(Unit)
                        Worker.State.FAILED -> loaded.completeExceptionally(
                            IllegalStateException("Couldn't load markdownlint", e.loadWorker.exception),
                        )
                        else -> Unit
                    }
                }
                e.load("${WebAssets.ORIGIN}/assets/lint.html")
                engine = e
            }
        }
        loaded.await()
        return e
    }

    private suspend fun eval(script: String): String {
        val e = ensure()
        return Fx.call { e.executeScript(script) as? String ?: error("markdownlint failed") }
    }

    suspend fun lint(text: String, config: LintConfig): LintResult =
        LintResult.parse(eval("lintNote(${Json.quote(text)}, ${config.toJson()})"), config)

    suspend fun fixAll(text: String, config: LintConfig): String =
        eval("fixNote(${Json.quote(text)}, ${config.toJson()})")
}
