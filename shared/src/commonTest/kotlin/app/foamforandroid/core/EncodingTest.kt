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
