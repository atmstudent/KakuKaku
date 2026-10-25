package io.github.atmstudent.kakukaku.WordList

import android.text.Selection
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView

/**
 * Selectable text whose [ClickableSpan]s can be tapped. (Selection needs [ArrowKeyMovementMethod], which does not
 * follow links.) A tap is a short touch that stays in place: a long press that starts a selection, or a drag that
 * moves the selection handles, is not a tap. Where spans overlap, the shortest one gets the tap.
 */
class TappableSelectionMethod : ArrowKeyMovementMethod()
{
    private var mDownX = 0f
    private var mDownY = 0f
    private var mDownTime = 0L
    private var mMoved = false

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean
    {
        when (event.actionMasked)
        {
            MotionEvent.ACTION_DOWN ->
            {
                mDownX = event.x
                mDownY = event.y
                mDownTime = event.eventTime
                mMoved = false
            }
            MotionEvent.ACTION_MOVE ->
            {
                val slop = ViewConfiguration.get(widget.context).scaledTouchSlop
                if (Math.abs(event.x - mDownX) > slop || Math.abs(event.y - mDownY) > slop) mMoved = true
            }
            MotionEvent.ACTION_UP ->
            {
                val isTap = !mMoved && event.eventTime - mDownTime < ViewConfiguration.getLongPressTimeout()
                if (isTap && Selection.getSelectionStart(buffer) == Selection.getSelectionEnd(buffer))
                {
                    val icon = rightEdgeIconAt(widget, buffer, event)
                    if (icon != null)
                    {
                        icon.onClick()
                        return true
                    }

                    val span = spanAt(widget, buffer, event)
                    if (span != null)
                    {
                        span.onClick(widget)
                        return true
                    }
                }
            }
        }

        return super.onTouchEvent(widget, buffer, event)
    }

    /** The icon drawn at the right edge of the line that was touched, if the touch is on it (with a little room around it) */
    private fun rightEdgeIconAt(widget: TextView, buffer: Spannable, event: MotionEvent): RightEdgeIconSpan?
    {
        val layout = widget.layout ?: return null
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        val room = 12 * widget.resources.displayMetrics.density

        for (span in buffer.getSpans(0, buffer.length, RightEdgeIconSpan::class.java))
        {
            val line = layout.getLineForOffset(buffer.getSpanStart(span))
            if (y >= layout.getLineTop(line) && y <= layout.getLineBottom(line) && x >= span.lineWidth() - span.width - room) return span
        }
        return null
    }

    private fun spanAt(widget: TextView, buffer: Spannable, event: MotionEvent): ClickableSpan?
    {
        val layout = widget.layout ?: return null
        val x = (event.x - widget.totalPaddingLeft + widget.scrollX).toInt()
        val y = (event.y - widget.totalPaddingTop + widget.scrollY).toInt()
        val line = layout.getLineForVertical(y)

        // Past the end of a line is not on its text
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return null

        val offset = layout.getOffsetForHorizontal(line, x.toFloat())
        return buffer.getSpans(offset, offset, ClickableSpan::class.java)
                .filter { buffer.getSpanStart(it) <= offset && offset < buffer.getSpanEnd(it) }
                .minByOrNull { buffer.getSpanEnd(it) - buffer.getSpanStart(it) }
    }
}
