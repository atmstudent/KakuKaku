package io.github.atmstudent.kakukaku.Anki

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceExtractorTest
{
    private val text = "吾輩は猫である。名前はまだ無い。「どこで生れたか頓と見当がつかぬ。」何でも薄暗い所だ！"

    @Test
    fun theSentenceAroundAPosition()
    {
        assertEquals("吾輩は猫である。", SentenceExtractor.sentenceAt(text, 0))
        assertEquals("名前はまだ無い。", SentenceExtractor.sentenceAt(text, text.indexOf("まだ")))
        assertEquals("「どこで生れたか頓と見当がつかぬ。」", SentenceExtractor.sentenceAt(text, text.indexOf("頓")))
        assertEquals("何でも薄暗い所だ！", SentenceExtractor.sentenceAt(text, text.indexOf("薄暗")))
    }

    @Test
    fun aLineBreakEndsTheSentence()
    {
        val lines = "今日は天気がよかった\n友達と公園へ行った"
        assertEquals("友達と公園へ行った", SentenceExtractor.sentenceAt(lines, lines.indexOf("公園")))
        assertEquals("今日は天気がよかった", SentenceExtractor.sentenceAt(lines, 2))
    }

    @Test
    fun withoutAnyMarkTheWholeTextIsTheSentence()
    {
        assertEquals("日本語を読む", SentenceExtractor.sentenceAt("日本語を読む", 3))
        assertEquals("", SentenceExtractor.sentenceAt("", 0))
    }

    @Test
    fun aPositionPastTheEndIsKeptInside()
    {
        assertEquals("日本語を読む", SentenceExtractor.sentenceAt("日本語を読む", 99))
    }
}
