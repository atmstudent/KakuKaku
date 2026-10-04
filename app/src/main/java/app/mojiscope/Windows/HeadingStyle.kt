package app.mojiscope.Windows

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import app.mojiscope.Windows.Views.KanjiCharacterView

/** Styling of the first line of a definition: the word and its reading, with the pitch accent beside them */
object HeadingStyle
{
    private const val PITCH_SCALE = 0.6f

    /**
     * Styles the heading that starts at [headingStart] and runs to the end of [sb]. The heading is as large as the
     * characters above; the pitch accent (from [pitchStart] to [pitchEnd], or -1 for none) is smaller, bold, and
     * level with the top of the line.
     */
    @JvmStatic
    fun apply(context: Context, sb: SpannableStringBuilder, headingStart: Int, pitchStart: Int, pitchEnd: Int)
    {
        val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        fun large(from: Int, to: Int)
        {
            if (to > from) sb.setSpan(AbsoluteSizeSpan(KanjiCharacterView.CHARACTER_TEXT_SIZE_DP, true), from, to, flags)
        }

        if (pitchStart < 0)
        {
            large(headingStart, sb.length)
            return
        }

        large(headingStart, pitchStart)
        large(pitchEnd, sb.length)

        val largePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, KanjiCharacterView.CHARACTER_TEXT_SIZE_DP.toFloat(), context.resources.displayMetrics)
        sb.setSpan(TopAlignedSmallSpan(largePx, PITCH_SCALE), pitchStart, pitchEnd, flags)
        sb.setSpan(StyleSpan(Typeface.BOLD), pitchStart, pitchEnd, flags)
    }

    /** Draws text at [scale] times [largePx], moved up so that its top is level with the top of text of size [largePx] */
    private class TopAlignedSmallSpan(private val largePx: Float, private val scale: Float) : MetricAffectingSpan()
    {
        override fun updateDrawState(tp: TextPaint) = adjust(tp)

        override fun updateMeasureState(tp: TextPaint) = adjust(tp)

        private fun adjust(tp: TextPaint)
        {
            val reference = TextPaint(tp)
            reference.textSize = largePx
            tp.textSize = largePx * scale
            // ascent() is negative, so this moves the text up
            tp.baselineShift += (reference.ascent() * (1f - scale)).toInt()
        }
    }
}
