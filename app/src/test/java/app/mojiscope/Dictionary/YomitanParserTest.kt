package app.mojiscope.Dictionary

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class YomitanParserTest
{
    private fun zip(files: Map<String, String>): () -> java.io.InputStream
    {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            for ((name, content) in files)
            {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        val data = bytes.toByteArray()
        return { ByteArrayInputStream(data) }
    }

    private fun parse(files: Map<String, String>): Triple<DictionaryMeta, Int, List<ImportedTerm>>
    {
        val rows = ArrayList<ImportedTerm>()
        val (meta, count) = YomitanParser.parse(zip(files)) { rows.addAll(it) }
        return Triple(meta, count, rows)
    }

    @Test
    fun parsesFormat3TermBanks()
    {
        val (meta, count, rows) = parse(mapOf(
                "index.json" to """{"title":"Test","revision":"2026-10-03","format":3}""",
                "term_bank_1.json" to """[
                    ["日本語","にほんご","n","",100,["Japanese (language)","Japanese"],1000,""],
                    ["猫","ねこ","n","",50,[{"type":"structured-content","content":[{"tag":"div","content":"cat"},{"tag":"div","content":"feline"}]}],2000,""],
                    ["空","から","","",0,[],3000,""]
                ]"""))

        assertEquals("Test", meta.title)
        assertEquals("2026-10-03", meta.revision)
        assertEquals(3, meta.format)
        assertEquals(2, count)

        assertEquals("日本語", rows[0].term)
        assertEquals("にほんご", rows[0].reading)
        assertEquals("Japanese (language), Japanese", rows[0].glossary)
        assertEquals(1000L, rows[0].sequence)
        assertEquals("cat feline", rows[1].glossary)
    }

    @Test
    fun readingEqualToTermIsDropped()
    {
        val (_, _, rows) = parse(mapOf(
                "index.json" to """{"title":"Test","format":3}""",
                "term_bank_1.json" to """[["ねこ","ねこ","n","",0,["cat"],1,""]]"""))

        assertEquals("", rows[0].reading)
    }

    @Test
    fun parsesLegacyFormat1()
    {
        val (meta, _, rows) = parse(mapOf(
                "index.json" to """{"title":"Old","version":1}""",
                "term_bank_1.json" to """[["犬","いぬ","n","",1,"dog","hound"]]"""))

        assertEquals(1, meta.format)
        assertEquals("dog, hound", rows[0].glossary)
    }

    @Test
    fun parsesKanjiBanks()
    {
        val (_, _, rows) = parse(mapOf(
                "index.json" to """{"title":"Kanji","format":3}""",
                "kanji_bank_1.json" to """[["日","ニチ ジツ","ひ -び","","sun, day",{}],["月","ゲツ","つき","",["moon","month"],{}]]"""))

        // The first row has a string where meanings should be an array and is skipped
        assertEquals(1, rows.size)
        assertEquals("月", rows[0].term)
        assertEquals("ゲツ / つき", rows[0].reading)
        assertEquals("moon, month", rows[0].glossary)
    }

    @Test
    fun readsEveryBankFile()
    {
        val (_, count, _) = parse(mapOf(
                "index.json" to """{"title":"Test","format":3}""",
                "term_bank_1.json" to """[["a","","","",0,["x"],1,""]]""",
                "term_bank_2.json" to """[["b","","","",0,["y"],2,""]]""",
                "tag_bank_1.json" to """[["n","partOfSpeech",0,"noun",0]]""",
                "term_meta_bank_1.json" to """[["a","freq",5]]"""))

        assertEquals(2, count)
    }

    @Test
    fun indexMayComeAfterTheBanks()
    {
        val (meta, count, _) = parse(linkedMapOf(
                "term_bank_1.json" to """[["a","","","",0,["x"],1,""]]""",
                "index.json" to """{"title":"Late","format":3}"""))

        assertEquals("Late", meta.title)
        assertEquals(1, count)
    }

    @Test
    fun rejectsZipWithoutIndex()
    {
        try
        {
            parse(mapOf("term_bank_1.json" to "[]"))
            fail("expected an exception")
        }
        catch (e: DictionaryFormatException)
        {
            assertTrue(e.message!!.contains("index.json"))
        }
    }

    @Test
    fun rejectsDictionaryWithoutDefinitions()
    {
        try
        {
            parse(mapOf(
                    "index.json" to """{"title":"Freq","format":3}""",
                    "term_meta_bank_1.json" to """[["a","freq",5]]"""))
            fail("expected an exception")
        }
        catch (e: DictionaryFormatException)
        {
            assertTrue(e.message!!.contains("no term or kanji entries"))
        }
    }

    @Test
    fun dropsSenseNumbersFromTagsAndFormsRows()
    {
        val (_, count, rows) = parse(mapOf(
                "index.json" to """{"title":"JMdict","format":3}""",
                "term_bank_1.json" to """[
                    ["食べる","たべる","1 v1 vt","v1",0,["to eat"],1,""],
                    ["食べる","たべる","forms","",0,[{"type":"structured-content","content":"table of forms"}],1,""]
                ]"""))

        assertEquals(1, count)
        assertEquals("v1 vt", rows[0].tags)
    }

    @Test
    fun flattensStructuredContent()
    {
        val content = JsonParser.parseString("""[
            "start",
            {"tag":"ul","content":[{"tag":"li","content":"one"},{"tag":"li","content":["two ",{"tag":"span","content":"b"}]}]},
            {"tag":"img","path":"x.png"},
            {"tag":"ruby","content":["漢",{"tag":"rt","content":"かん"}]}
        ]""")

        assertEquals("start one; two b 漢", YomitanParser.flatten(content))
    }
}
