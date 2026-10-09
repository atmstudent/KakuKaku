package io.github.atmstudent.kakukaku.Furigana

import android.content.Context
import android.util.Log
import io.github.atmstudent.kakukaku.Database.JmDictDatabase.JmDatabaseHelper
import io.github.atmstudent.kakukaku.KAKUKAKU_PREF_FILE
import io.github.atmstudent.kakukaku.KAKUKAKU_PREF_STRIP_FURIGANA
import io.github.atmstudent.kakukaku.Search.JmTask
import io.github.atmstudent.kakukaku.toHiragana
import io.github.atmstudent.kakukaku.DB_KANJIDICT_NAME

/** The "Strip furigana" setting, and furigana stripping for text that is selected in another app */
object Furigana
{
    private const val TAG = "Furigana"

    fun isEnabled(context: Context): Boolean
    {
        return context.getSharedPreferences(KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE).getBoolean(KAKUKAKU_PREF_STRIP_FURIGANA, true)
    }

    fun setEnabled(context: Context, enabled: Boolean)
    {
        context.getSharedPreferences(KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE).edit().putBoolean(KAKUKAKU_PREF_STRIP_FURIGANA, enabled).apply()
    }

    /** Takes the furigana out of [text] if the setting is on. Reads the dictionary, so not for the main thread. */
    fun strip(context: Context, text: String): String
    {
        if (!isEnabled(context)) return text

        val db = JmDatabaseHelper.instance(context.applicationContext).readableDatabase
        val start = System.currentTimeMillis()
        val result = FuriganaStripper { forms -> readings(db, forms) }.strip(text)
        Log.d(TAG, "Furigana: ${text.length - result.length} character(s) removed in ${System.currentTimeMillis() - start} ms")
        return result
    }

    /** Readings (hiragana) of the written forms found in the bundled dictionary */
    private fun readings(db: android.database.sqlite.SQLiteDatabase, forms: Collection<String>): Map<String, Set<String>>
    {
        val result = HashMap<String, MutableSet<String>>()
        for (chunk in forms.chunked(JmTask.SQL_CHUNK))
        {
            db.rawQuery("SELECT kanji, readings, dictionary FROM entryoptimized WHERE kanji IN (${JmTask.placeholders(chunk.size)})", chunk.toTypedArray()).use { c ->
                while (c.moveToNext())
                {
                    val set = result.getOrPut(c.getString(0)) { HashSet() }
                    val readings = c.getString(2).let { if (it == DB_KANJIDICT_NAME) kanjiReadings(c.getString(1)) else wordReadings(c.getString(1)) }
                    set.addAll(readings)
                }
            }
        }
        return result
    }

    private fun wordReadings(readings: String?): List<String> =
            (readings ?: "").split(",").map { toHiragana(it.trim()) }.filter { it.isNotEmpty() }

    /**
     * KANJIDIC rows look like "(ショク, ジキ) [く.う, た.べる, -は.む]": on readings in parentheses, kun readings
     * in brackets with a dot before the okurigana. Ruby over a lone kanji gives the on reading or the part of
     * the kun reading before the dot: 食た, 読よ. The okurigana are not part of it, or 食たべる would lose them.
     */
    internal fun kanjiReadings(text: String?): List<String>
    {
        if (text == null) return emptyList()
        val result = ArrayList<String>()

        Regex("\\(([^)]*)\\)").find(text)?.groupValues?.get(1)?.split(",")?.forEach { result.add(toHiragana(it.trim())) }
        Regex("\\[([^\\]]*)]").find(text)?.groupValues?.get(1)?.split(",")?.forEach {
            val kun = it.trim().trim('-')
            result.add(kun.substringBefore('.'))
        }

        return result.map { it.trim('-') }.filter { it.isNotEmpty() }
    }
}
