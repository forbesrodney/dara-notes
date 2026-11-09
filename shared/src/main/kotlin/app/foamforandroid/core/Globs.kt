package app.foamforandroid.core

/**
 * VS Code-style glob matching for repository paths ('/' separators), as used in Foam settings:
 * `*` matches within one folder, `**` across folders, `?` one character, `{a,b}` either.
 */
object Globs {
    private val cache = HashMap<String, Regex>()

    fun matches(glob: String, path: String): Boolean {
        val g = glob.trim().removePrefix("./").trim('/')
        if (g.isEmpty()) return false
        val re = synchronized(cache) { cache.getOrPut(g) { toRegex(g) } }
        return re.matches(path)
    }

    internal fun toRegex(glob: String): Regex {
        val sb = StringBuilder()
        var i = 0
        var braces = 0
        while (i < glob.length) {
            val c = glob[i]
            when {
                glob.startsWith("**/", i) -> { sb.append("(?:.*/)?"); i += 3; continue }
                glob.startsWith("**", i) -> { sb.append(".*"); i += 2; continue }
                c == '*' -> sb.append("[^/]*")
                c == '?' -> sb.append("[^/]")
                c == '{' -> { sb.append("(?:"); braces++ }
                c == '}' && braces > 0 -> { sb.append(")"); braces-- }
                c == ',' && braces > 0 -> sb.append("|")
                else -> sb.append(Regex.escape(c.toString()))
            }
            i++
        }
        // A folder pattern ("journal" or "journal/") also covers everything inside it.
        if (!glob.contains('*') && !glob.contains('?')) sb.append("(?:/.*)?")
        return Regex(sb.toString())
    }
}
