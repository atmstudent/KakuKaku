package io.github.atmstudent.kakukaku.WordList

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.atmstudent.kakukaku.Anki.Marker

/** A term saved to the word list: the text of every marker, as it was when the term was saved */
data class SavedTerm(val id: Long, val savedAt: Long, val values: Map<String, String>)
{
    val expression: String get() = values[Marker.EXPRESSION.id] ?: ""
}

/** The saved words, in their own small database. Everything is stored, so what is exported can be chosen later. */
class WordListStore private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, "word_list.db", null, 1)
{
    override fun onCreate(db: SQLiteDatabase)
    {
        db.execSQL("CREATE TABLE saved (id INTEGER PRIMARY KEY AUTOINCREMENT, saved_at INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT NOT NULL, sentence TEXT NOT NULL, data TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
    {
    }

    /** Saves a term; false if the same word with the same sentence is in the list already */
    fun add(values: Map<String, String>): Boolean
    {
        val expression = values[Marker.EXPRESSION.id] ?: ""
        val reading = values[Marker.READING.id] ?: ""
        val sentence = values[Marker.SENTENCE.id] ?: ""

        if (contains(values)) return false

        val row = ContentValues()
        row.put("saved_at", System.currentTimeMillis())
        row.put("expression", expression)
        row.put("reading", reading)
        row.put("sentence", sentence)
        row.put("data", Gson().toJson(values))
        writableDatabase.insertOrThrow("saved", null, row)
        return true
    }

    fun contains(values: Map<String, String>): Boolean
    {
        val args = arrayOf(values[Marker.EXPRESSION.id] ?: "", values[Marker.READING.id] ?: "", values[Marker.SENTENCE.id] ?: "")
        readableDatabase.rawQuery("SELECT 1 FROM saved WHERE expression = ? AND reading = ? AND sentence = ? LIMIT 1", args).use { return it.moveToFirst() }
    }

    /** The saved terms, the newest first */
    fun list(): List<SavedTerm>
    {
        val type = object : TypeToken<Map<String, String>>() {}.type
        val result = ArrayList<SavedTerm>()
        readableDatabase.rawQuery("SELECT id, saved_at, data FROM saved ORDER BY id DESC", null).use { c ->
            while (c.moveToNext())
            {
                val values: Map<String, String> = try { Gson().fromJson(c.getString(2), type) } catch (e: Exception) { emptyMap() }
                result.add(SavedTerm(c.getLong(0), c.getLong(1), values))
            }
        }
        return result
    }

    fun delete(id: Long)
    {
        writableDatabase.delete("saved", "id = ?", arrayOf(id.toString()))
    }

    fun clear()
    {
        writableDatabase.delete("saved", null, null)
    }

    companion object
    {
        @Volatile
        private var instance: WordListStore? = null

        fun get(context: Context): WordListStore
        {
            return instance ?: synchronized(this) { instance ?: WordListStore(context).also { instance = it } }
        }
    }
}

/** Which markers the word list shows and exports (the rest is stored too, so this can change later) */
object WordListSettings
{
    private const val KEY = "WordListMarkers"
    private val DEFAULT = listOf(Marker.EXPRESSION, Marker.READING, Marker.GLOSSARY, Marker.SENTENCE)

    private fun prefs(context: Context) = context.getSharedPreferences(io.github.atmstudent.kakukaku.KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE)

    /** In the order of [Marker.values] */
    fun markers(context: Context): List<Marker>
    {
        val saved = prefs(context).getString(KEY, null) ?: return DEFAULT
        val ids = saved.split(",").toSet()
        return Marker.values().filter { it.id in ids }
    }

    fun setMarkers(context: Context, markers: Collection<Marker>)
    {
        prefs(context).edit().putString(KEY, markers.joinToString(",") { it.id }).apply()
    }
}
