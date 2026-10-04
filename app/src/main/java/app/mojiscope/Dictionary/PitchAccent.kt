package app.mojiscope.Dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.mojiscope.MOJI_PREF_FILE
import app.mojiscope.MOJI_PREF_PITCH_ACCENT
import app.mojiscope.PITCH_DATABASE_NAME
import app.mojiscope.toHiragana
import java.io.File

/**
 * The bundled pitch accent dictionary. It works alongside whichever word dictionary is selected:
 * the downstep positions of a word are shown after its reading, for example 猫 (ねこ) [1].
 */
class PitchAccent private constructor(private val db: SQLiteDatabase?)
{
    /**
     * Downstep positions of a word, formatted like "[1]" or "[1][0]" for words with several accents.
     * [readings] is the entry's reading text ("ねこ, ネコ"); empty for words written in kana only.
     */
    fun lookup(term: String, readings: String): String
    {
        val db = db ?: return ""

        val wanted = readings.split(",")
                .map { toHiragana(it.trim()) }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf(toHiragana(term)) }
                .toSet()

        val positions = LinkedHashSet<String>()
        db.rawQuery("SELECT reading, positions FROM pitch WHERE term = ?", arrayOf(term)).use { c ->
            while (c.moveToNext())
            {
                if (c.getString(0) in wanted) positions.addAll(c.getString(1).split(","))
            }
        }

        return positions.joinToString("") { "[$it]" }
    }

    companion object
    {
        private const val TAG = "PitchAccent"

        @Volatile
        private var instance: PitchAccent? = null

        fun get(context: Context): PitchAccent
        {
            return instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }
        }

        private fun open(context: Context): PitchAccent
        {
            return try
            {
                PitchAccent(SQLiteDatabase.openDatabase(File(context.filesDir, PITCH_DATABASE_NAME).absolutePath, null, SQLiteDatabase.OPEN_READONLY))
            }
            catch (e: Exception)
            {
                Log.e(TAG, "Unable to open the pitch accent database", e)
                PitchAccent(null)
            }
        }

        fun isEnabled(context: Context): Boolean
        {
            return context.getSharedPreferences(MOJI_PREF_FILE, Context.MODE_PRIVATE).getBoolean(MOJI_PREF_PITCH_ACCENT, true)
        }

        fun setEnabled(context: Context, enabled: Boolean)
        {
            context.getSharedPreferences(MOJI_PREF_FILE, Context.MODE_PRIVATE).edit().putBoolean(MOJI_PREF_PITCH_ACCENT, enabled).apply()
        }
    }
}
