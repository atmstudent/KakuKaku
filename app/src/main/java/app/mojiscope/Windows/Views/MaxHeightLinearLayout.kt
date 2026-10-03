package app.mojiscope.Windows.Views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/** A LinearLayout that never grows taller than [maxHeightPx] (0 means no limit) */
class MaxHeightLinearLayout : LinearLayout
{
    var maxHeightPx: Int = 0
        set(value)
        {
            field = value
            requestLayout()
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
    {
        var heightSpec = heightMeasureSpec

        if (maxHeightPx > 0)
        {
            val mode = View.MeasureSpec.getMode(heightMeasureSpec)
            val size = View.MeasureSpec.getSize(heightMeasureSpec)

            if (mode == View.MeasureSpec.UNSPECIFIED || size > maxHeightPx)
            {
                heightSpec = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST)
            }
        }

        super.onMeasure(widthMeasureSpec, heightSpec)
    }
}
