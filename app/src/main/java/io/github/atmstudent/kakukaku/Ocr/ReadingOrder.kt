package io.github.atmstudent.kakukaku.Ocr

import io.github.atmstudent.kakukaku.Furigana.OcrBox

/** The order in which the lines of recognised text are read */
object ReadingOrder
{
    /**
     * Vertical text is read column by column, from the rightmost column to the leftmost. [columns] are the boxes of
     * the recognised lines (each one a column); the result is their indexes in reading order. A column that starts
     * higher comes first when two are equally far to the right.
     */
    fun verticalColumns(columns: List<OcrBox>): List<Int>
    {
        return columns.indices.sortedWith(compareByDescending<Int> { columns[it].left + columns[it].right }.thenBy { columns[it].top })
    }
}
