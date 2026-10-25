package io.github.atmstudent.kakukaku.WordList

import org.junit.Assert.assertEquals
import org.junit.Test

class WordListDisplayTest
{
    private val verb = mapOf(
            "expression" to "食べる", "reading" to "たべる",
            "glossary" to "<ol><li>(v1,vt) to eat</li><li>to live on &lt;food&gt;</li></ol>",
            "pitch-accent-positions" to "2, 0")

    @Test
    fun theHeadingIsLikeThePopups()
    {
        assertEquals("食べる (たべる) [2][0]", WordListDisplay.heading(verb, true))
        assertEquals("食べる (たべる)", WordListDisplay.heading(verb, false))
        assertEquals("ねこ", WordListDisplay.heading(mapOf("expression" to "ねこ", "reading" to "ねこ"), true))
        assertEquals("猫 (ねこ)", WordListDisplay.heading(mapOf("expression" to "猫", "reading" to "ねこ", "pitch-accent-positions" to ""), true))
    }

    @Test
    fun theGlossaryHasNumberedMeanings()
    {
        assertEquals("① (v1,vt) to eat ② to live on <food>", WordListDisplay.glossary(verb))
        assertEquals("", WordListDisplay.glossary(emptyMap()))
        assertEquals("plain text", WordListDisplay.glossary(mapOf("glossary" to "plain text")))
    }
}
