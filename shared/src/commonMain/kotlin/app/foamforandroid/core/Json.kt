package app.foamforandroid.core

/** Minimal JSON writing for the few places that hand data to JavaScript. */
object Json {
    /** [s] as a JSON string literal, quotes included. */
    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                // U+2028/2029 are valid in JSON but end a line in older JavaScript.
                c < ' ' || c == '\u2028' || c == '\u2029' ->
                    sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
