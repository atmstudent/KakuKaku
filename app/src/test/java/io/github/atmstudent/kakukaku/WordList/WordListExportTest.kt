package io.github.atmstudent.kakukaku.WordList

import io.github.atmstudent.kakukaku.Anki.Marker
import io.github.atmstudent.kakukaku.WordList.HtmlText
import io.github.atmstudent.kakukaku.WordList.SavedTerm
import io.github.atmstudent.kakukaku.WordList.WordListExport
import org.junit.Assert.assertEquals
import org.junit.Test

class WordListExportTest
{
    private val terms = listOf(
            SavedTerm(2, 0, mapOf("expression" to "食べる", "reading" to "たべる", "glossary" to "<ol><li>(v1) to eat</li><li>to live on</li></ol>", "furigana" to "<ruby>食<rt>た</rt></ruby>べる")),
            SavedTerm(1, 0, mapOf("expression" to "猫", "reading" to "ねこ", "glossary" to "<ol><li>(n) cat, with \"quotes\"</li></ol>")))

    @Test
    fun theTableHasAHeaderAndTheChosenMarkers()
    {
        val table = WordListExport.table(terms, listOf(Marker.EXPRESSION, Marker.READING), false)
        assertEquals(listOf(listOf("expression", "reading"), listOf("食べる", "たべる"), listOf("猫", "ねこ")), table)
    }

    @Test
    fun htmlBecomesText()
    {
        assertEquals("1. (v1) to eat; 2. to live on", HtmlText.plain("<ol><li>(v1) to eat</li><li>to live on</li></ol>"))
        assertEquals("食[た]べる", HtmlText.plain("<ruby>食<rt>た</rt></ruby>べる"))
        assertEquals("a < b", HtmlText.plain("a &lt; b"))
        assertEquals("", HtmlText.plain(""))
    }

    @Test
    fun tsvAndCsvAreEscaped()
    {
        val table = WordListExport.table(terms, listOf(Marker.EXPRESSION, Marker.GLOSSARY), true)
        assertEquals("expression\tglossary\n食べる\t1. (v1) to eat; 2. to live on\n猫\t1. (n) cat, with \"quotes\"\n", WordListExport.tsv(table))
        assertEquals("expression,glossary\r\n食べる,1. (v1) to eat; 2. to live on\r\n猫,\"1. (n) cat, with \"\"quotes\"\"\"\r\n", WordListExport.csv(table))
    }
}
