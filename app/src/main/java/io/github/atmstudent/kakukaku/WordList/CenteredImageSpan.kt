package io.github.atmstudent.kakukaku.WordList

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan

/** An image in a line of text, centred on the line instead of sitting on its bottom */
class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable, ALIGN_BOTTOM)
{
    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint)
    {
        val image = drawable
        canvas.save()
        canvas.translate(x, top + (bottom - top - image.bounds.height()) / 2f)
        image.draw(canvas)
        canvas.restore()
    }
}
