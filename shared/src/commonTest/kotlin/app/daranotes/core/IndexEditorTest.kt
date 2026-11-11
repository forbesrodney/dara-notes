package app.daranotes.core

import kotlin.test.assertEquals
import kotlin.test.Test

class IndexEditorTest {
    /** Shape of a real Foam index: two lists, then Foam's link definitions. */
    private val foamIndex = """
        <!-- cspell:disable -->
        # Foam Wiki

        * [[TODO]]
        * [[Shopping]]

        You can put random notes here, as well.

        * [[Git Commands]]
        * [[Let's Encrypt]]

        [Git Commands]: <Git Commands.md> "Git Notes"
        [Let's Encrypt]: <Let's Encrypt.md> "Let's Encrypt"
        [TODO]: <TODO.md> "TODO"
        [Shopping]: Shopping.md "Shopping"

    """.trimIndent()

    @Test fun addsAfterLastListItemAndAppendsDefinition() {
        val def = IndexEditor.definition("Tea & Biscuits", "Tea & Biscuits.md", "Tea & Biscuits")
        val out = IndexEditor.add(foamIndex, "Tea & Biscuits", "-", def)
        assertEquals(
            """
            <!-- cspell:disable -->
            # Foam Wiki

            * [[TODO]]
            * [[Shopping]]

            You can put random notes here, as well.

            * [[Git Commands]]
            * [[Let's Encrypt]]
            * [[Tea & Biscuits]]

            [Git Commands]: <Git Commands.md> "Git Notes"
            [Let's Encrypt]: <Let's Encrypt.md> "Let's Encrypt"
            [TODO]: <TODO.md> "TODO"
            [Shopping]: Shopping.md "Shopping"
            [Tea & Biscuits]: <Tea %26 Biscuits.md> "Tea & Biscuits"

            """.trimIndent(),
            out,
        )
    }

    @Test fun noDefinitionsBlockMeansNoDefinition() {
        val out = IndexEditor.add("# Index\n\n- [[a]]\n", "b", "*", "[b]: b.md \"B\"")
        assertEquals("# Index\n\n- [[a]]\n- [[b]]\n", out)
    }

    @Test fun emptyListGoesAboveDefinitions() {
        val out = IndexEditor.add("# Index\n\n[a]: a.md \"A\"\n", "b", "-", "[b]: b.md \"B\"")
        assertEquals("# Index\n\n- [[b]]\n\n[a]: a.md \"A\"\n[b]: b.md \"B\"\n", out)
    }

    @Test fun plainIndexAppends() {
        assertEquals("# Index\n\n- [[b]]\n", IndexEditor.add("# Index\n\n", "b", "-", null))
    }

    @Test fun removeDropsItemAndDefinition() {
        val out = IndexEditor.remove(foamIndex, listOf("Git Commands"))
        assertEquals(false, out.contains("Git Commands"))
        assertEquals(foamIndex.lines().size - 2, out.lines().size)
    }
}
