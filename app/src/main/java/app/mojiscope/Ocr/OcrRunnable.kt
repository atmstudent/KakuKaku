package app.mojiscope.Ocr

import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.util.Log

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.HashMap

import app.mojiscope.*
import app.mojiscope.Interfaces.Stoppable
import app.mojiscope.MainService
import app.mojiscope.Windows.CaptureWindow
import app.mojiscope.Windows.Data.ChoiceCertainty
import app.mojiscope.Windows.Data.DisplayDataOcr
import app.mojiscope.Windows.Data.ISquareChar
import app.mojiscope.Windows.Data.SquareCharOcr

/**
 * Created by 0xbad1d3a5 on 4/16/2016.
 */
class OcrRunnable(context: Context, private var mCaptureWindow: CaptureWindow?) : Runnable, Stoppable
{
    private val mContext: MainService = context as MainService
    private val mOcrLock = java.lang.Object()
    private val mSimilarChars = loadSimilarChars()
    private val mCommonMistakes = loadCommonMistakes()

    private var mTextRecognizer: TextRecognizer? = null
    private var mThreadRunning = true
    private var mReady = false
    private var mOcrParams: OcrParams? = null

    val isReadyForOcr: Boolean
        get() = mOcrParams == null

    init
    {
        mOcrParams = null
    }

    override fun run()
    {
        mTextRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        mReady = true

        while (mThreadRunning)
        {
            Log.d(TAG, "THREAD STARTING NEW LOOP")

            try
            {
                 synchronized(mOcrLock)
                 {
                    if (!mThreadRunning)
                    {
                        return@synchronized
                    }

                    Log.d(TAG, "WAITING")
                    mOcrLock.wait()
                    Log.d(TAG, "THREAD STOPPED WAITING")

                    val ocrParams = mOcrParams
                    if (ocrParams == null)
                    {
                        Log.d(TAG, "OcrRunnable - OcrParams null")
                        return@synchronized
                    }

                    Log.d(TAG, "Processing OCR with params " + ocrParams.toString())

                    val startTime = System.currentTimeMillis()

                    saveBitmap(ocrParams.bitmap)

                    mCaptureWindow!!.showLoadingAnimation()

                    val visionText = Tasks.await(mTextRecognizer!!.process(InputImage.fromBitmap(ocrParams.bitmap, 0)))
                    val displayData = getDisplayData(ocrParams, visionText)
                    processDisplayData(displayData)

                    if (displayData.text.length > 0)
                    {
                        val ocrTime = System.currentTimeMillis() - startTime
                        sendOcrResultToContext(OcrResult(displayData, ocrTime))
                    } else
                    {
                        sendToastToContext("No Characters Recognized.")
                    }

                    mCaptureWindow!!.stopLoadingAnimation(ocrParams.instantMode)

                    mOcrParams = null
                }
            } catch (e: Exception)
            {
                e.printStackTrace()
            }
        }

        Log.d(TAG, "THREAD STOPPED")
    }

    /**
     * Unblocks the thread and starts OCR
     */
    fun runTess(ocrParams: OcrParams)
    {
        synchronized(mOcrLock)
        {
            if (!mThreadRunning || !mReady)
            {
                return
            }

            mOcrParams = ocrParams
            mOcrLock.notify()

            Log.d(TAG, "NOTIFIED")
        }
    }

    /**
     * ML Kit recognition can't be interrupted, so callers simply wait for [isReadyForOcr].
     */
    fun cancel()
    {
    }

    /**
     * Cancels any OCR recognition in progress and stops any further OCR attempts
     */
    override fun stop()
    {
        synchronized(mOcrLock)
        {
            mThreadRunning = false
            mOcrParams = null
            mCaptureWindow = null

            mOcrLock.notify()
            mTextRecognizer?.close()
        }
    }

    private fun processDisplayData(displayData: DisplayDataOcr)
    {
        for (squareChar in displayData.squareChars as List<SquareCharOcr>)
        {
            val similarChars = mSimilarChars[squareChar.char]

            if (similarChars != null)
            {
                for (c in similarChars)
                {
                    squareChar.addChoice(c, ChoiceCertainty.UNCERTAIN)
                }
            }
        }

        for (squareChar in displayData.squareChars as List<SquareCharOcr>)
        {
            correctCommonMistake(squareChar, "く")
            correctCommonMistake(squareChar, "し")
            correctCommonMistake(squareChar, "じ")
            correctCommonMistake(squareChar, "え")
            correctCommonMistake(squareChar, "、")
            correctCommonMistake(squareChar, "。")

            correctKanjiOne(squareChar)
            correctKatakanaDash(squareChar)
        }
    }

    private fun correctCommonMistake(squareChar: SquareCharOcr, char: String)
    {
        if (mCommonMistakes[squareChar.char] == char)
        {
            val prev = squareChar.prev
            val next = squareChar.next

            if (prev?.char != null && LangUtils.IsJapaneseChar(prev.char[0]) ||
                next?.char != null && LangUtils.IsJapaneseChar(next.char[0]))
            {
                squareChar.addChoice(char, ChoiceCertainty.CERTAIN)
            }
        }
    }

    private fun correctKatakanaDash(squareChar: SquareCharOcr)
    {
        if (mCommonMistakes[squareChar.char] != null)
        {
            val prev = squareChar.prev

            if (prev?.char != null && LangUtils.IsKatakana(prev.char[0]))
            {
                squareChar.addChoice("ー", ChoiceCertainty.CERTAIN)
            }
        }
    }

    private fun correctKanjiOne(squareChar: SquareCharOcr)
    {
        if (mCommonMistakes[squareChar.char] != null)
        {
            val next = squareChar.next

            if (next?.char != null && (LangUtils.IsKanji(next.char[0]) || LangUtils.IsHiragana(next.char[0])))
            {
                squareChar.addChoice("一", ChoiceCertainty.CERTAIN)
            }
        }
    }

    private fun getDisplayData(ocrParams: OcrParams, visionText: Text): DisplayDataOcr
    {
        val bitmap = ocrParams.originalBitmap
        val boxParams = ocrParams.box

        val ocrChars = ArrayList<SquareCharOcr>()
        val displayData = DisplayDataOcr(bitmap, boxParams, ocrParams.instantMode, ocrChars)

        for (block in visionText.textBlocks)
        {
            for (line in block.lines)
            {
                for (element in line.elements)
                {
                    for (symbol in element.symbols)
                    {
                        val rect = symbol.boundingBox ?: continue
                        val choices = arrayListOf(kotlin.Pair(symbol.text, 100.0))
                        ocrChars.add(SquareCharOcr(displayData, choices, intArrayOf(rect.left, rect.top, rect.right, rect.bottom)))
                    }
                }
            }
        }

        displayData.assignIndicies()

        return displayData
    }

    private fun loadSimilarChars(): HashMap<String, List<String>>
    {
        val similarChars = HashMap<String, List<String>>()

        for (list in OcrCorrection.CommonLookalikes)
        {
            for ((index, kana) in list.withIndex())
            {
                if (list.size == 1)
                {
                    continue
                }

                val kanaList: List<String> = when (index)
                {
                    0 -> list.takeLast(list.size - 1)
                    list.size - 1 -> list.take(list.size - 1)
                    else -> list.subList(0, index) + list.subList(index + 1, list.size)
                }

                if (similarChars.containsKey(kana))
                {
                    for (k in kanaList)
                    {
                        if (!similarChars[kana]!!.contains(k))
                        {
                            similarChars[kana] = kanaList + listOf(k)
                        }
                    }
                }
                else
                {
                    similarChars[kana] = kanaList
                }
            }
        }

        return similarChars
    }

    private fun loadCommonMistakes(): HashMap<String, String>
    {
        val commonMistakes = HashMap<String, String>()

        for (pair in OcrCorrection.CommonMistakes)
        {
            for (c in pair.first)
            {
                commonMistakes[c] = pair.second
            }
        }

        return commonMistakes
    }

    private fun sendOcrResultToContext(result: OcrResult)
    {
        Message.obtain(mContext.handler, 0, result).sendToTarget()
    }

    private fun sendToastToContext(message: String)
    {
        Message.obtain(mContext.handler, 0, message).sendToTarget()
    }

    @Throws(FileNotFoundException::class)
    private fun saveBitmap(bitmap: Bitmap, name: String = "screen")
    {
        val fs = String.format("%s/%s/%s_%d.png", mContext.filesDir.absolutePath, SCREENSHOT_FOLDER_NAME, name, System.nanoTime())
        Log.d(TAG, fs)
        val fos = FileOutputStream(fs)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
    }

    companion object
    {

        private val TAG = OcrRunnable::class.java.name
    }
}
