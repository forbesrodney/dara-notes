package app.daranotes.core

/** A misspelled word: its range in the text, and the word as written. */
data class SpellIssue(val start: Int, val end: Int, val word: String)

/**
 * The `cspell:` comments in a note, as cSpell reads them:
 *
 *     <!-- cspell:disable -->            stop checking (to the end, or a cspell:enable)
 *     <!-- cspell:enable -->
 *     <!-- cspell:disable-line -->       not this line
 *     <!-- cspell:disable-next-line -->  not the next line
 *     <!-- cspell:ignore word other -->  accept these words in this note
 *     <!-- cspell:words word other -->   the same
 *     <!-- cspell:flagWords word -->     always flag these in this note
 *
 * `spell-checker:` and `spellchecker:` work too, and case doesn't matter.
 */
class SpellComments(
    /** Lowercase. */
    val words: Set<String>,
    /** Lowercase. */
    val flagWords: Set<String>,
    /** Ranges of the text not to check: disabled parts, and the comments themselves. */
    val skipped: List<IntRange>,
) {
    companion object {
        private val DIRECTIVE = Regex("""(?i)\b(?:cspell|spell-?checker)\s*:\s*([a-z][a-z-]*)([^\n]*)""")

        fun parse(text: String): SpellComments {
            val words = HashSet<String>()
            val flags = HashSet<String>()
            val skipped = ArrayList<IntRange>()
            var disabledFrom = -1
            fun lineOf(at: Int): IntRange {
                val start = text.lastIndexOf('\n', at - 1) + 1
                val end = text.indexOf('\n', at).let { if (it < 0) text.length else it }
                return start until end
            }
            for (m in DIRECTIVE.findAll(text)) {
                val args = m.groupValues[2].substringBefore("-->").substringBefore("*/")
                val argEnd = m.groups[2]!!.range.first + args.length
                skipped += m.range.first until argEnd
                val list = args.split(' ', '\t', ',').map { it.trim() }.filter { it.isNotEmpty() }.map(Spelling::fold)
                when (m.groupValues[1].lowercase()) {
                    "disable" -> if (disabledFrom < 0) disabledFrom = m.range.first
                    "enable" -> if (disabledFrom >= 0) { skipped += disabledFrom until argEnd; disabledFrom = -1 }
                    "disable-line" -> skipped += lineOf(m.range.first)
                    "disable-next-line" -> {
                        val next = text.indexOf('\n', m.range.first)
                        if (next >= 0) skipped += lineOf(next + 1)
                    }
                    "ignore", "words" -> words += list
                    "flagwords", "forbiddenwords" -> flags += list
                }
            }
            if (disabledFrom >= 0) skipped += disabledFrom until text.length
            return SpellComments(words, flags, skipped)
        }

        private val IGNORE_LINE = Regex("""(?i)^(\s*<!--\s*(?:cspell|spell-?checker)\s*:\s*ignore\b.*?)(\s*-->\s*)$""")

        /**
         * [text] with [word] accepted in it: added to the note's `cspell:ignore` comment, or
         * in a new one at the top (below any front matter), as `<!-- cspell:ignore word -->`.
         */
        fun ignoreInNote(text: String, word: String): String {
            val crlf = "\r\n" in text
            val lines = text.replace("\r\n", "\n").split("\n").toMutableList()
            val i = lines.indexOfFirst { IGNORE_LINE.matches(it) }
            if (i >= 0) {
                val m = IGNORE_LINE.find(lines[i])!!
                lines[i] = m.groupValues[1] + " " + word + m.groupValues[2]
            } else {
                val at = if (lines.firstOrNull()?.trimEnd() == "---") {
                    (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" || lines[it].trimEnd() == "..." }?.plus(1) ?: 0
                } else 0
                lines.add(at, "<!-- cspell:ignore $word -->")
            }
            val out = lines.joinToString("\n")
            return if (crlf) out.replace("\n", "\r\n") else out
        }
    }
}

/**
 * Checks a note's spelling the way cSpell does in VS Code, with its default settings: case
 * and accents don't matter, words shorter than four letters aren't checked, and words run
 * together aren't accepted unless the dictionary has them. camelCase words are checked part
 * by part, and words with digits or underscores aren't checked.
 *
 * Markdown that isn't prose is skipped: front matter, code, URLs and e-mail addresses, link
 * targets and wikilink targets (an alias is checked), HTML tags, math, #tags, footnote labels
 * and template variables. So are notes in cSpell's `ignorePaths`, and what `cspell:` comments
 * turn off.
 */
class SpellChecker(private val dictionaries: List<WordList>) {
    private val elisions = dictionaries.flatMap { it.elisions }.distinct()

    val isEmpty: Boolean get() = dictionaries.isEmpty()

    /** The misspelled words in [text], in order; none if [path] is in `ignorePaths`. */
    fun check(text: String, config: CSpellConfig, path: String? = null): List<SpellIssue> {
        if (dictionaries.isEmpty() || (path != null && config.ignores(path))) return emptyList()
        val comments = SpellComments.parse(text)
        val skip = Spelling.skipped(text, comments)
        val issues = ArrayList<SpellIssue>()
        val known = HashMap<String, Boolean>()
        Spelling.words(text, skip) { start, end ->
            val word = text.substring(start, end)
            // Short words aren't checked, unless they're flagged (hte).
            if (word.count { it.isLetter() } < Spelling.MIN_WORD_LENGTH) {
                val w = Spelling.fold(word)
                if (w !in config.flagWords && w !in comments.flagWords) return@words
            }
            val ok = known.getOrPut(word) { accepts(word, config, comments) }
            if (!ok) issues += SpellIssue(start, end, word)
        }
        return issues
    }

    /** Whether [word] is spelled right, given the repository's settings and the note's comments. */
    fun accepts(word: String, config: CSpellConfig, comments: SpellComments? = null): Boolean {
        val w = Spelling.fold(word)
        if (w in config.flagWords || (comments != null && w in comments.flagWords)) return false
        if (w in config.words || (comments != null && w in comments.words)) return true
        return known(w)
    }

    private fun known(w: String): Boolean {
        if (dictionaries.any { it.contains(w) }) return true
        // Possessives: Foam's, and plurals' (cSpell accepts these when the word is known).
        if (w.endsWith("'s") && w.length > 2 && dictionaries.any { it.contains(w.dropLast(2)) }) return true
        if (w.endsWith("'") && w.length > 1 && dictionaries.any { it.contains(w.dropLast(1)) }) return true
        // Elisions: l'homme, qu'il.
        for (e in elisions) {
            if (w.length > e.length && w.startsWith(e) && known(w.substring(e.length))) return true
        }
        return false
    }

    /**
     * Up to [limit] corrections for [word], closest first, written in its case (Word, WORD).
     * A `flagWords` entry's replacement (`hte->the`) comes first.
     */
    fun suggestions(word: String, config: CSpellConfig, limit: Int = 6): List<String> {
        val w = Spelling.fold(word)
        val scored = HashMap<String, Int>()
        config.flagWords[w]?.let { scored[Spelling.fold(it)] = -1 }
        val max = if (w.length <= 4) 1 else 2
        val bytes = w.encodeToByteArray().size
        // Accented letters take two bytes, so allow for them in the length window.
        val window = (bytes - max * 2).coerceAtLeast(1)..(bytes + max * 2)
        fun consider(candidate: String) {
            if (candidate == w) return
            val d = Spelling.distance(w, candidate, max)
            if (d <= max) scored[candidate] = minOf(scored[candidate] ?: Int.MAX_VALUE, d)
        }
        for (d in dictionaries) d.forEachWord(window, ::consider)
        config.words.forEach(::consider)
        return scored.entries
            .sortedWith(
                // A missed or extra doubled letter is the commonest slip: mispeled is misspelled.
                compareBy<Map.Entry<String, Int>> { if (undoubled(it.key) == undoubled(w)) 0 else 1 }
                    .thenBy { it.value }
                    .thenBy { if (it.key.firstOrNull() == w.firstOrNull()) 0 else 1 }
                    // Typos tend to come late in a word: speling is spelling before spewing.
                    .thenByDescending { it.key.commonPrefixWith(w).length + it.key.commonSuffixWith(w).length }
                    .thenBy { kotlin.math.abs(it.key.length - w.length) }
                    .thenBy { it.key },
            )
            .take(limit)
            .map { Spelling.matchCase(it.key, word) }
    }
}

/** [s] with runs of the same letter as one: misspelled is mispeled. */
private fun undoubled(s: String): String = buildString { for (c in s) if (isEmpty() || last() != c) append(c) }

object Spelling {
    /** The shortest word checked, as cSpell's minWordLength. */
    const val MIN_WORD_LENGTH = 4

    /** [word] as compared: lowercase, with typographic apostrophes as straight ones. */
    fun fold(word: String): String = word.replace('’', '\'').lowercase()

    /** [suggestion] (lowercase) written in [like]'s case: Word, WORD or word. */
    fun matchCase(suggestion: String, like: String): String = when {
        like.length > 1 && like.all { !it.isLetter() || it.isUpperCase() } -> suggestion.uppercase()
        like.firstOrNull()?.isUpperCase() == true -> suggestion.replaceFirstChar { it.uppercaseChar() }
        else -> suggestion
    }

    /**
     * The edit distance from [a] to [b] (insertions, deletions, substitutions and swaps of
     * neighbors), or more than [max] as soon as it's sure to exceed it.
     */
    fun distance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private val PATTERNS = listOf(
        Regex("""(`+)[\s\S]*?\1"""),                                  // inline code
        Regex("""</?[A-Za-z][^>\n]*>"""),                              // HTML tags
        Regex("""\b[A-Za-z][A-Za-z0-9+.-]*://[^\s<>()\]]+"""),         // URLs
        Regex("""\bwww\.[^\s<>()\]]+"""),
        Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+"""),                     // e-mail addresses
        Regex("""\]\([^)\n]*\)"""),                                    // link targets
        Regex("""(?m)^ {0,3}\[[^\]\n]+\]:.*$"""),                     // link reference definitions
        Regex("""\[\^[^\]\n]*\]"""),                                   // footnote labels
        Regex("""\$\$[\s\S]*?\$\$"""),                                 // display math
        Regex("""\\\([\s\S]*?\\\)"""),
        Regex("""\\\[[\s\S]*?\\\]"""),
        Regex("""(?<![\\$\w])\$(?=[^\s$])[^$\n]*?[^\s\\$]\$(?!\w)"""),   // inline math
        Regex("""\$\{[^}\n]*\}|\$[A-Z_][A-Z0-9_]*"""),                 // template variables
        Regex("""(?<![\w&/#])#[\p{L}\p{N}_/-]*[\p{L}_/-][\p{L}\p{N}_/-]*"""), // #tags
        Regex("""&#?\w+;"""),                                          // HTML entities
    )
    private val WIKILINK = Regex("""!?\[\[([^\]|\n]*)(?:\|[^\]\n]*)?\]\]""")

    /** Which characters of [text] aren't prose to check. */
    fun skipped(text: String, comments: SpellComments): BooleanArray {
        val skip = BooleanArray(text.length)
        fun mark(r: IntRange) { for (i in r.first.coerceAtLeast(0)..r.last.coerceAtMost(text.length - 1)) skip[i] = true }
        // Front matter.
        if (text.startsWith("---")) {
            val lines = text.split('\n')
            if (lines[0].trimEnd() == "---") {
                var offset = lines[0].length + 1
                for (k in 1 until lines.size) {
                    val end = offset + lines[k].length
                    if (lines[k].trimEnd() == "---" || lines[k].trimEnd() == "...") { mark(0 until end); break }
                    offset = end + 1
                }
            }
        }
        // Fenced code blocks, Mermaid diagrams among them.
        var fence: String? = null
        var fenceStart = 0
        var pos = 0
        for (line in text.split('\n')) {
            val t = line.trimStart()
            val indent = line.length - t.length
            if (fence == null) {
                if (indent <= 3 && (t.startsWith("```") || t.startsWith("~~~"))) {
                    fence = t.takeWhile { it == t[0] }
                    fenceStart = pos
                }
            } else if (t.startsWith(fence) && t.trimEnd().all { it == fence[0] }) {
                mark(fenceStart until pos + line.length)
                fence = null
            }
            pos += line.length + 1
        }
        if (fence != null) mark(fenceStart until text.length)
        for (p in PATTERNS) p.findAll(text).forEach { mark(it.range) }
        for (m in WIKILINK.findAll(text)) {
            mark(m.range.first..m.groups[1]!!.range.last + 1) // `[[target`, and the `|`
            mark(m.range.last - 1..m.range.last)
        }
        comments.skipped.forEach(::mark)
        return skip
    }

    private fun isWordChar(c: Char) =
        c.isLetterOrDigit() || c == '_' || c == '\'' || c == '’' || c.category == CharCategory.NON_SPACING_MARK

    /**
     * Calls [block] with the range of each word to check: runs of letters (with apostrophes
     * inside) outside [skip], split at camelCase humps. The caller skips the short ones.
     * Runs with digits or underscores are skipped whole, as cSpell does.
     */
    fun words(text: String, skip: BooleanArray, block: (start: Int, end: Int) -> Unit) {
        var i = 0
        while (i < text.length) {
            if (skip[i] || !isWordChar(text[i])) { i++; continue }
            var j = i
            while (j < text.length && !skip[j] && isWordChar(text[j])) j++
            var a = i
            var b = j
            while (a < b && (text[a] == '\'' || text[a] == '’')) a++
            while (b > a && (text[b - 1] == '\'' || text[b - 1] == '’')) b--
            // A possessive plural (notes') keeps its apostrophe.
            if (b < j && b > a && text[b - 1] == 's' && b - a >= 2) b++
            val run = text.substring(a, b)
            if (run.isNotEmpty() && run.none { it.isDigit() || it == '_' }) {
                var s = a
                for (k in a + 1 until b) {
                    val prev = text[k - 1]
                    val c = text[k]
                    val hump = (prev.isLowerCase() && c.isUpperCase()) ||
                        (prev.isUpperCase() && c.isUpperCase() && k + 1 < b && text[k + 1].isLowerCase())
                    if (hump) { emit(text, s, k, block); s = k }
                }
                emit(text, s, b, block)
            }
            i = j
        }
    }

    private fun emit(text: String, start: Int, end: Int, block: (Int, Int) -> Unit) {
        if (end > start) block(start, end)
    }
}
