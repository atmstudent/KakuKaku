package io.github.atmstudent.kakukaku.Ocr

import android.graphics.Bitmap
import io.github.atmstudent.kakukaku.TextDirection

data class OcrParams(val bitmap: Bitmap,
                     val originalBitmap: Bitmap,
                     val box: BoxParams,
                     val textDirection: TextDirection,
                     val instantMode: Boolean)
{
    override fun toString() : String {
        return "Box: $box InstantOCR: $instantMode TextDirection: $textDirection"
    }
}

