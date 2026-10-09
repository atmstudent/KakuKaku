package io.github.atmstudent.kakukaku.Furigana

import io.github.atmstudent.kakukaku.LangUtils

data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int)
{
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    /** The size of the characters in a line: the box is as thick as they are tall, whichever way the text runs */
    val thickness: Int get() = minOf(width, height)
}

data class OcrSymbolBox(val text: String, val box: OcrBox)

data class OcrLineBox(val box: OcrBox, val symbols: List<OcrSymbolBox>)
{
    val text: String get() = symbols.joinToString("") { it.text }
}

/**
 * Finds the furigana among the lines ML Kit recognised. It reads each ruby unit as a line of its own: a few small
 * kana above (or, in vertical text, to the right of) a larger line. A line counts as furigana when
 * - it is kana (and no kanji),
 * - its characters are much smaller than those of a neighbouring line (at most [MAX_RATIO] of their size),
 * - that line sits right next to it on the side furigana is written on and runs the same way, and
 * - the part of that line the furigana stands over has a kanji in it.
 */
object FuriganaFilter
{
    private const val MAX_RATIO = 0.7
    private const val MAX_GAP = 0.8
    private const val MIN_OVERLAP = 0.5
    private const val MIN_KANA = 0.6

    /** The indexes of the lines in [lines] that are furigana */
    fun furiganaLines(lines: List<OcrLineBox>): Set<Int>
    {
        val result = HashSet<Int>()

        for ((index, line) in lines.withIndex())
        {
            if (!looksLikeFurigana(line.text)) continue

            if (lines.withIndex().any { (other, base) -> other != index && isFuriganaOf(line, base) }) result.add(index)
        }

        return result
    }

    /**
     * Kana, and no kanji. Where the capture box cuts through a character, ML Kit often adds a stray mark to the
     * line ("れん!"), so a little of something else is allowed.
     */
    private fun looksLikeFurigana(text: String): Boolean
    {
        if (text.isEmpty() || text.any { FuriganaStripper.isKanji(it) }) return false
        return text.count { FuriganaStripper.isKana(it) } >= text.length * MIN_KANA
    }

    private fun isFuriganaOf(furigana: OcrLineBox, base: OcrLineBox): Boolean
    {
        val f = furigana.box
        val b = base.box
        if (f.thickness <= 0 || f.thickness > b.thickness * MAX_RATIO) return false
        if (base.symbols.none { FuriganaStripper.isKanji(it.text.firstOrNull() ?: ' ') }) return false

        val maxGap = b.thickness * MAX_GAP
        val slack = b.thickness * 0.3

        // Horizontal text: furigana above the line, standing over part of it
        if (overlap(f.left, f.right, b.left, b.right) >= f.width * MIN_OVERLAP && (b.top - f.bottom).toDouble() in -slack..maxGap)
        {
            return base.symbols.any { overlap(f.left, f.right, it.box.left, it.box.right) > 0 && isKanjiSymbol(it) }
        }

        // Vertical text: furigana to the right of the line
        if (overlap(f.top, f.bottom, b.top, b.bottom) >= f.height * MIN_OVERLAP && (f.left - b.right).toDouble() in -slack..maxGap)
        {
            return base.symbols.any { overlap(f.top, f.bottom, it.box.top, it.box.bottom) > 0 && isKanjiSymbol(it) }
        }

        return false
    }

    private fun isKanjiSymbol(symbol: OcrSymbolBox) = symbol.text.isNotEmpty() && symbol.text.all { FuriganaStripper.isKanji(it) }

    private fun overlap(a1: Int, a2: Int, b1: Int, b2: Int): Int = minOf(a2, b2) - maxOf(a1, b1)
}
