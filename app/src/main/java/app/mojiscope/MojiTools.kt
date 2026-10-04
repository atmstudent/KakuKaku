@file:JvmName("MojiTools")

package app.mojiscope

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.content.ContextCompat
import android.view.WindowManager
import android.widget.Toast

import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

import java.util.ArrayList

private const val TAG = "MojiTools"
private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation().create()

enum class TextDirection(val value: Int) {
    AUTO(0),
    HORIZONTAL(1),
    VERTICAL(2);

    companion object {
        private val values = values();
        fun getByValue(value: Int) = values.firstOrNull { it.value == value }
    }
}

data class Prefs(val textDirectionSetting: TextDirection,
                 val imageFilterSetting: Boolean,
                 val instantModeSetting: Boolean,
                 val showHideSetting: Boolean);

// NOTE: The defValue here should match the defValue of the BroadcastReceivers, otherwise
// they will be out of sync the first time.
fun getPrefs(context: Context): Prefs
{
    val prefs = context.getSharedPreferences(MOJI_PREF_FILE, Context.MODE_PRIVATE)

    return Prefs(
            TextDirection.valueOf(prefs.getString(MOJI_PREF_TEXT_DIRECTION, TextDirection.AUTO.toString()).toString()),
            prefs.getBoolean(MOJI_PREF_IMAGE_FILTER, false),
            prefs.getBoolean(MOJI_PREF_INSTANT_MODE, false),
            prefs.getBoolean(MOJI_PREF_SHOW_HIDE, true))
}

fun toJson(obj: Any): String
{
    return gson.toJson(obj)
}

/**
 * Full physical screen size in the current rotation (includes system bars and cutouts)
 */
fun getRealScreenSize(context: Context): Point
{
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
    {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return Point(bounds.width(), bounds.height())
    }

    val size = Point()
    @Suppress("DEPRECATION")
    windowManager.defaultDisplay.getRealSize(size)
    return size
}

fun dpToPx(context: Context, dp: Int): Int
{
    val displayMetrics = context.resources.displayMetrics
    // Same scale as the dp and sp units in layouts. The display's physical dpi (xdpi) must not be used:
    // e-ink tablets report one that differs from the density, which made the cells smaller than their text
    return Math.round(dp * displayMetrics.density)
}

fun pxToDp(context: Context, px: Int): Int
{
    val displayMetrics = context.resources.displayMetrics
    return Math.round(px / displayMetrics.density)
}

/**
 * Splits `text` into individual unicode characters as a list of strings
 * @param text Text to split
 * @return List of strings with each string representing one unicode character
 */
fun splitTextByChar(text: String): List<String>
{
    val charList = ArrayList<String>()

    val length = text.length
    var offset = 0
    while (offset < length)
    {
        val curr = text.codePointAt(offset)
        val charz = String(intArrayOf(curr), 0, 1)
        charList.add(charz)
        offset += Character.charCount(curr)
    }

    return charList
}

fun startMojiscopeService(context: Context, i: Intent)
{
    ContextCompat.startForegroundService(context, i)
}

fun setupMojiscopeDatabasesAndFiles(context: Context)
{
    try {
        val filesAndPaths = hashMapOf(
                JMDICT_DATABASE_NAME to context.filesDir.absolutePath,
                PITCH_DATABASE_NAME to context.filesDir.absolutePath)

        deleteOutdatedDatabases(context)

        if (shouldResetData(filesAndPaths))
        {
            Log.d(TAG, "Resetting Data")
            for (fileAndPath in filesAndPaths){
                File("${fileAndPath.value}/${fileAndPath.key}").delete()
            }
        }

        copyFilesIfNotExists(context, filesAndPaths)

        var screenshotPath: String = context.filesDir.absolutePath + "/$SCREENSHOT_FOLDER_NAME"
        createDirIfNotExists(screenshotPath)
        deleteScreenshotsOlderThanOneDay(screenshotPath)
    }
    catch (e: Exception)
    {
        Toast.makeText(context, "Unable to setup Kaku2 database", Toast.LENGTH_LONG).show()
        return
    }
}

/**
 * The bundled database is versioned by file name, so an app update that ships a newer one leaves
 * the previous copy behind; remove it (and its journal files) to free the space.
 */
fun deleteOutdatedDatabases(context: Context)
{
    context.filesDir.listFiles()?.filter {
        (it.name.startsWith("DB_") && it.name.contains("Dict-") && !it.name.startsWith(JMDICT_DATABASE_NAME)) ||
                (it.name.startsWith("DB_MojiPitch-") && !it.name.startsWith(PITCH_DATABASE_NAME))
    }?.forEach {
        Log.d(TAG, "Deleting outdated database ${it.name}")
        it.delete()
    }
}

fun shouldResetData(filesAndPaths: Map<String, String>) : Boolean
{
    for (fileAndPath in filesAndPaths){
        if (!File("${fileAndPath.value}/${fileAndPath.key}").exists()) return true
    }
    return false
}

fun createDirIfNotExists(path: String)
{
    val dir = File(path)
    if (!dir.exists())
    {
        dir.mkdirs()
    }
}

fun copyFilesIfNotExists(context: Context, filesAndPaths: Map<String, String>)
{
    for (fileAndPath in filesAndPaths)
    {
        val path = fileAndPath.value
        val fileName = fileAndPath.key
        val filePath = "$path/$fileName"

        if (File(filePath).exists())
        {
            continue
        }

        createDirIfNotExists(path)

        context.assets.open(fileName).use { input ->
            FileOutputStream(filePath).use { output -> input.copyTo(output) }
        }

        Log.d(TAG, "Copied $filePath")
    }
}

fun deleteScreenshotsOlderThanOneDay(path: String)
{
    try {
        var dir = File(path)
        if (dir.exists())
        {
            Log.d(TAG, dir.absolutePath)
            val purgeTime = System.currentTimeMillis() - 1 * 24 * 60 * 60 * 1000
            for (file in dir.listFiles() ?: emptyArray())
            {
                if (file.isFile && file.lastModified() < purgeTime)
                {
                    file.delete()
                }
            }
        }
    }
    catch (e: Exception)
    {
        Log.d(TAG, e.toString())
    }
}