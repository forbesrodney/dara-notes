package app.daranotes.core

import okio.Path.Companion.toOkioPath
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Date
import java.util.TimeZone

class JGitEditTimesTest {
    private val dir: File = Files.createTempDirectory("dara-edit-times").toFile()

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun write(name: String, text: String) = File(dir, name).apply { parentFile.mkdirs() }.writeText(text)

    private fun commit(git: Git, at: Long, message: String) {
        val who = PersonIdent("Test", "test@example.com", Date(at), TimeZone.getTimeZone("UTC"))
        git.add().addFilepattern(".").call()
        git.commit().setMessage(message).setAuthor(who).setCommitter(who).setSign(false).call()
    }

    @Test fun newestCommitPerFileAndUncommittedFiles() {
        assertEquals(EditTimes.NONE, JGitEngine.editTimes(dir.toOkioPath()))
        val day1 = 1_794_312_000_000L
        val day3 = day1 + 2 * 86_400_000L
        Git.init().setDirectory(dir).setInitialBranch("main").call().use { git ->
            write("one.md", "one\n")
            write("sub/two.md", "two\n")
            commit(git, day1, "first")
            write("one.md", "one, edited\n")
            commit(git, day3, "second")
        }
        // Not yet committed: an edit and a new note.
        write("sub/two.md", "two, edited\n")
        write("three.md", "three\n")

        val times = JGitEngine.editTimes(dir.toOkioPath())
        assertEquals(mapOf("one.md" to day3, "sub/two.md" to day1), times.committed)
        assertEquals(setOf("sub/two.md", "three.md"), times.uncommitted)
        assertEquals(42L, times.of("three.md", fileTime = 42L))
        assertEquals(day3, times.of("one.md", fileTime = 42L))
    }
}
