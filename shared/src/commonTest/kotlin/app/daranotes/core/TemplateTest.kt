package app.daranotes.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class TemplateTest {
    @Test fun dropsBlankLinesLeftByTheFoamTemplateBlock() {
        val t = Template.parse("---\nfoam_template:\n  filepath: '/dev-notes/\$FOAM_SLUG.md'\n---\n\n# \$FOAM_TITLE\n")
        assertEquals("/dev-notes/\$FOAM_SLUG.md", t.filepath)
        assertEquals("# \$FOAM_TITLE\n", t.body)
    }

    @Test fun keepsOtherFrontMatterAndItsSpacing() {
        val t = Template.parse("---\nfoam_template:\n  filepath: x.md\ntags: daily\n---\n\n# Title\n")
        assertEquals("---\ntags: daily\n---\n\n# Title\n", t.body)
    }

    private fun resolve(text: String, now: LocalDateTime, current: LocalDateTime = now, zone: TimeZone = TimeZone.UTC) =
        FoamVariables.resolve(text, "T", now, current = current, zone = zone)

    @Test fun numbersTheWeekdayFromMonday() {
        assertEquals("1", resolve("\$FOAM_DATE_DAY_ISO", LocalDateTime(2026, 11, 16, 9, 0))) // Monday
        assertEquals("5", resolve("\$FOAM_DATE_DAY_ISO", LocalDateTime(2026, 11, 13, 9, 0))) // Friday
        assertEquals("7", resolve("\${FOAM_DATE_DAY_ISO}", LocalDateTime(2026, 11, 15, 9, 0))) // Sunday
    }

    @Test fun givesTheYearTheIsoWeekBelongsTo() {
        val week = "\$FOAM_DATE_WEEK_YEAR-W\$FOAM_DATE_WEEK"
        assertEquals("2026-W46", resolve(week, LocalDateTime(2026, 11, 13, 9, 0)))
        // New Year's Day 2027 is a Friday, still in 2026's last week...
        assertEquals("2026-W53", resolve(week, LocalDateTime(2027, 1, 1, 9, 0)))
        // ...and 2024-12-30, a Monday, starts 2025's first.
        assertEquals("2025-W01", resolve(week, LocalDateTime(2024, 12, 30, 9, 0)))
    }

    @Test fun countsUnixSecondsInTheGivenTimeZone() {
        val epoch = LocalDateTime(1970, 1, 1, 0, 0)
        assertEquals("0", resolve("\$FOAM_DATE_SECONDS_UNIX", epoch))
        val india = UtcOffset(hours = 5, minutes = 30).asTimeZone()
        assertEquals("-19800", resolve("\$FOAM_DATE_SECONDS_UNIX", epoch, zone = india))
    }

    @Test fun resolvesVsCodeDateVariablesForTheTimeTheNoteIsMade() {
        // A daily note for another day: FOAM_DATE_* are that day, CURRENT_* are now.
        val day = LocalDateTime(2026, 11, 2, 9, 0)
        val now = LocalDateTime(2027, 3, 4, 5, 6, 7)
        val text = "\$CURRENT_YEAR \$CURRENT_YEAR_SHORT \$CURRENT_MONTH \$CURRENT_DATE " +
            "\$CURRENT_HOUR:\$CURRENT_MINUTE:\${CURRENT_SECOND} \$CURRENT_SECONDS_UNIX \$FOAM_DATE_DATE"
        assertEquals("2027 27 03 04 05:06:07 1804136767 02", resolve(text, day, now))
        assertEquals(
            listOf(
                PlatformDates.monthName(3, short = false), PlatformDates.monthName(3, short = true),
                PlatformDates.dayName(4, short = false), PlatformDates.dayName(4, short = true), // Thursday
            ).joinToString("|"),
            resolve("\$CURRENT_MONTH_NAME|\$CURRENT_MONTH_NAME_SHORT|\$CURRENT_DAY_NAME|\$CURRENT_DAY_NAME_SHORT", day, now),
        )
    }

    @Test fun writesTheTimeZoneOffsetAsVsCodeDoes() {
        val now = LocalDateTime(2026, 11, 13, 9, 0)
        assertEquals("+00:00", resolve("\$CURRENT_TIMEZONE_OFFSET", now))
        assertEquals("+05:30", resolve("\$CURRENT_TIMEZONE_OFFSET", now, zone = UtcOffset(hours = 5, minutes = 30).asTimeZone()))
        assertEquals("-03:30", resolve("\$CURRENT_TIMEZONE_OFFSET", now, zone = UtcOffset(hours = -3, minutes = -30).asTimeZone()))
    }

    @Test fun namesTheNotesFileWithoutItsExtension() {
        assertEquals("# shopping-list\n", FoamVariables.resolve("# \$TM_FILENAME_BASE\n", "Shopping List", path = "lists/shopping-list.md"))
        // Without a file yet (in a template's filepath), it stays, or falls back to its default.
        assertEquals("\$TM_FILENAME_BASE/x", FoamVariables.resolve("\$TM_FILENAME_BASE/x", "T"))
        assertEquals("note", FoamVariables.resolve("\${TM_FILENAME_BASE:note}", "T"))
    }

    @Test fun makesANewUuidAndRandomNumberEachTime() {
        val uuids = FoamVariables.resolve("\$UUID \${UUID}", "T").split(' ')
        assertEquals(2, uuids.size)
        uuids.forEach { assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(it), it) }
        assertNotEquals(uuids[0], uuids[1])
        val random = FoamVariables.resolve("\$RANDOM \$RANDOM_HEX", "T").split(' ')
        assertTrue(Regex("[0-9]{6}").matches(random[0]), random[0])
        assertTrue(Regex("[0-9a-f]{6}").matches(random[1]), random[1])
    }

    @Test fun fillsInEachVariableOnce() {
        // A title that looks like a variable or a tab stop is kept as typed.
        assertEquals("# \$FOAM_SLUG costs \$1\n", FoamVariables.resolve("# \$FOAM_TITLE\n", "\$FOAM_SLUG costs \$1"))
        assertEquals("Hello  world", FoamVariables.resolve("\${1:Hello} \$0 \${2}world", "T"))
    }
}
