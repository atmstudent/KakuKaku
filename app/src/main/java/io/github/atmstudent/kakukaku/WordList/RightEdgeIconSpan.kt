package io.github.atmstudent.kakukaku.WordList

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan
import android.widget.TextView

/**
 * A tappable icon that is drawn at the right edge of the line it is on, wherever the text before it ends. It takes
 * up its width in the line, so the text before it does not run under it. Taps are found by [TappableSelectionMethod].
 */
class RightEdgeIconSpan(private val icon: Drawable, private val textView: TextView, val onClick: () -> Unit) : ReplacementSpan()
{
    val width: Int get() = icon.bounds.width()

    /** The width of the lines of the text */
    fun lineWidth(): Int = textView.width - textView.totalPaddingLeft - textView.totalPaddingRight

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = width

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint)
    {
        canvas.save()
        canvas.translate((lineWidth() - width).toFloat(), top + (bottom - top - icon.bounds.height()) / 2f)
        icon.draw(canvas)
        canvas.restore()
    }
}
