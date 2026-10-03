package app.mojiscope.Windows.Views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
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

    private enum class HighlightState { NONE, FILLED, OUTLINED }

    private var mHighlightState = HighlightState.NONE
    private val mFillPaint = Paint()
    private val mStrokePaint = Paint()

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
        setWillNotDraw(false)

        mFillPaint.style = Paint.Style.FILL
        mFillPaint.color = ContextCompat.getColor(context, R.color.blue_dark_translucent)
        mStrokePaint.style = Paint.Style.STROKE
        mStrokePaint.color = ContextCompat.getColor(context, R.color.blue_dark)
        mStrokePaint.strokeWidth = Math.max(1f, context.resources.displayMetrics.density)

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
        // The highlight fills the whole cell so that neighboring highlighted cells touch and merge
        mCellSizePx = px
    }

    fun highlight()
    {
        setHighlightState(HighlightState.FILLED)
    }

    fun highlightLight()
    {
        setHighlightState(HighlightState.OUTLINED)
    }

    fun unhighlight()
    {
        setHighlightState(HighlightState.NONE)
    }

    private fun setHighlightState(state: HighlightState)
    {
        if (mHighlightState == state)
        {
            return
        }

        mHighlightState = state
        invalidate()

        // Neighbors may need to add or remove the border they share with this cell
        neighbor(-1)?.invalidate()
        neighbor(1)?.invalidate()
    }

    private fun neighbor(direction: Int): KanjiCharacterView?
    {
        val group = parent as? ViewGroup ?: return null
        val neighborView = group.getChildAt(group.indexOfChild(this) + direction) as? KanjiCharacterView ?: return null

        // Only cells that are touching in the same row count (not the end of one row and the start of the next)
        if (Math.abs(neighborView.top - top) > 1) return null
        val gap = if (direction < 0) left - neighborView.right else neighborView.left - right
        return if (Math.abs(gap) <= 1) neighborView else null
    }

    override fun onDraw(canvas: Canvas)
    {
        super.onDraw(canvas)

        if (mHighlightState == HighlightState.NONE)
        {
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val half = mStrokePaint.strokeWidth / 2f

        if (mHighlightState == HighlightState.FILLED)
        {
            canvas.drawRect(0f, 0f, w, h, mFillPaint)
        }

        canvas.drawLine(0f, half, w, half, mStrokePaint)
        canvas.drawLine(0f, h - half, w, h - half, mStrokePaint)

        val joinedLeft = neighbor(-1)?.mHighlightState == mHighlightState
        val joinedRight = neighbor(1)?.mHighlightState == mHighlightState
        if (!joinedLeft) canvas.drawLine(half, 0f, half, h, mStrokePaint)
        if (!joinedRight) canvas.drawLine(w - half, 0f, w - half, h, mStrokePaint)
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
