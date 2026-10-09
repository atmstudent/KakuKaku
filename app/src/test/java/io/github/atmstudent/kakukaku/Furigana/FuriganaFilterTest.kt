package io.github.atmstudent.kakukaku.Furigana

import org.junit.Assert.assertEquals
import org.junit.Test

/** The boxes are what ML Kit really returned for ruby text rendered at different sizes (see the Furigana package) */
class FuriganaFilterTest
{
    private fun line(l: Int, t: Int, r: Int, b: Int, vararg symbols: Triple<String, IntArray, Unit>): OcrLineBox =
            OcrLineBox(OcrBox(l, t, r, b), symbols.map { OcrSymbolBox(it.first, OcrBox(it.second[0], it.second[1], it.second[2], it.second[3])) })

    private fun sym(text: String, l: Int, t: Int, r: Int, b: Int) = Triple(text, intArrayOf(l, t, r, b), Unit)

    private val horizontal = listOf(
            line(64, 41, 143, 66, sym("か", 64, 41, 97, 66), sym("ん", 97, 41, 125, 66), sym("じ", 125, 41, 143, 66)),
            line(254, 41, 274, 65, sym("よ", 254, 41, 274, 65)),
            line(362, 41, 494, 66, sym("れ", 362, 41, 387, 66), sym("ん", 390, 41, 414, 66), sym("し", 423, 41, 441, 66), sym("ゅ", 450, 41, 468, 66), sym("う", 477, 41, 494, 66)),
            line(585, 41, 607, 65, sym("た", 585, 41, 607, 65)),
            line(43, 78, 747, 138,
                    sym("漢", 43, 78, 101, 138), sym("字", 108, 78, 163, 138), sym("を", 174, 78, 224, 138), sym("読", 235, 78, 294, 138), sym("む", 302, 78, 355, 138),
                    sym("練", 369, 78, 427, 138), sym("習", 436, 78, 487, 138), sym("を", 506, 78, 556, 138), sym("食", 567, 78, 626, 138), sym("べ", 632, 78, 688, 138), sym("る", 700, 78, 747, 138)))

    private val vertical = listOf(
            line(116, 41, 174, 750, sym("漢", 117, 41, 174, 101), sym("字", 116, 104, 173, 164), sym("を", 116, 172, 173, 227), sym("読", 116, 232, 173, 292), sym("む", 116, 299, 173, 354),
                    sym("練", 116, 366, 173, 426), sym("習", 116, 434, 173, 491), sym("を", 116, 504, 173, 559), sym("食", 116, 564, 173, 624), sym("べ", 116, 636, 173, 681), sym("る", 116, 699, 173, 750)),
            line(183, 63, 211, 140, sym("か", 184, 63, 210, 86), sym("ん", 184, 90, 210, 113), sym("じ", 185, 118, 211, 140)),
            line(183, 360, 210, 495, sym("れ", 184, 360, 210, 398), sym("ん", 183, 397, 209, 426), sym("し", 183, 426, 209, 469), sym("う", 183, 469, 209, 495)),
            line(186, 583, 208, 607, sym("た", 186, 583, 208, 607)))

    @Test
    fun smallKanaAboveALargerLineIsFurigana()
    {
        assertEquals(setOf(0, 1, 2, 3), FuriganaFilter.furiganaLines(horizontal))
    }

    @Test
    fun smallKanaBesideVerticalTextIsFurigana()
    {
        assertEquals(setOf(1, 2, 3), FuriganaFilter.furiganaLines(vertical))
    }

    @Test
    fun aStrayMarkInTheLineDoesNotHideFurigana()
    {
        val lines = listOf(
                line(319, 57, 385, 83, sym("れ", 319, 57, 346, 83), sym("ん", 349, 57, 375, 83), sym("!", 376, 57, 385, 83)),
                line(18, 98, 368, 162, sym("美", 18, 98, 34, 162), sym("字", 42, 98, 102, 162), sym("練", 326, 98, 368, 162)))
        assertEquals(setOf(0), FuriganaFilter.furiganaLines(lines))
    }

    @Test
    fun aLineWithMostlyOtherCharactersIsNotFurigana()
    {
        val lines = listOf(
                line(319, 57, 385, 83, sym("れ", 319, 57, 346, 83), sym("!", 349, 57, 375, 83), sym("?", 376, 57, 385, 83)),
                line(18, 98, 368, 162, sym("美", 18, 98, 34, 162), sym("練", 326, 98, 368, 162)))
        assertEquals(emptySet<Int>(), FuriganaFilter.furiganaLines(lines))
    }

    @Test
    fun aLoneKanaLineIsNotFurigana()
    {
        assertEquals(emptySet<Int>(), FuriganaFilter.furiganaLines(listOf(horizontal[0])))
    }

    @Test
    fun kanaLinesOfTheSameSizeAreNotFurigana()
    {
        val lines = listOf(
                line(43, 78, 400, 138, sym("漢", 43, 78, 101, 138), sym("字", 108, 78, 163, 138)),
                line(43, 150, 400, 208, sym("あ", 43, 150, 101, 208), sym("い", 108, 150, 163, 208)))
        assertEquals(emptySet<Int>(), FuriganaFilter.furiganaLines(lines))
    }

    @Test
    fun smallKanaOverKanaOnlyTextIsNotFurigana()
    {
        val lines = listOf(
                line(64, 41, 143, 66, sym("あ", 64, 41, 97, 66)),
                line(43, 78, 747, 138, sym("あ", 43, 78, 101, 138), sym("い", 108, 78, 163, 138)))
        assertEquals(emptySet<Int>(), FuriganaFilter.furiganaLines(lines))
    }

    @Test
    fun smallKanaFarFromTheLineIsNotFurigana()
    {
        val far = horizontal.toMutableList()
        far[0] = line(64, -300, 143, -275, sym("か", 64, -300, 97, -275), sym("ん", 97, -300, 125, -275), sym("じ", 125, -300, 143, -275))
        assertEquals(setOf(1, 2, 3), FuriganaFilter.furiganaLines(far))
    }
}
