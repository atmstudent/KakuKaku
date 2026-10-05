package io.github.atmstudent.kakukaku.Windows.Views

import io.github.atmstudent.kakukaku.dpToPx
import android.content.Context
import android.util.AttributeSet
import io.github.atmstudent.kakukaku.Windows.Data.DisplayData
import io.github.atmstudent.kakukaku.Windows.Data.ISquareChar
import io.github.atmstudent.kakukaku.Windows.Interfaces.IRecalculateKanjiViews
import io.github.atmstudent.kakukaku.Windows.Interfaces.ISearchPerformer
import io.github.atmstudent.kakukaku.Windows.WindowCoordinator
import java.util.*


/**
 * Created by 0xbad1d3a5 on 5/5/2016.
 */
class KanjiGridView : SquareGridView, IRecalculateKanjiViews
{
    private lateinit var mWindowCoordinator: WindowCoordinator
    private lateinit var mSearchPerformer: ISearchPerformer
    private lateinit var mDisplayData: DisplayData

    private var mScrollValue: Int = 0

    private val mKanjiCellSize = squareCellSize

    init
    {
        // The highlight of a character sticks out of its cell a little (see KanjiCharacterView.onDraw)
        clipChildren = false
        contentInset = dpToPx(context, KanjiCharacterView.HIGHLIGHT_GROW_DP)
    }

    var offset: Int = 0
        private set

    val kanjiViewList: List<KanjiCharacterView>
        get()
        {
            val count = childCount
            val kanjiViewList = ArrayList<KanjiCharacterView>()

            for (i in 0 until count)
            {
                kanjiViewList.add(getChildAt(i) as KanjiCharacterView)
            }

            return kanjiViewList
        }

    constructor(context: Context) : super(context)
    {
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    {
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    {
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    {
    }

    fun setDependencies(windowCoordinator: WindowCoordinator, searchPerformer: ISearchPerformer)
    {
        mWindowCoordinator = windowCoordinator
        mSearchPerformer = searchPerformer
    }

    fun setText(displayData: DisplayData)
    {
        mDisplayData = displayData
        offset = 0

        ensureViews()
    }

    fun getText() : String
    {
        return mDisplayData.text
    }

    fun clearText()
    {
        removeAllViews()
        postInvalidate()
    }

    fun unhighlightAll(squareCharToExclude: ISquareChar)
    {
        for (k in kanjiViewList)
        {
            if (k.getSquareChar() !== squareCharToExclude) k.unhighlight()
        }
    }

    fun unhighlightAll()
    {
        for (k in kanjiViewList)
        {
            k.unhighlight()
        }
    }

    fun scrollNext()
    {
        if (offset + maxSquares < mDisplayData.count)
        {
            offset += maxSquares
            mScrollValue = maxSquares
        }

        ensureViews()
    }

    fun scrollPrev()
    {
        if (offset - mScrollValue >= 0)
        {
            offset -= mScrollValue
        }
        else
        {
            offset = 0
        }

        ensureViews()
    }

    override fun recalculateKanjiViews()
    {
        mDisplayData.recomputeChars()

        ensureViews()
    }

    private fun ensureViews()
    {
        val kanjiViews = kanjiViewList
        val numChars = mDisplayData.count - offset
        val kanjiViewSize = kanjiViews.size

        if (numChars > kanjiViewSize)
        {
            addKanjiViews(numChars - kanjiViewSize)
        } else if (numChars < kanjiViewSize)
        {
            removeKanjiViews(kanjiViewSize - numChars)
        }

        for ((index, squareChar) in mDisplayData.squareChars.subList(offset, mDisplayData.count).withIndex())
        {
            val kanjiView = getChildAt(index) as KanjiCharacterView
            kanjiView.setText(squareChar)
        }

        setItemCount(numChars)
        unhighlightAll()
        postInvalidate()
    }

    private fun addKanjiViews(count: Int)
    {
        for (i in 0 until count)
        {
            val kanjiView = KanjiCharacterView(context)
            kanjiView.setDependencies(mWindowCoordinator, mSearchPerformer)
            kanjiView.setCellSize(mKanjiCellSize)

            addView(kanjiView)
        }
    }

    private fun removeKanjiViews(count: Int)
    {
        val childCount = childCount
        for (i in childCount downTo childCount - count + 1)
        {
            removeViewAt(i - 1)
        }
    }

    companion object
    {
        private val TAG = KanjiGridView::class.java.name
    }
}
