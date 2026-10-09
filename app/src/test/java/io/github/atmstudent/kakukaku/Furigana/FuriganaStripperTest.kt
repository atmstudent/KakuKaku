package io.github.atmstudent.kakukaku.Furigana

import org.junit.Assert.assertEquals
import org.junit.Test

class FuriganaStripperTest
{
    private val dictionary = mapOf(
            "漢字" to setOf("かんじ"),
            "食べる" to setOf("たべる"),
            "食べ物" to setOf("たべもの"),
            "練習" to setOf("れんしゅう"),
            "読む" to setOf("よむ"),
            "読" to setOf("どく", "よ"),
            "漢" to setOf("かん"),
            "字" to setOf("じ", "あざ"),
            "食" to setOf("しょく", "く", "た"),
            "行" to setOf("ぎょう", "い", "いき"),
            "行き" to setOf("いき"),
            "歯" to setOf("は", "し"),
            "二" to setOf("に", "じ"),
            "手" to setOf("て", "しゅ"),
            "日本語" to setOf("にほんご"))

    private val stripper = FuriganaStripper { forms -> dictionary.filterKeys { it in forms } }

    private fun strip(text: String) = stripper.strip(text)

    @Test
    fun readingAfterTheWord()
    {
        assertEquals("漢字を読む", strip("漢字かんじを読む"))
        assertEquals("日本語を練習", strip("日本語にほんごを練習れんしゅう"))
    }

    @Test
    fun readingAfterTheWholeWordWithOkurigana()
    {
        assertEquals("食べ物を食べる", strip("食べ物たべものを食べる"))
    }

    @Test
    fun readingOfTheKanjiOnlyBeforeOkurigana()
    {
        assertEquals("食べる", strip("食たべる"))
        assertEquals("読む", strip("読よむ"))
    }

    @Test
    fun theShorterReadingIsRemovedWhenTwoFit()
    {
        assertEquals("行きました", strip("行いきました"))
    }

    @Test
    fun readingOfEachKanji()
    {
        assertEquals("漢字", strip("漢かん字じ"))
    }

    @Test
    fun ordinaryTextStaysAsItIs()
    {
        assertEquals("漢字を読む", strip("漢字を読む"))
        assertEquals("食べる", strip("食べる"))
        assertEquals("ひらがなだけ", strip("ひらがなだけ"))
        assertEquals("漢字", strip("漢字"))
    }

    @Test
    fun aParticleAfterAKanjiIsNotAReading()
    {
        assertEquals("歯は痛い", strip("歯は痛い"))
        assertEquals("二にある", strip("二にある"))
    }

    @Test
    fun aKanjiWithoutAMatchingReadingIsLeftAlone()
    {
        assertEquals("手をあげる", strip("手をあげる"))
        assertEquals("日本語のつもり", strip("日本語のつもり"))
    }

    @Test
    fun readingsAreMatchedWhateverTheKana()
    {
        assertEquals("漢字", strip("漢字カンジ"))
    }

    @Test
    fun kanjiDictionaryRows()
    {
        assertEquals(listOf("しょく", "じき", "く", "た", "は"), Furigana.kanjiReadings("(ショク, ジキ) [く.う, た.べる, -は.む]"))
        assertEquals(listOf("かん"), Furigana.kanjiReadings("(カン) []"))
    }
}
