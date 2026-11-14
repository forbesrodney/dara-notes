package app.daranotes.core

import okio.Buffer
import okio.FileSystem
import okio.GzipSource
import okio.Path
import okio.buffer
import okio.use

/**
 * A spelling dictionary in memory: a word list from dictionaries/build.py, kept as its UTF-8
 * bytes with an index of where each word starts, so even a large language (Spanish has some
 * 650,000 words) takes little more memory than the file itself. Words are lowercase and sorted
 * by code point (the same order as their UTF-8 bytes), so lookups are a binary search.
 *
 * The file's format:
 *
 *     #dara-dictionary 1
 *     #elisions l' d' qu'      optional: prefixes joined to a word by an apostrophe
 *     word                     the words, sorted
 *     #accent-free
 *     cafe                     accent-free spellings of accented words, also sorted
 */
class WordList private constructor(
    private val bytes: ByteArray,
    private val words: IntArray,
    private val accentFree: IntArray,
    /** Prefixes such as French `l'` that join a word with an apostrophe. */
    val elisions: List<String>,
) {
    val size: Int get() = words.size

    /** Whether [word] (lowercase) is in the list, or is an accent-free spelling of one. */
    fun contains(word: String): Boolean {
        if (word.isEmpty()) return false
        val key = word.encodeToByteArray()
        return search(words, key) || search(accentFree, key)
    }

    /**
     * Calls [block] with each word (not the accent-free spellings) whose UTF-8 length is in
     * [byteLengths]: the candidates for suggestions.
     */
    fun forEachWord(byteLengths: IntRange, block: (String) -> Unit) {
        for (start in words) {
            var end = start
            while (end < bytes.size && bytes[end] != NL) end++
            if (end - start in byteLengths) block(bytes.decodeToString(start, end))
        }
    }

    private fun search(index: IntArray, key: ByteArray): Boolean {
        var lo = 0
        var hi = index.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(index[mid], key)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return true
            }
        }
        return false
    }

    /** The word starting at [start] compared with [key], as unsigned bytes. */
    private fun compare(start: Int, key: ByteArray): Int {
        var i = 0
        while (true) {
            val p = start + i
            val atEnd = p >= bytes.size || bytes[p] == NL
            if (i == key.size) return if (atEnd) 0 else 1
            if (atEnd) return -1
            val d = (bytes[p].toInt() and 0xff) - (key[i].toInt() and 0xff)
            if (d != 0) return d
            i++
        }
    }

    companion object {
        private const val NL = '\n'.code.toByte()

        /** Reads a gzipped list, as downloaded. */
        fun readGzip(fs: FileSystem, path: Path): WordList =
            parse(GzipSource(fs.source(path)).buffer().use { it.readByteArray() })

        fun gunzip(gz: ByteArray): ByteArray =
            GzipSource(Buffer().write(gz)).buffer().use { it.readByteArray() }

        /** A list of [words], for tests and for words that aren't in a file. */
        fun of(words: Collection<String>, accentFree: Collection<String> = emptyList()): WordList =
            parse(
                buildString {
                    append("#dara-dictionary 1\n")
                    words.map { it.lowercase() }.distinct().sorted().forEach { append(it).append('\n') }
                    append("#accent-free\n")
                    accentFree.map { it.lowercase() }.distinct().sorted().forEach { append(it).append('\n') }
                }.encodeToByteArray(),
            )

        /** Parses an uncompressed list. */
        fun parse(bytes: ByteArray): WordList {
            val words = IntList()
            val accentFree = IntList()
            var elisions = emptyList<String>()
            var current = words
            var start = 0
            while (start < bytes.size) {
                var end = start
                while (end < bytes.size && bytes[end] != NL) end++
                if (end > start) {
                    if (bytes[start] == '#'.code.toByte()) {
                        val line = bytes.decodeToString(start, end)
                        when {
                            line == "#accent-free" -> current = accentFree
                            line.startsWith("#elisions ") ->
                                elisions = line.removePrefix("#elisions ").split(' ').filter { it.isNotEmpty() }
                        }
                    } else {
                        current.add(start)
                    }
                }
                start = end + 1
            }
            val list = WordList(bytes, words.toArray(), accentFree.toArray(), elisions)
            // The build sorts them; a list that isn't (made by hand, say) is sorted here.
            return if (list.sorted(list.words) && list.sorted(list.accentFree)) list
            else WordList(bytes, list.resorted(list.words), list.resorted(list.accentFree), elisions)
        }
    }

    private fun wordAt(start: Int): ByteArray {
        var end = start
        while (end < bytes.size && bytes[end] != NL) end++
        return bytes.copyOfRange(start, end)
    }

    private fun sorted(index: IntArray): Boolean {
        for (i in 1 until index.size) if (compare(index[i - 1], wordAt(index[i])) > 0) return false
        return true
    }

    private fun resorted(index: IntArray): IntArray =
        index.sortedWith { a, b -> compare(a, wordAt(b)) }.toIntArray()

    /** A growable IntArray, so the index doesn't box every offset. */
    private class IntList {
        private var data = IntArray(1024)
        private var n = 0
        fun add(v: Int) {
            if (n == data.size) data = data.copyOf(n * 2)
            data[n++] = v
        }
        fun toArray(): IntArray = data.copyOf(n)
    }
}
