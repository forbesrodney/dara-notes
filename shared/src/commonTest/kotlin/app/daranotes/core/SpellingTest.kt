package app.daranotes.core

import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.GzipSink
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpellingTest {
    private val english = WordList.of(
        listOf("this", "note", "notes", "about", "spelling", "with", "word", "words", "camel", "case", "server",
            "http", "foam", "plan", "inline", "code", "footnote", "the", "there", "their", "where", "here", "house", "hello", "help", "link", "alias"),
    )
    private val checker = SpellChecker(listOf(english))
    private val none = CSpellConfig()

    private fun flagged(text: String, config: CSpellConfig = none, path: String? = null) =
        checker.check(text, config, path).map { it.word }

    @Test fun findsMisspelledWords() {
        assertEquals(listOf("abuot", "speling"), flagged("This note is abuot speling."))
        val issue = checker.check("A note abuot", none).single()
        assertEquals("abuot", "A note abuot".substring(issue.start, issue.end))
    }

    @Test fun ignoresCaseShortWordsDigitsAndUnderscores() {
        assertEquals(emptyList(), flagged("THIS Note, xyz qq abc1234 snake_case_wrod v2beta"))
    }

    @Test fun checksCamelCasePartByPart() {
        assertEquals(emptyList(), flagged("camelCase HTTPServer"))
        assertEquals(listOf("Sevrer"), flagged("HTTPSevrer"))
    }

    @Test fun acceptsPossessivesAndTypographicApostrophes() {
        assertEquals(emptyList(), flagged("Foam's notes' house’s"))
    }

    @Test fun acceptsContractionsOfKnownAndIgnoredWords() {
        assertEquals(emptyList(), flagged("there'll here'd where've there're"))
        assertEquals(listOf("wrogn'll"), flagged("wrogn'll"))
        // Ignoring or adding a word covers its possessive, and a camelCase word whole.
        assertEquals(emptyList(), flagged("<!-- cspell:ignore namecheap -->\nNamecheap's NameCheap's"))
        assertEquals(emptyList(), flagged("Namecheap's", CSpellConfig(words = setOf("namecheap"))))
        assertEquals(emptyList(), flagged("<!-- cspell:ignore MyWrognNote -->\nMyWrognNote's"))
        assertEquals(listOf("Wrogn"), flagged("MyWrognNote"))
    }

    @Test fun skipsMarkdownThatIsntProse() {
        val text = """
            |---
            |title: Wrogn
            |tags: [mispeled]
            |---
            |# Plan
            |
            |```kotlin
            |val qwerty = asdfgh()
            |```
            |
            |Inline `zxcvb` code, https://exmaple.com/pathh and someone@exmaple.com.
            |[link](../folderr/notte.md) and [[Wikii Notte|alias]] and ![[imagge.png]].
            |#tagg <span class="clas">help</span> ${'$'}x^2 + yyyy${'$'} ${'$'}FOAM_TITEL footnote[^notte].
            |
            |[reff]: https://exmaple.com
        """.trimMargin()
        assertEquals(emptyList(), flagged(text))
        // A wikilink's alias is prose.
        assertEquals(listOf("aliass"), flagged("[[note|aliass]]"))
    }

    @Test fun followsCspellComments() {
        assertEquals(emptyList(), flagged("<!-- cspell:disable -->\nwrogn wrods"))
        assertEquals(listOf("baad"), flagged("<!-- cspell:disable -->\nwrogn\n<!-- cspell:enable -->\nbaad"))
        assertEquals(listOf("baad"), flagged("<!-- cspell:disable-next-line -->\nwrogn\nbaad"))
        assertEquals(listOf("baad"), flagged("wrogn <!-- cspell:disable-line -->\nbaad"))
        assertEquals(listOf("baad"), flagged("<!-- cspell:ignore wrogn, Dara -->\nwrogn Dara baad"))
        assertEquals(listOf("baad"), flagged("<!-- spell-checker:words wrogn -->\nWrogn baad"))
        assertEquals(listOf("there"), flagged("<!-- cSpell:flagWords there -->\nthere here"))
    }

    @Test fun usesTheRepositorysSettings() {
        val settings = """
            {
              // Set language locale
              "cSpell.language": "en-US",
              "cSpell.userWords": ["mycustomterm"],
              "cSpell.ignoreWords": ["behaviour"],
              "cSpell.flagWords": ["hte"],
              "cSpell.ignorePaths": ["drafts/**"],
            }
        """.trimIndent()
        val cspell = """{ "words": ["Dara"], "flagWords": ["teh->the"], "ignorePaths": ["*.tmp.md"] }"""
        val config = CSpellConfig.parse(cspell, settings)
        assertEquals(listOf("hte", "teh"), flagged("mycustomterm behaviour dara hte teh", config))
        assertEquals(emptyList(), flagged("wrogn", config, "drafts/a.md"))
        assertEquals(emptyList(), flagged("wrogn", config, "deep/in/x.tmp.md"))
        assertEquals(listOf("wrogn"), flagged("wrogn", config, "notes/a.md"))
        // A flagWords replacement is the first suggestion.
        assertEquals("the", checker.suggestions("teh", config).first())
    }

    @Test fun suggestsCloseWordsInTheWordsCase() {
        assertEquals("note", checker.suggestions("ntoe", none).first())
        assertEquals("spelling", SpellChecker(listOf(WordList.of(listOf("spewing", "spelling")))).suggestions("speling", none).first())
        assertEquals("misspelled", SpellChecker(listOf(WordList.of(listOf("misfiled", "misled", "misspelled")))).suggestions("mispeled", none).first())
        assertEquals("House", checker.suggestions("Huose", none).first())
        assertEquals("SPELLING", checker.suggestions("SPELING", none).first())
        assertTrue("there" in checker.suggestions("thier", none))
        assertEquals(emptyList(), checker.suggestions("zzzzzzzzz", none))
    }

    @Test fun accentsAndElisions() {
        val french = WordList.parse(
            "#dara-dictionary 1\n#elisions l' qu'\ncafé\nhomme\nil\n#accent-free\ncafe\n".encodeToByteArray(),
        )
        val c = SpellChecker(listOf(french))
        assertEquals(emptyList(), c.check("Café cafe l'homme qu’il", none).map { it.word })
        assertEquals(listOf("l'hommme"), c.check("l'hommme", none).map { it.word })
    }

    @Test fun wordListsSortedByHandStillWork() {
        val list = WordList.parse("zeta\nalpha\nmike\n".encodeToByteArray())
        assertTrue(list.contains("alpha") && list.contains("mike") && list.contains("zeta"))
        assertFalse(list.contains("beta"))
    }

    @Test fun distanceCountsSwapsAsOneEdit() {
        assertEquals(1, Spelling.distance("teh", "the", 2))
        assertEquals(2, Spelling.distance("abcd", "acbe", 2))
        assertEquals(3, Spelling.distance("abc", "xyzw", 2))
    }

    // ---- writing ----------------------------------------------------------------------------

    @Test fun ignoreInNoteAddsOrExtendsTheComment() {
        assertEquals("<!-- cspell:ignore wrogn -->\n# T\n", SpellComments.ignoreInNote("# T\n", "wrogn"))
        assertEquals(
            "---\ntags: [a]\n---\n<!-- cspell:ignore wrogn -->\n# T\n",
            SpellComments.ignoreInNote("---\ntags: [a]\n---\n# T\n", "wrogn"),
        )
        assertEquals(
            "# T\n<!-- cspell:ignore one wrogn -->\ntext\n",
            SpellComments.ignoreInNote("# T\n<!-- cspell:ignore one -->\ntext\n", "wrogn"),
        )
        assertEquals("<!-- cspell:ignore wrogn -->\r\n# T\r\n", SpellComments.ignoreInNote("# T\r\n", "wrogn"))
    }

    @Test fun addsWordsToJsonWithComments() {
        val settings = """
            {
              // Set language locale
              "cSpell.language": "en-US",
              // Words accepted
              "cSpell.words": [
                "mycustomterm",
                "companyname"
              ],
              "foam.orphans.exclude": ["journal/**"]
            }
        """.trimIndent()
        val added = Jsonc.addToArray(settings, "cSpell.words", "Dara")
        assertEquals(settings.replace("\"companyname\"\n", "\"companyname\",\n    \"Dara\"\n"), added)
        assertEquals(added, Jsonc.addToArray(added, "cSpell.words", "Dara"))

        // No such key yet: added at the top, in the file's indentation.
        val noKey = "{\n    // comment\n    \"a\": 1\n}\n"
        assertEquals("{\n    \"cSpell.words\": [\"Dara\"],\n    // comment\n    \"a\": 1\n}\n", Jsonc.addToArray(noKey, "cSpell.words", "Dara"))
        // A one-line array, an empty one, a trailing comma, an empty object, no file.
        assertEquals("""{"words": ["a", "b"]}""", Jsonc.addToArray("""{"words": ["a"]}""", "words", "b"))
        assertEquals("""{"words": ["b"]}""", Jsonc.addToArray("""{"words": []}""", "words", "b"))
        assertEquals("{\"words\": [\n  \"a\",\n  \"b\"\n]}", Jsonc.addToArray("{\"words\": [\n  \"a\",\n]}", "words", "b"))
        assertEquals("{\n  \"words\": [\"b\"]\n}", Jsonc.addToArray("{}", "words", "b"))
        assertEquals("{\n  \"cSpell.words\": [\n    \"b\"\n  ]\n}\n", Jsonc.addToArray(null, "cSpell.words", "b"))
        // Nested keys of the same name aren't touched.
        assertEquals(
            """{"cSpell.words": ["b"], "x": {"cSpell.words": []}}""",
            Jsonc.addToArray("""{"x": {"cSpell.words": []}}""", "cSpell.words", "b"),
        )
    }

    @Test fun addWordPicksCspellJsonWhenThereIsOne() {
        val fs = FakeFileSystem()
        val root = "/repo".toPath()
        fs.createDirectories(root)
        assertEquals(".vscode/settings.json", CSpellConfig.addWord(fs, root, "Dara"))
        assertEquals(setOf("dara"), CSpellConfig.load(fs, root).words)
        fs.write(root / "cspell.json") { writeUtf8("{\n  \"version\": \"0.2\"\n}\n") }
        assertEquals("cspell.json", CSpellConfig.addWord(fs, root, "Foam"))
        assertEquals(setOf("dara", "foam"), CSpellConfig.load(fs, root).words)
    }

    // ---- downloads --------------------------------------------------------------------------

    private fun gz(text: String): ByteArray {
        val out = Buffer()
        GzipSink(out).buffer().use { it.writeUtf8(text) }
        return out.readByteArray()
    }

    @Test fun installsChecksAndRemovesDictionaries() {
        val fs = FakeFileSystem()
        val en = gz("#dara-dictionary 1\nhello\nworld\n#accent-free\n")
        val extras = gz("#dara-dictionary 1\nkotlin\n#accent-free\n")
        fun info(code: String, data: ByteArray) =
            """{"code":"$code","name":"$code","file":"$code.txt.gz","size":${data.size},"sha256":"${data.toByteString().sha256().hex()}","words":1,"license":"MIT","licenseFile":"$code.LICENSE.txt"}"""
        val manifest = """{"version":1,"languages":[${info("en-US", en)}],"extras":${info("software-terms", extras)}}"""
        val files = mapOf("manifest.json" to manifest.encodeToByteArray(), "en-US.txt.gz" to en, "software-terms.txt.gz" to extras)
        val d = Dictionaries(fs, "/data/dictionaries".toPath(), "https://x/") { url -> files[url.removePrefix("https://x/")] ?: error("404 $url") }

        val m = d.manifest()
        assertEquals(listOf("en-US"), m.languages.map { it.code })
        d.install(m.languages.single(), m)
        assertEquals(listOf("en-US"), d.languages().map { it.code })
        assertEquals(setOf("en-US", "software-terms"), d.installed().map { it.code }.toSet())
        val c = SpellChecker(d.load())
        assertEquals(listOf("wrold"), c.check("hello wrold kotlin", none).map { it.word })

        d.remove("en-US")
        assertEquals(emptyList(), d.installed())

        // A damaged download is refused.
        val bad = Dictionaries(fs, "/data/dictionaries".toPath(), "https://x/") { url ->
            if (url.endsWith("en-US.txt.gz")) gz("tampered\n") else files[url.removePrefix("https://x/")]!!
        }
        assertFailsWith<okio.IOException> { bad.install(m.languages.single(), m) }
        assertEquals(emptyList(), bad.installed())
    }
}
