package io.github.atmstudent.kakukaku.WordList

import android.text.StaticLayout
import android.text.TextPaint

/** Cuts a text that is more than two lines long at the end of its second line */
object TwoLineCut
{
    /**
     * The start of [text] that fits two lines of [width] pixels in [paint], with room on the second line for an
     * ellipsis and [reserve] more pixels (an arrow); null if the whole text fits two lines.
     */
    fun cut(text: String, paint: TextPaint, width: Int, reserve: Int): String?
    {
        if (text.isEmpty() || width <= 0) return null

        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
        if (layout.lineCount <= 2) return null

        val secondLineStart = layout.getLineStart(1)
        var cut = text.substring(0, layout.getLineEnd(1)).trimEnd()
        val ellipsis = paint.measureText("… ")
        while (cut.length > secondLineStart && paint.measureText(text, secondLineStart, cut.length) + ellipsis + reserve > width)
        {
            cut = cut.substring(0, cut.length - 1).trimEnd()
        }
        return cut
    }
}
