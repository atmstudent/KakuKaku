package app.mojiscope.Dictionary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.mojiscope.DB_SPLIT_CHAR
import app.mojiscope.Database.JmDictDatabase.Models.EntryOptimized
import app.mojiscope.MOJI_PREF_FILE
import app.mojiscope.MOJI_PREF_SELECTED_DICTIONARY
import java.io.InputStream

data class UserDictionary(val id: Long, val title: String, val revision: String, val entries: Int)

data class ImportResult(val dictionary: UserDictionary, val replacedIds: List<Long>)

/**
 * Dictionaries imported by the user (Yomitan format). Kept in their own database so the bundled
 * dictionary is never touched.
 */
class UserDictionaryStore private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, "user_dictionaries.db", null, 1)
{
    override fun onCreate(db: SQLiteDatabase)
    {
        db.execSQL("CREATE TABLE dictionaries (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, revision TEXT NOT NULL, entries INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE terms (dict_id INTEGER NOT NULL, term TEXT NOT NULL, reading TEXT NOT NULL, tags TEXT NOT NULL, rules TEXT NOT NULL, score INTEGER NOT NULL, sequence INTEGER NOT NULL, glossary TEXT NOT NULL)")
        db.execSQL("CREATE INDEX terms_lookup ON terms (dict_id, term)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
    {
    }

    fun list(): List<UserDictionary>
    {
        val result = ArrayList<UserDictionary>()
        readableDatabase.rawQuery("SELECT id, title, revision, entries FROM dictionaries ORDER BY title COLLATE NOCASE", null).use { c ->
            while (c.moveToNext())
            {
                result.add(UserDictionary(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3)))
            }
        }
        return result
    }

    fun delete(id: Long)
    {
        val db = writableDatabase
        db.beginTransaction()
        try
        {
            db.delete("terms", "dict_id = ?", arrayOf(id.toString()))
            db.delete("dictionaries", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        }
        finally
        {
            db.endTransaction()
        }
    }

    /**
     * Imports a Yomitan zip. Everything happens in one transaction, so a failed import leaves nothing behind.
     * An existing dictionary with the same title is replaced, which is how a newer version is installed.
     *
     * @param onProgress called with the number of entries imported so far
     */
    fun import(openStream: () -> InputStream, onProgress: (Int) -> Unit): ImportResult
    {
        val db = writableDatabase
        db.beginTransaction()
        try
        {
            var imported = 0

            // Reads the title first so an unusable file fails before anything is written
            val title = YomitanParser.readIndex(openStream).title
            val created = ContentValues()
            created.put("title", title)
            created.put("revision", "")
            created.put("entries", 0)
            val dictId = db.insertOrThrow("dictionaries", null, created)

            val statement = db.compileStatement("INSERT INTO terms (dict_id, term, reading, tags, rules, score, sequence, glossary) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")

            val (meta, count) = YomitanParser.parse(openStream) { rows ->
                for (row in rows)
                {
                    statement.clearBindings()
                    statement.bindLong(1, dictId)
                    statement.bindString(2, row.term)
                    statement.bindString(3, row.reading)
                    statement.bindString(4, row.tags)
                    statement.bindString(5, row.rules)
                    statement.bindLong(6, row.score.toLong())
                    statement.bindLong(7, row.sequence)
                    statement.bindString(8, row.glossary)
                    statement.executeInsert()
                }

                imported += rows.size
                onProgress(imported)
            }

            val update = ContentValues()
            update.put("title", meta.title)
            update.put("revision", meta.revision)
            update.put("entries", count)
            db.update("dictionaries", update, "id = ?", arrayOf(dictId.toString()))

            // Replace older copies of the same dictionary
            val replaced = ArrayList<Long>()
            db.rawQuery("SELECT id FROM dictionaries WHERE title = ? AND id != ?", arrayOf(meta.title, dictId.toString())).use { c ->
                while (c.moveToNext()) replaced.add(c.getLong(0))
            }
            for (old in replaced)
            {
                db.delete("terms", "dict_id = ?", arrayOf(old.toString()))
                db.delete("dictionaries", "id = ?", arrayOf(old.toString()))
            }

            db.setTransactionSuccessful()
            return ImportResult(UserDictionary(dictId, meta.title, meta.revision, count), replaced)
        }
        finally
        {
            db.endTransaction()
        }
    }

    /**
     * Entries of [dictionaryId] whose written form starts with [firstChar], shaped like the bundled
     * dictionary's entries so the existing matching and display code can use them.
     */
    fun search(dictionaryId: Long, firstChar: String): List<EntryOptimized>
    {
        val title = list().firstOrNull { it.id == dictionaryId }?.title ?: return emptyList()
        val escaped = firstChar.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        class Group(val term: String, val reading: String, val senses: MutableList<String>, val tags: MutableList<String>, val rules: MutableSet<String>)

        val groups = LinkedHashMap<String, Group>()
        var unique = 0L

        readableDatabase.rawQuery(
                "SELECT term, reading, tags, rules, sequence, glossary FROM terms WHERE dict_id = ? AND term LIKE ? ESCAPE '\\' ORDER BY score DESC",
                arrayOf(dictionaryId.toString(), "$escaped%")).use { c ->
            while (c.moveToNext())
            {
                val term = c.getString(0)
                val reading = c.getString(1)
                val sequence = c.getLong(4)

                // Rows with the same sequence number are the separate senses of one word
                val key = if (sequence != 0L) "$term\u0000$reading\u0000$sequence" else "u${unique++}"
                val group = groups.getOrPut(key) { Group(term, reading, ArrayList(), ArrayList(), LinkedHashSet()) }

                group.senses.add(c.getString(5))
                group.tags.add(c.getString(2))
                c.getString(3).split(" ").filter { it.isNotEmpty() }.forEach { group.rules.add(it) }
            }
        }

        return groups.values.map {
            val entry = EntryOptimized()
            entry.kanji = it.term
            entry.readings = it.reading
            entry.meanings = it.senses.joinToString(DB_SPLIT_CHAR)
            entry.pos = it.tags.joinToString(DB_SPLIT_CHAR)
            entry.rules = it.rules.joinToString(" ")
            entry.priorities = ""
            entry.dictionary = title
            entry.isPrimaryEntry = true
            entry
        }
    }

    companion object
    {
        @Volatile
        private var instance: UserDictionaryStore? = null

        fun get(context: Context): UserDictionaryStore
        {
            return instance ?: synchronized(this) {
                instance ?: UserDictionaryStore(context).also { instance = it }
            }
        }
    }
}

/** Which dictionary is used for word lookups: the bundled one or an imported one */
object DictionarySelection
{
    const val BUILT_IN = -1L

    fun get(context: Context): Long
    {
        val id = context.getSharedPreferences(MOJI_PREF_FILE, Context.MODE_PRIVATE).getLong(MOJI_PREF_SELECTED_DICTIONARY, BUILT_IN)
        if (id == BUILT_IN) return BUILT_IN

        // Fall back to the bundled dictionary if the selected one has been deleted
        return if (UserDictionaryStore.get(context).list().any { it.id == id }) id else BUILT_IN
    }

    fun set(context: Context, id: Long)
    {
        context.getSharedPreferences(MOJI_PREF_FILE, Context.MODE_PRIVATE).edit().putLong(MOJI_PREF_SELECTED_DICTIONARY, id).apply()
    }
}
