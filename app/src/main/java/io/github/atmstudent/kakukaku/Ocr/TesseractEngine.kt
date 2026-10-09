package io.github.atmstudent.kakukaku.Ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.ResultIterator
import com.googlecode.tesseract.android.TessBaseAPI
import io.github.atmstudent.kakukaku.BuildConfig
import java.io.File
import java.io.FileOutputStream

/** One character found by Tesseract: its choices (best first, with confidence), its box and whether it starts a line */
class TesseractSymbol(val choices: List<Pair<String, Double>>, val box: IntArray, val startsLine: Boolean)

/**
 * The older text recognition, Tesseract with the Japanese language data that comes with the app (`assets/tessdata`).
 * Horizontal text uses `jpn`, vertical text `jpn_vert`. Each is loaded when it is first needed (a second or two), and
 * the data is copied from the APK to the files folder, where Tesseract can read it, the first time and after an update.
 */
class TesseractEngine(context: Context)
{
    private val mContext = context.applicationContext
    private val mEngines = HashMap<String, TessBaseAPI>()

    @Volatile
    private var mBusy: TessBaseAPI? = null

    /** Reads the text in [bitmap]. Vertical text is read from the rightmost column to the left. Not for the main thread. */
    fun recognize(bitmap: Bitmap, vertical: Boolean): List<TesseractSymbol>
    {
        val api = engine(if (vertical) VERTICAL_LANGUAGE else HORIZONTAL_LANGUAGE)
        api.pageSegMode = if (vertical) TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK_VERT_TEXT else TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK

        val result = ArrayList<TesseractSymbol>()
        mBusy = api
        try
        {
            api.setImage(bitmap)
            // Recognising here, not through the iterator, is what can be interrupted by stop()
            api.getHOCRText(0)

            val iterator: ResultIterator = api.resultIterator ?: return result
            try
            {
                iterator.begin()
                do
                {
                    val text = iterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL)
                    val choices = iterator.symbolChoicesAndConfidence.map { Pair(it.first as String, it.second as Double) }
                    val box = iterator.getBoundingBox(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL)
                    if (choices.isNotEmpty() && box != null && text != null)
                    {
                        result.add(TesseractSymbol(choices, box, iterator.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)))
                    }
                } while (iterator.next(TessBaseAPI.PageIteratorLevel.RIL_SYMBOL))
            }
            finally
            {
                iterator.delete()
            }
        }
        finally
        {
            api.clear()
            mBusy = null
        }

        return result
    }

    /** Interrupts a recognition that is running */
    fun cancel()
    {
        mBusy?.stop()
    }

    fun release()
    {
        cancel()
        synchronized(mEngines)
        {
            for (api in mEngines.values) api.recycle()
            mEngines.clear()
        }
    }

    private fun engine(language: String): TessBaseAPI
    {
        synchronized(mEngines)
        {
            mEngines[language]?.let { return it }

            copyData()
            val api = TessBaseAPI()
            if (!api.init(mContext.filesDir.absolutePath, language))
            {
                api.recycle()
                throw IllegalStateException("Tesseract could not load $language")
            }
            mEngines[language] = api
            return api
        }
    }

    /** Tesseract reads its data from a folder, not from the APK: copy it there once per app version */
    private fun copyData()
    {
        val folder = File(mContext.filesDir, DATA_FOLDER)
        val marker = File(folder, ".version")
        if (marker.exists() && marker.readText() == BuildConfig.VERSION_CODE.toString() && DATA_FILES.all { File(folder, it).exists() }) return

        Log.d(TAG, "Copying the Tesseract data")
        folder.mkdirs()
        for (name in DATA_FILES)
        {
            mContext.assets.open("$DATA_FOLDER/$name").use { input ->
                FileOutputStream(File(folder, name)).use { output -> input.copyTo(output) }
            }
        }
        marker.writeText(BuildConfig.VERSION_CODE.toString())
    }

    companion object
    {
        private const val TAG = "TesseractEngine"
        private const val DATA_FOLDER = "tessdata"
        private const val HORIZONTAL_LANGUAGE = "jpn"
        private const val VERTICAL_LANGUAGE = "jpn_vert"
        private val DATA_FILES = listOf("$HORIZONTAL_LANGUAGE.traineddata", "$VERTICAL_LANGUAGE.traineddata")
    }
}
