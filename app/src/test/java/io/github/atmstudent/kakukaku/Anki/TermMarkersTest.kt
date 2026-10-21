package io.github.atmstudent.kakukaku.Anki

import org.junit.Assert.assertEquals
import org.junit.Test

class TermMarkersTest
{
    private val context = TermContext(
            sentence = "友達と食べた。",
            expression = "食べる",
            reading = "たべる",
            writing = "食べる",
            clozeBody = "食べた",
            glossary = listOf(Pair("v1,vt", "to eat"), Pair("", "to live on <food>")),
            pitch = "[2][0]")

    @Test
    fun everyMarkerHasAValue()
    {
        val values = context.values()
        assertEquals(Marker.values().map { it.id }.toSet(), values.keys)
        assertEquals("友達と食べた。", values["sentence"])
        assertEquals("食べる", values["expression"])
        assertEquals("たべる", values["reading"])
        assertEquals("食べた", values["cloze-body"])
        assertEquals("食[た]べる", values["furigana-plain"])
        assertEquals("<ruby>食<rt>た</rt></ruby>べる", values["furigana"])
        assertEquals("2, 0", values["pitch-accent-positions"])
        assertEquals("<ol><li>(v1,vt) to eat</li><li>to live on &lt;food&gt;</li></ol>", values["glossary"])
    }

    @Test
    fun theWrittenFormFollowsTheText()
    {
        // The text had 喰べた, the dictionary form is written 食べる
        val variant = context.copy(writing = "喰べる").values()
        assertEquals("喰べる", variant["writing"])
        assertEquals("喰[た]べる", variant["writing-furigana-plain"])
        assertEquals("<ruby>喰<rt>た</rt></ruby>べる", variant["writing-furigana"])

        // The text had it in kana
        val kana = context.copy(writing = "たべる").values()
        assertEquals("たべる", kana["writing"])
        assertEquals("たべる", kana["writing-furigana-plain"])
    }

    @Test
    fun withoutPitchTheMarkersAreEmpty()
    {
        val values = context.copy(pitch = "", glossary = emptyList()).values()
        assertEquals("", values["pitch-accent-positions"])
        assertEquals("", values["glossary"])
    }

    @Test
    fun templatesAreFilledAndUnknownMarkersStay()
    {
        assertEquals("食べる[たべる] - 友達と食べた。", TemplateFiller.fill("{expression}[{reading}] - {sentence}", context.values()))
        assertEquals("{nothing} 食べる", TemplateFiller.fill("{nothing} {expression}", context.values()))
        assertEquals("plain text", TemplateFiller.fill("plain text", context.values()))
    }
}
