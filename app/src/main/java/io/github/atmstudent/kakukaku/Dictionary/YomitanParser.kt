package io.github.atmstudent.kakukaku.Dictionary

import io.github.atmstudent.kakukaku.toHiragana
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/** [frequencyMode] is "rank-based" (1 is the most common word) or "occurrence-based" (a bigger number is more common); empty if the dictionary does not say */
data class DictionaryMeta(val title: String, val revision: String, val format: Int, val frequencyMode: String = "")

/**
 * One row of a term bank. [senses] holds one text per definition and [tags] the definition tags
 * (for example part of speech). Rows of the same word with the same [sequence] belong together.
 */
data class ImportedTerm(
        val term: String,
        val reading: String,
        val tags: String,
        val rules: String,
        val score: Int,
        val sequence: Long,
        val glossary: String)

/** One pitch accent of a word: the downstep positions ("0" is flat, "1" drops after the first mora) of a reading, comma separated */
data class ImportedPitch(val term: String, val reading: String, val positions: String)

/**
 * The frequency of a word's reading, as a rank where a smaller number is a more common word. [reading] is
 * hiragana, or empty if the dictionary gives none (then the entry is the word itself, which is how kana-only words come).
 */
data class ImportedFrequency(val term: String, val reading: String, val rank: Double)

class DictionaryFormatException(message: String) : Exception(message)

/** Reports the total number of bytes read so far */
private class CountingInputStream(private val source: InputStream, private val onBytesRead: (Long) -> Unit) : InputStream()
{
    private var total = 0L

    override fun read(): Int
    {
        val b = source.read()
        if (b >= 0) advance(1)
        return b
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int
    {
        val n = source.read(buffer, offset, length)
        if (n > 0) advance(n)
        return n
    }

    override fun available(): Int = source.available()

    override fun close() = source.close()

    private fun advance(n: Int)
    {
        total += n
        onBytesRead(total)
    }
}

/**
 * Reads dictionaries in the Yomitan/Yomichan zip format: an index.json plus term_bank_N.json and
 * kanji_bank_N.json files. Other meta banks are ignored; pitch accent banks are read by [parsePitch] and frequency banks by [parseFrequency].
 */
object YomitanParser
{
    private val TERM_BANK = Regex("(.*/)?term_bank_\\d+\\.json")
    private val KANJI_BANK = Regex("(.*/)?kanji_bank_\\d+\\.json")
    private val META_BANK = Regex("(.*/)?term_meta_bank_\\d+\\.json")

    /**
     * @param openStream opens the zip from the start; called twice because the zip is read sequentially
     * @param onTerms called with each batch of rows (one bank file at a time)
     * @return the dictionary's metadata and how many rows were read
     */
    fun parse(openStream: () -> InputStream, onBytesRead: (Long) -> Unit = {}, onTerms: (List<ImportedTerm>) -> Unit): Pair<DictionaryMeta, Int>
    {
        val meta = readIndex(openStream)

        var count = 0
        openStream().use { raw ->
            // Counting the compressed bytes read from the file gives a progress fraction without knowing the entry count
            val zip = ZipInputStream(CountingInputStream(raw, onBytesRead))
            while (true)
            {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue

                val rows = when
                {
                    TERM_BANK.matches(entry.name) -> parseTermBank(readJson(zip), meta.format)
                    KANJI_BANK.matches(entry.name) -> parseKanjiBank(readJson(zip))
                    else -> continue
                }

                if (rows.isNotEmpty())
                {
                    count += rows.size
                    onTerms(rows)
                }
            }
        }

        if (count == 0)
        {
            throw DictionaryFormatException("This dictionary has no term or kanji entries (pitch accent and frequency dictionaries have their own imports).")
        }

        return Pair(meta, count)
    }

    /**
     * Reads the pitch accent entries of a Yomitan zip (term_meta_bank_N.json rows [term, "pitch", {reading, pitches}]).
     * Readings are stored in hiragana so that katakana and hiragana spellings match.
     */
    fun parsePitch(openStream: () -> InputStream, onBytesRead: (Long) -> Unit = {}, onRows: (List<ImportedPitch>) -> Unit): Pair<DictionaryMeta, Int>
    {
        val meta = readIndex(openStream)

        var count = 0
        openStream().use { raw ->
            val zip = ZipInputStream(CountingInputStream(raw, onBytesRead))
            while (true)
            {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !META_BANK.matches(entry.name)) continue

                val rows = parsePitchBank(readJson(zip))
                if (rows.isNotEmpty())
                {
                    count += rows.size
                    onRows(rows)
                }
            }
        }

        if (count == 0)
        {
            throw DictionaryFormatException("This dictionary has no pitch accent entries (term_meta_bank files with \"pitch\" rows).")
        }

        return Pair(meta, count)
    }

    /**
     * Reads the frequency entries of a Yomitan zip (term_meta_bank_N.json rows [term, "freq", data]). The data is
     * a number, {value, displayValue}, or {reading, frequency: number or {value, displayValue}}. Frequency banks
     * are big (a million rows, 80 MB of text), so they are read as a stream and handed on in batches.
     * Occurrence-based counts are stored negated, so a smaller number is always a more common word.
     */
    fun parseFrequency(openStream: () -> InputStream, onBytesRead: (Long) -> Unit = {}, onRows: (List<ImportedFrequency>) -> Unit): Pair<DictionaryMeta, Int>
    {
        val meta = readIndex(openStream)
        val sign = if (meta.frequencyMode == "occurrence-based") -1.0 else 1.0

        var count = 0
        openStream().use { raw ->
            val zip = ZipInputStream(CountingInputStream(raw, onBytesRead))
            while (true)
            {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !META_BANK.matches(entry.name)) continue

                // Not closed on purpose: that would close the whole zip stream
                val reader = JsonReader(InputStreamReader(zip, Charsets.UTF_8))
                if (reader.peek() != com.google.gson.stream.JsonToken.BEGIN_ARRAY) continue

                val batch = ArrayList<ImportedFrequency>()
                reader.beginArray()
                while (reader.hasNext())
                {
                    val item = JsonParser.parseReader(reader)
                    parseFrequencyRow(item, sign)?.let { batch.add(it) }

                    if (batch.size >= FREQUENCY_BATCH)
                    {
                        count += batch.size
                        onRows(ArrayList(batch))
                        batch.clear()
                    }
                }
                reader.endArray()

                if (batch.isNotEmpty())
                {
                    count += batch.size
                    onRows(batch)
                }
            }
        }

        if (count == 0)
        {
            throw DictionaryFormatException("This dictionary has no frequency entries (term_meta_bank files with \"freq\" rows).")
        }

        return Pair(meta, count)
    }

    private const val FREQUENCY_BATCH = 20000

    private fun parseFrequencyRow(item: JsonElement, sign: Double): ImportedFrequency?
    {
        if (!item.isJsonArray) return null
        val a = item.asJsonArray
        if (a.size() < 3 || a[1].text() != "freq") return null

        val term = a[0].text()
        if (term.isEmpty()) return null

        var data = a[2]
        var reading = ""
        if (data.isJsonObject && data.asJsonObject.has("frequency"))
        {
            reading = data.asJsonObject.string("reading")
            data = data.asJsonObject.get("frequency")
        }

        val value = when
        {
            data.isJsonPrimitive && data.asJsonPrimitive.isNumber -> data.asDouble
            data.isJsonObject && data.asJsonObject.get("value")?.isJsonPrimitive == true -> data.asJsonObject.get("value").asDouble
            else -> return null
        }

        return ImportedFrequency(term, toHiragana(reading), value * sign)
    }

    private fun parsePitchBank(bank: JsonElement): List<ImportedPitch>
    {
        val rows = ArrayList<ImportedPitch>()
        if (!bank.isJsonArray) return rows

        for (item in bank.asJsonArray)
        {
            if (!item.isJsonArray) continue
            val a = item.asJsonArray
            if (a.size() < 3 || a[1].text() != "pitch" || !a[2].isJsonObject) continue

            val term = a[0].text()
            val data = a[2].asJsonObject
            val reading = data.string("reading").ifEmpty { term }
            val pitches = data.get("pitches")
            if (term.isEmpty() || pitches == null || !pitches.isJsonArray) continue

            val positions = LinkedHashSet<String>()
            for (p in pitches.asJsonArray)
            {
                if (!p.isJsonObject) continue
                val position = p.asJsonObject.get("position")
                if (position == null || !position.isJsonPrimitive) continue
                downstep(position.asString)?.let { positions.add(it) }
            }
            if (positions.isEmpty()) continue

            rows.add(ImportedPitch(term, toHiragana(reading), positions.joinToString(",")))
        }

        return rows
    }

    /** A position is a number, or a high/low pattern such as "LHHL" (the drop is after the last H, if an L follows it) */
    private fun downstep(position: String): String?
    {
        position.toIntOrNull()?.let { return it.toString() }
        if (position.isEmpty() || !position.all { it == 'H' || it == 'L' }) return null

        val lastHigh = position.lastIndexOf('H')
        return if (lastHigh >= 0 && lastHigh < position.length - 1) (lastHigh + 1).toString() else "0"
    }

    fun readIndex(openStream: () -> InputStream): DictionaryMeta
    {
        openStream().use { raw ->
            val zip = ZipInputStream(raw)
            while (true)
            {
                val entry = zip.nextEntry ?: break
                if (entry.name != "index.json") continue

                val index = readJson(zip)
                if (!index.isJsonObject) break

                val obj = index.asJsonObject
                val title = obj.string("title")
                if (title.isEmpty()) throw DictionaryFormatException("index.json has no title.")

                val format = obj.get("format")?.takeIf { it.isJsonPrimitive }?.asInt
                        ?: obj.get("version")?.takeIf { it.isJsonPrimitive }?.asInt
                        ?: 1
                return DictionaryMeta(title, obj.string("revision"), format, obj.string("frequencyMode"))
            }
        }

        throw DictionaryFormatException("Not a Yomitan dictionary: index.json was not found at the top level of the zip.")
    }

    private fun readJson(zip: InputStream): JsonElement
    {
        // Not closed on purpose: that would close the whole zip stream
        return JsonParser.parseReader(JsonReader(InputStreamReader(zip, Charsets.UTF_8)))
    }

    private fun JsonObject.string(name: String): String
    {
        val e = get(name)
        return if (e != null && e.isJsonPrimitive) e.asString else ""
    }

    private fun JsonElement.text(): String
    {
        return if (isJsonPrimitive) asString else ""
    }

    private fun parseTermBank(bank: JsonElement, format: Int): List<ImportedTerm>
    {
        val rows = ArrayList<ImportedTerm>()
        if (!bank.isJsonArray) return rows

        for (item in bank.asJsonArray)
        {
            if (!item.isJsonArray) continue
            val a = item.asJsonArray
            if (a.size() < 6) continue

            val term = a[0].text()
            if (term.isEmpty()) continue

            // Format 3 keeps the definitions in an array at index 5; older formats list them from index 5 on
            val glossaryItems = if (a[5].isJsonArray) a[5].asJsonArray.toList() else a.toList().subList(5, a.size())
            val glossary = glossaryText(glossaryItems)
            if (glossary.isEmpty()) continue

            // JMdict builds start the tags with the sense number, and carry a table of alternative forms
            // as a separate "forms" row that has no meaning of its own: drop both
            val tagTokens = a[2].text().split(" ").filter { it.isNotEmpty() && !it.all { c -> c.isDigit() } }
            if ("forms" in tagTokens) continue

            val reading = a[1].text().let { if (it == term) "" else it }
            val score = if (a[4].isJsonPrimitive) a[4].asDouble.toInt() else 0
            val sequence = if (a[5].isJsonArray && a.size() > 6 && a[6].isJsonPrimitive) a[6].asLong else 0L

            rows.add(ImportedTerm(term, reading, tagTokens.joinToString(" "), a[3].text().trim(), score, sequence, glossary))
        }

        return rows
    }

    /** Kanji banks: [character, onyomi, kunyomi, tags, meanings[], stats] */
    private fun parseKanjiBank(bank: JsonElement): List<ImportedTerm>
    {
        val rows = ArrayList<ImportedTerm>()
        if (!bank.isJsonArray) return rows

        for (item in bank.asJsonArray)
        {
            if (!item.isJsonArray) continue
            val a = item.asJsonArray
            if (a.size() < 5 || !a[4].isJsonArray) continue

            val character = a[0].text()
            val meanings = a[4].asJsonArray.map { it.text() }.filter { it.isNotEmpty() }
            if (character.isEmpty() || meanings.isEmpty()) continue

            val on = a[1].text().trim().replace(" ", "、")
            val kun = a[2].text().trim().replace(" ", "、")
            val reading = listOf(on, kun).filter { it.isNotEmpty() }.joinToString(" / ")

            rows.add(ImportedTerm(character, reading, a[3].text().trim(), "", 0, 0L, meanings.joinToString(", ")))
        }

        return rows
    }

    private fun glossaryText(items: List<JsonElement>): String
    {
        val parts = ArrayList<String>()
        var structured = false

        for (item in items)
        {
            when
            {
                item.isJsonPrimitive -> parts.add(item.asString)
                item.isJsonObject ->
                {
                    val obj = item.asJsonObject
                    when (obj.string("type"))
                    {
                        "text" -> parts.add(obj.string("text"))
                        "structured-content" ->
                        {
                            structured = true
                            parts.add(flatten(obj.get("content")))
                        }
                        // images and unknown types have no plain-text form
                    }
                }
            }
        }

        // Plain glosses read as a list; structured content already carries its own structure
        val separator = if (structured) " " else ", "
        return parts.map { collapse(it) }.filter { it.isNotEmpty() }.joinToString(separator)
    }

    private val BLOCK_TAGS = setOf("div", "li", "ul", "ol", "p", "br", "tr", "table", "details", "summary", "h1", "h2", "h3")
    private val SKIPPED_TAGS = setOf("img", "rt", "rp")

    /** Turns Yomitan structured content (nested strings, arrays and tagged objects) into plain text */
    fun flatten(content: JsonElement?): String
    {
        val sb = StringBuilder()
        append(sb, content)
        return collapse(sb.toString())
    }

    private fun append(sb: StringBuilder, e: JsonElement?)
    {
        when
        {
            e == null || e.isJsonNull -> return
            e.isJsonPrimitive -> sb.append(e.asString)
            e.isJsonArray ->
            {
                // Separate the items of a list ("food; foodstuff") instead of running them together
                var previousWasListItem = false
                for (child in e.asJsonArray)
                {
                    val isListItem = child.isJsonObject && child.asJsonObject.string("tag") == "li"
                    if (isListItem && previousWasListItem) sb.append("; ")
                    append(sb, child)
                    previousWasListItem = isListItem
                }
            }
            e.isJsonObject ->
            {
                val obj = e.asJsonObject
                val tag = obj.string("tag")
                if (tag in SKIPPED_TAGS) return

                val block = tag in BLOCK_TAGS
                if (block) sb.append(" ")
                append(sb, obj.get("content"))
                if (block) sb.append(" ")
            }
        }
    }

    private val WHITESPACE = Regex("\\s+")
    private val SPACE_BEFORE_SEMICOLON = Regex(" ;")

    private fun collapse(text: String): String
    {
        return text.replace(WHITESPACE, " ").replace(SPACE_BEFORE_SEMICOLON, ";").trim()
    }
}
