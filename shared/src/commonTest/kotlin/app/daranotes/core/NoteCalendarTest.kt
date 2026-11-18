package app.daranotes.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteCalendarTest {
    // 2026-11-10 12:00 and 2026-11-12 09:00 UTC.
    private val nov10 = 1_794_312_000_000L
    private val nov12 = 1_794_474_000_000L

    @Test
    fun dailyNotesAreDatedByName() {
        assertEquals(LocalDate(2026, 11, 3), NoteCalendar.dailyDate(Note("journal/2026-11-03.md", "x", 0)))
        assertEquals(LocalDate(2026, 11, 3), NoteCalendar.dailyDate(Note("2026-11-03.md", "x", 0)))
        assertNull(NoteCalendar.dailyDate(Note("2026-13-40.md", "x", 0)))
        assertNull(NoteCalendar.dailyDate(Note("notes/meeting 2026-11-03.md", "x", 0)))
    }

    @Test
    fun notesGoOnTheirDailyDateOrTheirLastCommit() {
        val daily = Note("journal/2026-11-03.md", "Nov 3", modified = nov12)
        val committed = Note("ideas.md", "Ideas", modified = nov12) // written by a pull on the 12th
        val edited = Note("draft.md", "Draft", modified = nov12) // edited, not yet committed
        val newNote = Note("new.md", "New", modified = nov10) // never committed
        val times = EditTimes(
            committed = mapOf("ideas.md" to nov10, "draft.md" to nov10, "journal/2026-11-03.md" to nov10),
            uncommitted = setOf("draft.md", "new.md"),
        )
        val days = NoteCalendar.days(listOf(daily, committed, edited, newNote), times, TimeZone.UTC)
        assertEquals(
            mapOf(
                LocalDate(2026, 11, 3) to CalendarDay(listOf(daily), emptyList()),
                LocalDate(2026, 11, 10) to CalendarDay(emptyList(), listOf(committed, newNote)),
                LocalDate(2026, 11, 12) to CalendarDay(emptyList(), listOf(edited)),
            ),
            days,
        )
    }

    @Test
    fun monthGridHasWholeWeeks() {
        // November 2026 starts on a Sunday and has 30 days.
        val sundayFirst = NoteCalendar.monthGrid(LocalDate(2026, 11, 1), weekStart = 7)
        assertEquals(5, sundayFirst.size)
        assertEquals(LocalDate(2026, 11, 1), sundayFirst[0][0])
        assertEquals(listOf(LocalDate(2026, 11, 29), LocalDate(2026, 11, 30), null, null, null, null, null), sundayFirst[4])

        val mondayFirst = NoteCalendar.monthGrid(LocalDate(2026, 11, 1), weekStart = 1)
        assertEquals(6, mondayFirst.size)
        assertEquals(listOf(null, null, null, null, null, null, LocalDate(2026, 11, 1)), mondayFirst[0])
        assertEquals(LocalDate(2026, 11, 30), mondayFirst[5][0])
    }
}
