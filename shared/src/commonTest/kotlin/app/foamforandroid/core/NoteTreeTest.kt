package app.foamforandroid.core

import kotlin.test.assertEquals
import kotlin.test.Test

class NoteTreeTest {
    private fun note(path: String, title: String = path.substringAfterLast('/').removeSuffix(".md")) =
        Note(path, title, 0)

    @Test fun groupsByFolderSortedWithCounts() {
        val root = NoteTree.build(
            listOf(
                note("Index.md"), note("b.md", "Beta"), note("a.md", "alpha"),
                note("journal/2026/2026-11-09.md"), note("journal/2026/2026-11-08.md"),
                note("Projects/app.md"), note("journal/readme.md"),
            ),
        )
        assertEquals(listOf("alpha", "Beta", "Index"), root.notes.map { it.title })
        assertEquals(listOf("journal", "Projects"), root.folders.map { it.name })
        assertEquals(7, root.noteCount)

        val journal = root.folders[0]
        assertEquals("journal", journal.path)
        assertEquals(3, journal.noteCount)
        assertEquals(listOf("readme"), journal.notes.map { it.title })
        assertEquals("journal/2026", journal.folders.single().path)
        assertEquals(listOf("2026-11-08", "2026-11-09"), journal.folders.single().notes.map { it.title })
    }

    @Test fun emptyListGivesEmptyRoot() {
        val root = NoteTree.build(emptyList())
        assertEquals(0, root.noteCount)
        assertEquals(emptyList<FolderNode>(), root.folders)
    }
}
