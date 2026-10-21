package io.github.atmstudent.kakukaku.Anki

import org.junit.Assert.assertEquals
import org.junit.Test

class FuriganaBuilderTest
{
    private fun plain(expression: String, reading: String) = FuriganaBuilder.plain(FuriganaBuilder.segments(expression, reading))
    private fun ruby(expression: String, reading: String) = FuriganaBuilder.ruby(FuriganaBuilder.segments(expression, reading))

    @Test
    fun aWordOfKanjiOnlyGetsOneReading()
    {
        assertEquals("日本語[にほんご]", plain("日本語", "にほんご"))
        assertEquals("<ruby>日本語<rt>にほんご</rt></ruby>", ruby("日本語", "にほんご"))
    }

    @Test
    fun kanaInTheWordMarkEachKanjiPart()
    {
        assertEquals("読[よ]み 込[こ]む", plain("読み込む", "よみこむ"))
        assertEquals("<ruby>読<rt>よ</rt></ruby>み<ruby>込<rt>こ</rt></ruby>む", ruby("読み込む", "よみこむ"))
        assertEquals("食[た]べる", plain("食べる", "たべる"))
        assertEquals("お 弁当[べんとう]", plain("お弁当", "おべんとう"))
    }

    @Test
    fun katakanaReadingsMatchHiraganaInTheWord()
    {
        assertEquals("食[タ]べる", plain("食べる", "タベル"))
    }

    @Test
    fun anAmbiguousSplitReadsTheWholeWord()
    {
        // 今日 + きょう / 大人 + おとな: the reading cannot be shared out between the characters of one kanji part
        assertEquals("今日[きょう]", plain("今日", "きょう"))
        assertEquals("大人[おとな]", plain("大人", "おとな"))
        // the の of the word can match either の of the reading (はる|の|のひ or はるの|の|ひ)
        assertEquals("春の日[はるののひ]", plain("春の日", "はるののひ"))
        // when the kana can only match once the split is clear
        assertEquals("の 日[のひ]", plain("の日", "ののひ"))
    }

    @Test
    fun wordsWithoutKanjiOrReadingAreLeftAlone()
    {
        assertEquals("ねこ", plain("ねこ", "ねこ"))
        assertEquals("ネコ", plain("ネコ", "ねこ"))
        assertEquals("猫", plain("猫", ""))
    }

    @Test
    fun aReadingThatDoesNotFitReadsTheWholeWord()
    {
        assertEquals("食べる[いく]", plain("食べる", "いく"))
    }
}
