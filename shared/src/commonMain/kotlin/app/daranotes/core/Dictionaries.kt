package app.daranotes.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.IOException
import okio.Path
import kotlinx.serialization.json.Json as JsonParser

/** A dictionary offered for download, as manifest.json describes it. */
data class DictionaryInfo(
    val code: String,
    val name: String,
    val file: String,
    val size: Long,
    val sha256: String,
    val words: Int,
    val license: String,
    val licenseFile: String,
) {
    companion object {
        fun parse(o: JsonObject): DictionaryInfo? {
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            return DictionaryInfo(
                code = s("code") ?: return null,
                name = s("name") ?: s("code")!!,
                file = s("file") ?: return null,
                size = (o["size"] as? JsonPrimitive)?.longOrNull ?: 0,
                sha256 = s("sha256") ?: return null,
                words = (o["words"] as? JsonPrimitive)?.intOrNull ?: 0,
                license = s("license") ?: "",
                licenseFile = s("licenseFile") ?: "",
            )
        }
    }
}

/** The dictionaries available: the languages, and the extras that come with any of them. */
data class DictionaryManifest(val languages: List<DictionaryInfo>, val extras: DictionaryInfo?) {
    companion object {
        fun parse(json: String): DictionaryManifest {
            val o = JsonParser.parseToJsonElement(json).jsonObject
            val languages = (o["languages"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(DictionaryInfo::parse) }
            return DictionaryManifest(languages, (o["extras"] as? JsonObject)?.let(DictionaryInfo::parse))
        }
    }
}

/** A downloaded dictionary. */
data class InstalledDictionary(val code: String, val name: String, val sha256: String, val extras: Boolean)

/**
 * The spelling dictionaries on this device, in [dir] (in the app's own storage, never the
 * notes repository): downloaded from [baseUrl] (the `dictionaries` release that
 * dictionaries/build.py makes), checked against the manifest's checksums, and listed in
 * installed.json. The extras (software terms and company names) come with the first language
 * and go with the last.
 */
class Dictionaries(
    private val fs: FileSystem,
    val dir: Path,
    val baseUrl: String = BASE_URL,
    private val http: (String) -> ByteArray = ::httpGet,
) {
    private val index get() = dir / "installed.json"

    fun installed(): List<InstalledDictionary> = runCatching {
        val o = JsonParser.parseToJsonElement(fs.read(index) { readUtf8() }).jsonObject
        o.entries.mapNotNull { (code, v) ->
            val e = v as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (e[k] as? JsonPrimitive)?.contentOrNull
            InstalledDictionary(code, s("name") ?: code, s("sha256") ?: "", s("extras") == "true")
        }.filter { fs.exists(dir / "${it.code}.txt.gz") }
    }.getOrDefault(emptyList())

    /** The installed languages (not the extras). */
    fun languages(): List<InstalledDictionary> = installed().filter { !it.extras }

    /** Fetches the list of dictionaries available. */
    fun manifest(): DictionaryManifest = DictionaryManifest.parse(http(baseUrl + MANIFEST).decodeToString())

    /** Downloads [language] (and the extras, if they're missing or out of date). */
    fun install(language: DictionaryInfo, manifest: DictionaryManifest) {
        val have = installed().associateBy { it.code }
        val all = buildList {
            add(language to false)
            manifest.extras?.let { if (have[it.code]?.sha256 != it.sha256) add(it to true) }
        }
        for ((info, extras) in all) {
            val data = http(baseUrl + info.file)
            val sum = data.toByteString().sha256().hex()
            if (sum != info.sha256) throw IOException("The download of ${info.name} was damaged (checksum mismatch); try again")
            // Make sure it reads before replacing anything.
            WordList.parse(WordList.gunzip(data))
            fs.createDirectories(dir)
            val tmp = dir / "${info.code}.txt.gz.tmp"
            fs.write(tmp) { write(data) }
            fs.atomicMove(tmp, dir / "${info.code}.txt.gz")
            record(InstalledDictionary(info.code, info.name, info.sha256, extras))
        }
    }

    /** Removes the language [code], and the extras with the last language. */
    fun remove(code: String) {
        val left = installed().filter { it.code != code }
        fs.delete(dir / "$code.txt.gz", mustExist = false)
        val keep = if (left.none { !it.extras }) {
            left.filter { it.extras }.forEach { fs.delete(dir / "${it.code}.txt.gz", mustExist = false) }
            emptyList()
        } else left
        write(keep)
    }

    /** The installed dictionaries, read into memory. Unreadable ones are skipped. */
    fun load(): List<WordList> = installed().mapNotNull { d ->
        runCatching { WordList.readGzip(fs, dir / "${d.code}.txt.gz") }.getOrNull()
    }

    /** Where [info]'s license can be read. */
    fun licenseUrl(info: DictionaryInfo): String = baseUrl + info.licenseFile

    private fun record(d: InstalledDictionary) = write(installed().filter { it.code != d.code } + d)

    private fun write(list: List<InstalledDictionary>) {
        fs.createDirectories(dir)
        val json = buildJsonObject {
            for (d in list) putJsonObject(d.code) {
                put("name", d.name)
                put("sha256", d.sha256)
                put("extras", d.extras.toString())
            }
        }.toString()
        fs.write(index) { writeUtf8(json) }
    }

    companion object {
        /** The `dictionaries` release, which .github/workflows/dictionaries.yml publishes. */
        const val BASE_URL = "https://github.com/forbesrodney/dara-notes/releases/download/dictionaries/"
        const val MANIFEST = "manifest.json"
    }
}
