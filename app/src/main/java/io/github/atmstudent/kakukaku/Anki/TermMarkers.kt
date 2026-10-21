package io.github.atmstudent.kakukaku.Anki

import io.github.atmstudent.kakukaku.DB_KANJIDICT_NAME
import io.github.atmstudent.kakukaku.DB_SPLIT_CHAR
import io.github.atmstudent.kakukaku.Search.JmSearchResult

/** The pieces of information about a looked-up term that can go on a card or into the saved list, as {id} in a template */
enum class Marker(val id: String)
{
    SENTENCE("sentence"),
    EXPRESSION("expression"),
    READING("reading"),
    FURIGANA("furigana"),
    FURIGANA_PLAIN("furigana-plain"),
    CLOZE_BODY("cloze-body"),
    GLOSSARY("glossary"),
    PITCH_ACCENTS("pitch-accents"),
    PITCH_ACCENT_POSITIONS("pitch-accent-positions"),
    WRITING("writing"),
    WRITING_FURIGANA("writing-furigana"),
    WRITING_FURIGANA_PLAIN("writing-furigana-plain");

    companion object
    {
        fun byId(id: String): Marker? = values().firstOrNull { it.id == id }
    }
}

/** What is known about one looked-up term; [values] turns it into the text of every marker */
data class TermContext(
        /** The sentence of the source text the term is in */
        val sentence: String,
        /** The dictionary form as written in the dictionary */
        val expression: String,
        /** The reading of the term (kana) */
        val reading: String,
        /** The term deinflected, written as it appeared in the text */
        val writing: String,
        /** The text that was matched, inflected as it appeared */
        val clozeBody: String,
        /** The meanings, each with its part of speech (may be empty) */
        val glossary: List<Pair<String, String>>,
        /** Pitch accent as shown in the popup, such as "[1][0]"; empty if none */
        val pitch: String)
{
    fun values(): Map<String, String>
    {
        val expressionSegments = FuriganaBuilder.segments(expression, reading)
        val writingSegments = FuriganaBuilder.segments(writing, reading)

        return mapOf(
                Marker.SENTENCE.id to sentence,
                Marker.EXPRESSION.id to expression,
                Marker.READING.id to reading,
                Marker.FURIGANA.id to FuriganaBuilder.ruby(expressionSegments),
                Marker.FURIGANA_PLAIN.id to FuriganaBuilder.plain(expressionSegments),
                Marker.CLOZE_BODY.id to clozeBody,
                Marker.GLOSSARY.id to glossaryHtml(),
                Marker.PITCH_ACCENTS.id to pitch,
                Marker.PITCH_ACCENT_POSITIONS.id to pitchPositions(pitch),
                Marker.WRITING.id to writing,
                Marker.WRITING_FURIGANA.id to FuriganaBuilder.ruby(writingSegments),
                Marker.WRITING_FURIGANA_PLAIN.id to FuriganaBuilder.plain(writingSegments))
    }

    private fun glossaryHtml(): String
    {
        if (glossary.isEmpty()) return ""
        return glossary.joinToString("", "<ol>", "</ol>") { (pos, meaning) ->
            "<li>" + (if (pos.isEmpty()) "" else "(${escape(pos)}) ") + escape(meaning) + "</li>"
        }
    }

    companion object
    {
        /** The positions of "[1][0]" as "1, 0" */
        fun pitchPositions(pitch: String): String = Regex("\\[(\\d+)\\]").findAll(pitch).joinToString(", ") { it.groupValues[1] }

        private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        /** The context of [result] found at [matchOffset] in [sourceText] (the text of the popup) */
        fun from(result: JmSearchResult, sourceText: String, matchOffset: Int): TermContext
        {
            val entry = result.entry
            val kanjiEntry = DB_KANJIDICT_NAME == entry.dictionary

            val expression = entry.kanji ?: ""
            val readings = entry.readings.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            // Kana-only words have no separate reading; kanji entries list on and kun readings, kept as they are
            val reading = if (kanjiEntry) entry.readings else readings.firstOrNull() ?: expression

            val meanings = (entry.meanings ?: "").split(DB_SPLIT_CHAR)
            val partsOfSpeech = entry.pos.split(DB_SPLIT_CHAR)
            val glossary = meanings.withIndex().filter { it.value.isNotEmpty() }
                    .map { (i, meaning) -> Pair(if (kanjiEntry) "" else partsOfSpeech.getOrElse(i) { "" }, meaning) }

            return TermContext(
                    SentenceExtractor.sentenceAt(sourceText, matchOffset),
                    expression,
                    reading,
                    result.deinfInfo.word.ifEmpty { expression },
                    result.word,
                    glossary,
                    result.pitch)
        }
    }
}

/** Fills a template such as "{expression}[{reading}]" with the values of the markers; unknown {names} stay as typed */
object TemplateFiller
{
    private val MARKER = Regex("\\{([a-z-]+)\\}")

    fun fill(template: String, values: Map<String, String>): String = MARKER.replace(template) { match -> values[match.groupValues[1]] ?: match.value }
}
