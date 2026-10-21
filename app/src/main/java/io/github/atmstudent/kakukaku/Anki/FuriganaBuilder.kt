package io.github.atmstudent.kakukaku.Anki

import io.github.atmstudent.kakukaku.toHiragana

/** A part of a written word: [text] with its [reading] when it is kanji (null for kana, which reads as it is written) */
data class FuriganaSegment(val text: String, val reading: String?)

/**
 * Puts the reading of a word over its kanji: 日本語 + にほんご is one segment, 読み込む + よみこむ is 読(よ) み 込(こ) む.
 * The kana of the word must appear as they are in the reading and mark where each kanji part ends. When there is more
 * than one way to share the reading out (or none), the whole word gets the whole reading, as Yomitan does.
 */
object FuriganaBuilder
{
    fun segments(expression: String, reading: String): List<FuriganaSegment>
    {
        if (expression.isEmpty()) return emptyList()
        if (reading.isEmpty() || expression.none { isKanji(it) } || toHiragana(reading) == toHiragana(expression))
        {
            return listOf(FuriganaSegment(expression, null))
        }

        val runs = splitRuns(expression)
        val matches = ArrayList<List<FuriganaSegment>>()
        match(runs, 0, reading, 0, ArrayList(), matches)

        return if (matches.size == 1) matches[0] else listOf(FuriganaSegment(expression, reading))
    }

    /** HTML ruby, as Yomitan's {furigana}: <ruby>日本語<rt>にほんご</rt></ruby> */
    fun ruby(segments: List<FuriganaSegment>): String
    {
        return segments.joinToString("") { segment ->
            if (segment.reading == null) escape(segment.text) else "<ruby>${escape(segment.text)}<rt>${escape(segment.reading)}</rt></ruby>"
        }
    }

    /** Anki's bracket form: 日本語[にほんご]; a space starts each kanji part after kana, as Anki needs: 読[よ]み 込[こ]む */
    fun plain(segments: List<FuriganaSegment>): String
    {
        val sb = StringBuilder()
        for (segment in segments)
        {
            if (segment.reading == null)
            {
                sb.append(segment.text)
            }
            else
            {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(segment.text).append('[').append(segment.reading).append(']')
            }
        }
        return sb.toString()
    }

    private class Run(val text: String, val kanji: Boolean)

    private fun splitRuns(expression: String): List<Run>
    {
        val runs = ArrayList<Run>()
        var start = 0
        for (i in 1..expression.length)
        {
            if (i == expression.length || isKanji(expression[i]) != isKanji(expression[start]))
            {
                runs.add(Run(expression.substring(start, i), isKanji(expression[start])))
                start = i
            }
        }
        return runs
    }

    /** Collects every way to read the runs from [reading] (from [position] on); stops looking after two */
    private fun match(runs: List<Run>, runIndex: Int, reading: String, position: Int, parts: ArrayList<FuriganaSegment>, found: ArrayList<List<FuriganaSegment>>)
    {
        if (found.size > 1) return

        if (runIndex == runs.size)
        {
            if (position == reading.length) found.add(ArrayList(parts))
            return
        }

        val run = runs[runIndex]
        val rest = reading.length - position

        if (!run.kanji)
        {
            val literal = run.text
            if (rest >= literal.length && toHiragana(reading.substring(position, position + literal.length)) == toHiragana(literal))
            {
                parts.add(FuriganaSegment(literal, null))
                match(runs, runIndex + 1, reading, position + literal.length, parts, found)
                parts.removeAt(parts.size - 1)
            }
            return
        }

        // Every kanji part reads at least one kana
        for (length in 1..rest)
        {
            parts.add(FuriganaSegment(run.text, reading.substring(position, position + length)))
            match(runs, runIndex + 1, reading, position + length, parts, found)
            parts.removeAt(parts.size - 1)
        }
    }

    fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿' || c == '々' || c == '〇' || c in '豈'..'﫿'

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
