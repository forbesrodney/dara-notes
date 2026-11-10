package app.foamforandroid.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EncodingTest {
    @Test fun percentDecodesUtf8AndKeepsPlus() {
        assertEquals("shopping list.md", RepoPaths.percentDecode("shopping%20list.md"))
        assertEquals("café+tea", RepoPaths.percentDecode("caf%C3%A9+tea"))
        assertEquals("plain", RepoPaths.percentDecode("plain"))
    }

    @Test fun malformedEscapeIsNull() {
        assertNull(RepoPaths.percentDecode("50%"))
        assertNull(RepoPaths.percentDecode("a%zzb"))
    }

    @Test fun jsonQuoteEscapes() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", Json.quote("a\"b\\c\nd\u0001"))
    }
}

class LintResultTest {
    @Test fun parsesLintOutput() {
        val config = LintConfig(".markdownlint.json", "{}", "json", "markdownlint")
        val r = LintResult.parse(
            """{"issues":[{"line":3,"rule":"MD009/no-trailing-spaces","description":"Trailing spaces","detail":"","context":"","column":5,"length":2,"fixable":true},""" +
                """{"line":1,"rule":"MD041","description":"First line","fixable":false}],"configError":null}""",
            config,
        )
        assertEquals(listOf(1, 3), r.issues.map { it.line })
        assertEquals(true, r.issues[1].fixable)
        assertEquals(".markdownlint.json", r.configFile)
        assertNull(r.configError)
    }
}
