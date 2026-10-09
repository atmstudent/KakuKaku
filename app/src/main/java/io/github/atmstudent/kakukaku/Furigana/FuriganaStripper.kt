package io.github.atmstudent.kakukaku.Furigana

import io.github.atmstudent.kakukaku.LangUtils
import io.github.atmstudent.kakukaku.toHiragana

/**
 * Removes furigana from copied text. Browsers and e-book readers copy ruby text as the base followed
 * directly by its reading, with no marker: 漢字かんじ, 食たべる, 漢かん字じ. Text like that cannot be told apart from
 * real text by its shape, so a reading is only removed where the dictionary says it is the reading of the
 * kanji right before it. [readingsOf] gives, for each written form it knows, its readings as hiragana.
 *
 * Two shapes are recognised:
 * - the whole word, okurigana included, followed by its reading: 食べ物たべもの
 * - the kanji alone followed by their reading, the okurigana carrying on after it: 食たべる, 漢かん字じ
 */
class FuriganaStripper(private val readingsOf: (Collection<String>) -> Map<String, Set<String>>)
{
    fun strip(text: String): String
    {
        if (text.none { isKanji(it) } || text.none { isKana(it) }) return text

        val runs = kanjiRuns(text)
        val candidates = LinkedHashSet<String>()
        for ((start, end) in runs) addCandidates(text, start, end, candidates)

        val known = readingsOf(candidates)
        if (known.isEmpty()) return text

        // Removals, in text order. Where more than one reading fits a position, the shortest is removed: 行いきました
        // is 行+い+きました, not the noun 行き read いき with the rest left over.
        val removals = ArrayList<IntRange>()
        for ((start, end) in runs)
        {
            val removal = findRemoval(text, start, end, known) ?: continue
            if (removals.isEmpty() || removal.first >= removals.last().last + 1) removals.add(removal)
        }
        if (removals.isEmpty()) return text

        val result = StringBuilder()
        var position = 0
        for (range in removals)
        {
            result.append(text, position, range.first)
            position = range.last + 1
        }
        result.append(text, position, text.length)
        return result.toString()
    }

    /** The written forms whose readings are needed to decide about the kanji run [start, end) */
    private fun addCandidates(text: String, start: Int, end: Int, into: MutableSet<String>)
    {
        for (e in start + 1..minOf(text.length, start + MAX_BASE)) into.add(text.substring(start, e))

        val kana = kanaRunEnd(text, end)
        for (readingLength in 1..MAX_READING)
        {
            for (okurigana in 0..MAX_OKURIGANA)
            {
                if (end + readingLength + okurigana > kana) continue
                into.add(text.substring(start, end) + text.substring(end + readingLength, end + readingLength + okurigana))
            }
        }
    }

    private fun findRemoval(text: String, start: Int, end: Int, known: Map<String, Set<String>>): IntRange?
    {
        var best: IntRange? = null

        fun consider(range: IntRange)
        {
            if (best == null || range.last - range.first < best!!.last - best!!.first) best = range
        }

        // The base (with its okurigana) followed by its reading
        for (e in start + 1..minOf(text.length, start + MAX_BASE))
        {
            val readings = known[text.substring(start, e)] ?: continue
            for (reading in readings)
            {
                if (reading.isEmpty() || e + reading.length > text.length) continue
                if (toHiragana(text.substring(e, e + reading.length)) != reading) continue
                if (!isKana(text, e, e + reading.length)) continue
                if (e == end && lonelyParticle(text, e, e + reading.length)) continue
                consider(e until e + reading.length)
            }
        }

        // The kanji followed by their reading, and the okurigana carrying on
        val kana = kanaRunEnd(text, end)
        for (readingLength in 1..MAX_READING)
        {
            for (okurigana in 0..MAX_OKURIGANA)
            {
                val readingEnd = end + readingLength + okurigana
                if (readingEnd > kana) continue

                val word = text.substring(start, end) + text.substring(end + readingLength, readingEnd)
                val readings = known[word] ?: continue
                if (toHiragana(text.substring(end, readingEnd)) !in readings) continue
                if (okurigana == 0 && lonelyParticle(text, end, end + readingLength)) continue
                consider(end until end + readingLength)
            }
        }

        return best
    }

    /**
     * A single particle after a kanji is far more likely to be the particle than a reading: 歯は ("as for the
     * tooth") and 二に, which would otherwise lose their は and に.
     */
    private fun lonelyParticle(text: String, from: Int, to: Int): Boolean
    {
        return to - from == 1 && text[from] in PARTICLES
    }

    private fun kanjiRuns(text: String): List<Pair<Int, Int>>
    {
        val runs = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < text.length)
        {
            if (!isKanji(text[i])) { i++; continue }
            var j = i
            while (j < text.length && isKanji(text[j])) j++
            runs.add(Pair(i, j))
            i = j
        }
        return runs
    }

    private fun kanaRunEnd(text: String, from: Int): Int
    {
        var i = from
        while (i < text.length && isKana(text[i])) i++
        return i
    }

    private fun isKana(text: String, from: Int, to: Int): Boolean = (from until to).all { isKana(text[it]) }

    companion object
    {
        private const val MAX_BASE = 16
        private const val MAX_READING = 10
        private const val MAX_OKURIGANA = 4
        private val PARTICLES = "はがをにでとのもへやか".toSet()

        fun isKanji(c: Char) = LangUtils.IsKanji(c) || c == '々'

        fun isKana(c: Char) = LangUtils.IsHiragana(c) || LangUtils.IsKatakana(c) || c == 'ー'
    }
}
