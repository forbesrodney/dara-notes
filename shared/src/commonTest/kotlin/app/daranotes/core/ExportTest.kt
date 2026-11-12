package app.daranotes.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ExportTest {
    @Test fun plainTextDropsLinksThatCantLeadAnywhere() {
        val note = """
            ---
            tags: [a]
            ---
            # Plan

            - [Goals](#goals)
            - [Next steps](#next-steps)

            ## Goals <!-- omit from toc -->

            See [[Shopping]], [[Work/Plan|the plan]] and [the site](https://example.com).

            ```
            [[kept]] [x](#kept)
            ```

            [Shopping]: Shopping.md "Shopping"
            [Work/Plan|the plan]: Work/Plan.md "The Plan"

        """.trimIndent()
        assertEquals(
            """
            # Plan

            - Goals
            - Next steps

            ## Goals

            See Shopping, the plan and [the site](https://example.com).

            ```
            [[kept]] [x](#kept)
            ```

            """.trimIndent(),
            Export.plainText(note),
        )
    }

    @Test fun fileNames() {
        assertEquals("Shopping.pdf", Export.fileName("Lists/Shopping.md", "pdf"))
        assertEquals("Shopping.md", Export.fileName("Shopping.md", "md"))
    }
}
