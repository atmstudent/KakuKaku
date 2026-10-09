package io.github.atmstudent.kakukaku.Ocr

import io.github.atmstudent.kakukaku.Furigana.OcrBox
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderTest
{
    @Test
    fun columnsAreReadFromRightToLeft()
    {
        // Recognised left to right: the right column must come first
        val left = OcrBox(10, 10, 50, 300)
        val middle = OcrBox(70, 10, 110, 300)
        val right = OcrBox(130, 10, 170, 300)

        assertEquals(listOf(2, 1, 0), ReadingOrder.verticalColumns(listOf(left, middle, right)))
    }

    @Test
    fun columnsOfDifferentLengthKeepTheirPlace()
    {
        val shortRight = OcrBox(130, 10, 170, 80)
        val longLeft = OcrBox(10, 10, 50, 400)

        assertEquals(listOf(0, 1), ReadingOrder.verticalColumns(listOf(shortRight, longLeft)))
        assertEquals(listOf(1, 0), ReadingOrder.verticalColumns(listOf(longLeft, shortRight)))
    }

    @Test
    fun aSingleColumnIsUnchanged()
    {
        assertEquals(listOf(0), ReadingOrder.verticalColumns(listOf(OcrBox(0, 0, 40, 200))))
        assertEquals(emptyList<Int>(), ReadingOrder.verticalColumns(emptyList()))
    }

    @Test
    fun columnsAtTheSameHeightOfEqualDistanceStartingHigherFirst()
    {
        val lower = OcrBox(100, 200, 140, 400)
        val higher = OcrBox(100, 10, 140, 190)

        assertEquals(listOf(1, 0), ReadingOrder.verticalColumns(listOf(lower, higher)))
    }
}
