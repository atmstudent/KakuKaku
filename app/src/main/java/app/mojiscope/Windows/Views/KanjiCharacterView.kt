package app.mojiscope.Windows.Views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import app.mojiscope.*
import app.mojiscope.Ocr.BoxParams
import app.mojiscope.Windows.*
import app.mojiscope.Windows.Data.DisplayDataOcr
import app.mojiscope.Windows.Data.ISquareChar
import app.mojiscope.Windows.Interfaces.ICopyText
import app.mojiscope.Windows.Interfaces.IRecalculateKanjiViews
import app.mojiscope.Windows.Interfaces.ISearchPerformer

/**
 * Created by 0xbad1d3a5 on 5/5/2016.
 */
class KanjiCharacterView : FrameLayout, GestureDetector.OnGestureListener, IRecalculateKanjiViews
{
    private lateinit var mContext: Context
    private lateinit var mGestureDetector: GestureDetector
    private lateinit var mWindowCoordinator: WindowCoordinator
    private lateinit var mSearchPerformer: ISearchPerformer
    private lateinit var mKanjiChoiceWindow: KanjiChoiceWindow
    private lateinit var mEditWindow: EditWindow
    private lateinit var mSquareChar: ISquareChar

    private lateinit var mKanjiTextView: TextView
    private lateinit var mIconImageView: ImageView

    private var mCellSizePx: Int = 0
    private var mScrollStartEvent: MotionEvent? = null

    constructor(context: Context) : super(context)
    {
        Init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    {
        Init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    {
        Init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    {
        Init(context)
    }

    private fun Init(context: Context)
    {
        mContext = context
        mGestureDetector = GestureDetector(mContext, this)

        mKanjiTextView = TextView(mContext)
        mKanjiTextView.gravity = Gravity.CENTER
        // Font padding pushes the glyph toward the bottom of the cell; drop it so the glyph is centered
        mKanjiTextView.includeFontPadding = false
        mKanjiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20.toFloat())
        mKanjiTextView.setTextColor(Color.BLACK)

        addView(mKanjiTextView)

        mIconImageView = ImageView(mContext)
        mIconImageView.visibility = INVISIBLE
        addView(mIconImageView)
    }

    fun getSquareChar(): ISquareChar
    {
        return mSquareChar
    }

    fun setDependencies(windowCoordinator: WindowCoordinator, searchPerformer: ISearchPerformer)
    {
        mWindowCoordinator = windowCoordinator
        mSearchPerformer = searchPerformer

        mKanjiChoiceWindow = mWindowCoordinator.getWindowOfType(WINDOW_KANJI_CHOICE)
        mEditWindow = mWindowCoordinator.getWindowOfType(WINDOW_EDIT)
    }

    fun setText(squareChar: ISquareChar)
    {
        mSquareChar = squareChar
        mKanjiTextView.text = squareChar.char
    }

    fun setCellSize(px: Int)
    {
        mCellSizePx = dpToPx(context, pxToDp(context, px) - 2)
    }

    fun highlight()
    {
        background = mContext.getDrawable(R.drawable.bg_translucent_border_0_blue_blue)
    }

    fun highlightLight()
    {
        background = mContext.getDrawable(R.drawable.bg_transparent_border_0_nil_default)
    }

    fun unhighlight()
    {
        background = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)
    {
        val cellWidthSpec = View.MeasureSpec.makeMeasureSpec(mCellSizePx, View.MeasureSpec.EXACTLY)
        val cellHeightSpec = View.MeasureSpec.makeMeasureSpec(mCellSizePx, View.MeasureSpec.EXACTLY)

        for (i in 0 until childCount)
        {
            val child = getChildAt(i)
            if (child === mKanjiTextView)
            {
                // CJK fonts have a line height taller than the cell; let the text keep its natural
                // height (it would otherwise be pinned to the top and look low) and position it in onLayout
                child.measure(cellWidthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            }
            else
            {
                child.measure(cellWidthSpec, cellHeightSpec)
            }
        }

        setMeasuredDimension(mCellSizePx, mCellSizePx)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int)
    {
        super.onLayout(changed, left, top, right, bottom)

        // Put the middle of a full-width glyph (measured from a reference kanji) in the middle of the cell
        val paint = mKanjiTextView.paint
        val bounds = android.graphics.Rect()
        paint.getTextBounds("国", 0, 1, bounds)
        // The laid-out baseline accounts for the fallback CJK font actually used for the glyph
        val inkCenterFromTop = mKanjiTextView.baseline + (bounds.top + bounds.bottom) / 2f
        val textTop = Math.round((bottom - top) / 2f - inkCenterFromTop)
        mKanjiTextView.layout(0, textTop, mKanjiTextView.measuredWidth, textTop + mKanjiTextView.measuredHeight)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean
    {
        mGestureDetector.onTouchEvent(e)

        if (e.action == MotionEvent.ACTION_UP)
        {
            visibility = View.VISIBLE

            if (mScrollStartEvent != null)
            {
                mScrollStartEvent = null

                mKanjiTextView.visibility = View.VISIBLE
                mIconImageView.visibility = View.INVISIBLE

                val choiceResult = mKanjiChoiceWindow.onSquareScrollEnd(e)
                when (choiceResult.first)
                {
                    ChoiceResultType.SWAP ->
                    {
                        mKanjiTextView.text = choiceResult.second
                        mSquareChar.text = choiceResult.second
                        recalculateKanjiViews()
                    }
                    ChoiceResultType.EDIT ->
                    {
                        val window = getProperWindow<Window>()
                        if (mSquareChar.displayData is DisplayDataOcr)
                        {
                            window.hide()
                        }

                        mEditWindow.setInfo(mSquareChar)
                        mEditWindow.setInputDoneCallback(this)
                        mEditWindow.show()
                    }
                    ChoiceResultType.DELETE ->
                    {
                        mSquareChar.text = ""
                        recalculateKanjiViews()
                    }
                    ChoiceResultType.NONE ->
                    {
                        // Do nothing
                    }
                }
            }
        }

        return true
    }

    override fun recalculateKanjiViews()
    {
        val cwindow = getProperWindow<IRecalculateKanjiViews>()
        cwindow.recalculateKanjiViews()

        val window = getProperWindow<Window>()
        window.show()
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean
    {
        highlightLight()
        mSquareChar.userTouched = true
        mSearchPerformer.performSearch(mSquareChar)
        return true
    }

    override fun onScroll(motionEvent: MotionEvent?, motionEvent1: MotionEvent, v: Float, v1: Float): Boolean
    {
        // scroll event start
        if (mScrollStartEvent == null)
        {
            Log.d(TAG, "ScrollStart")
            mScrollStartEvent = motionEvent

            unhighlight()
            mKanjiTextView.visibility = View.INVISIBLE
            mIconImageView.visibility = View.VISIBLE
            mIconImageView.setImageResource(R.drawable.icon_swap)

            mKanjiChoiceWindow.onSquareScrollStart(mSquareChar, getKanjiBoxParams())
        }
        // scroll event continuing
        else {
            Log.d(TAG, "ScrollContinue")
            mIconImageView.setImageResource(mKanjiChoiceWindow.onSquareScroll(motionEvent1))
        }

        return true
    }

    override fun onDown(motionEvent: MotionEvent): Boolean
    {
        return false
    }

    override fun onFling(motionEvent: MotionEvent?, motionEvent1: MotionEvent, v: Float, v1: Float): Boolean
    {
        return false
    }

    override fun onLongPress(motionEvent: MotionEvent)
    {
        val window = getProperWindow<ICopyText>()
        window.copyText()
    }

    override fun onShowPress(e: MotionEvent)
    {
    }

    private fun <WindowType> getProperWindow() : WindowType
    {
        return if (mSquareChar.displayData.instantMode)
        {
            mWindowCoordinator.getWindowOfType(WINDOW_INSTANT_KANJI)
        }
        else {
            mWindowCoordinator.getWindowOfType(WINDOW_INFO)
        }
    }

    private fun getKanjiBoxParams() : BoxParams
    {
        var pos = IntArray(2)
        getLocationOnScreen(pos)
        return BoxParams(pos[0], pos[1], width, height)
    }

    companion object
    {
        private val TAG = KanjiCharacterView::class.java.name
    }
}
