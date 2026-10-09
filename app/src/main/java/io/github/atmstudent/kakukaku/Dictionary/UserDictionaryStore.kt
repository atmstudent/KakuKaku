package io.github.atmstudent.kakukaku.Dictionary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import android.util.Log
import io.github.atmstudent.kakukaku.DB_SPLIT_CHAR
import io.github.atmstudent.kakukaku.Database.JmDictDatabase.Models.EntryOptimized
import io.github.atmstudent.kakukaku.KAKUKAKU_PREF_FILE
import io.github.atmstudent.kakukaku.KAKUKAKU_PREF_PITCH_ACCENT
import io.github.atmstudent.kakukaku.KAKUKAKU_PREF_SELECTED_DICTIONARY
import io.github.atmstudent.kakukaku.Search.JmTask
import io.github.atmstudent.kakukaku.toKatakana
import java.io.InputStream

data class UserDictionary(val id: Long, val title: String, val revision: String, val entries: Int)

data class ImportResult(val dictionary: UserDictionary, val replacedIds: List<Long>)

/** The two kinds of data that come with a word dictionary: pitch accent and word frequency. Each can hold several dictionaries. */
enum class MetaKind(val table: String, val sources: String, val oldSources: String, val index: String, val valueColumn: String, val valueType: String)
{
    PITCH("pitch", "pitch_dicts", "pitch_source", "pitch_lookup", "positions", "TEXT"),
    FREQUENCY("freq", "freq_dicts", "freq_source", "freq_lookup", "rank", "REAL")
}

/**
 * Dictionaries imported by the user (Yomitan format). Kept in their own database so the bundled
 * dictionary is never touched.
 */
class UserDictionaryStore private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, "user_dictionaries.db", null, 5)
{
    init
    {
        // In write-ahead logging mode readers never wait for the big import transaction: they see the
        // last committed state. Without it, opening the dictionary screen mid-import blocks (and shows "not responding").
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase)
    {
        db.execSQL("CREATE TABLE dictionaries (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, revision TEXT NOT NULL, entries INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE terms (dict_id INTEGER NOT NULL, term TEXT NOT NULL, reading TEXT NOT NULL, tags TEXT NOT NULL, rules TEXT NOT NULL, score INTEGER NOT NULL, sequence INTEGER NOT NULL, glossary TEXT NOT NULL)")
        db.execSQL("CREATE INDEX terms_lookup ON terms (dict_id, term)")
        db.execSQL("CREATE INDEX terms_reading ON terms (dict_id, reading)")
        for (kind in MetaKind.values()) createMetaTables(db, kind)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
    {
        if (oldVersion < 3) db.execSQL("CREATE INDEX IF NOT EXISTS terms_reading ON terms (dict_id, reading)")
        if (oldVersion < 5)
        {
            // Until version 5 there was one pitch and one frequency dictionary (versions 2 and 4); now each is a list
            for (kind in MetaKind.values()) upgradeMetaTables(db, kind)
        }
    }

    private fun createMetaTables(db: SQLiteDatabase, kind: MetaKind)
    {
        db.execSQL("CREATE TABLE ${kind.table} (dict_id INTEGER NOT NULL, term TEXT NOT NULL, reading TEXT NOT NULL, ${kind.valueColumn} ${kind.valueType} NOT NULL)")
        db.execSQL("CREATE TABLE ${kind.sources} (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, entries INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX ${kind.index} ON ${kind.table} (dict_id, term)")
    }

    /** Keeps what was imported: the single old dictionary becomes the one with id 1 */
    private fun upgradeMetaTables(db: SQLiteDatabase, kind: MetaKind)
    {
        val hadOld = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(kind.oldSources)).use { it.moveToFirst() }
        if (!hadOld)
        {
            createMetaTables(db, kind)
            return
        }

        db.execSQL("ALTER TABLE ${kind.table} ADD COLUMN dict_id INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE TABLE ${kind.sources} (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, entries INTEGER NOT NULL)")
        db.execSQL("INSERT INTO ${kind.sources} (id, title, entries) SELECT 1, title, entries FROM ${kind.oldSources}")
        db.execSQL("DROP TABLE ${kind.oldSources}")
        db.execSQL("DROP INDEX IF EXISTS ${kind.index}")
        db.execSQL("CREATE INDEX ${kind.index} ON ${kind.table} (dict_id, term)")
    }

    /** The imported pitch accent or frequency dictionaries */
    fun metaList(kind: MetaKind): List<UserDictionary>
    {
        val result = ArrayList<UserDictionary>()
        readableDatabase.rawQuery("SELECT id, title, entries FROM ${kind.sources} ORDER BY title COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) result.add(UserDictionary(c.getLong(0), c.getString(1), "", c.getInt(2)))
        }
        return result
    }

    fun deleteMeta(kind: MetaKind, id: Long)
    {
        val db = writableDatabase
        db.beginTransaction()
        try
        {
            db.delete(kind.table, "dict_id = ?", arrayOf(id.toString()))
            db.delete(kind.sources, "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        }
        finally
        {
            db.endTransaction()
        }
    }

    /** The ranks [dictId] stores for each of [terms], as pairs of hiragana reading (empty if none was given) and rank */
    fun frequenciesFor(dictId: Long, terms: Collection<String>): Map<String, List<Pair<String, Double>>>
    {
        val result = HashMap<String, MutableList<Pair<String, Double>>>()
        for (chunk in terms.distinct().chunked(JmTask.SQL_CHUNK))
        {
            val args = arrayOf(dictId.toString()) + chunk.toTypedArray()
            readableDatabase.rawQuery("SELECT term, reading, rank FROM freq WHERE dict_id = ? AND term IN (${JmTask.placeholders(chunk.size)})", args).use { c ->
                while (c.moveToNext()) result.getOrPut(c.getString(0)) { ArrayList() }.add(Pair(c.getString(1), c.getDouble(2)))
            }
        }
        return result
    }

    /** Downstep positions [dictId] stores for [term], as pairs of hiragana reading and comma separated positions */
    fun pitchFor(dictId: Long, term: String): List<Pair<String, String>>
    {
        val result = ArrayList<Pair<String, String>>()
        readableDatabase.rawQuery("SELECT reading, positions FROM pitch WHERE dict_id = ? AND term = ?", arrayOf(dictId.toString(), term)).use { c ->
            while (c.moveToNext()) result.add(Pair(c.getString(0), c.getString(1)))
        }
        return result
    }

    /** Imports a Yomitan frequency zip. One transaction, so a failed import leaves nothing behind. A copy with the same title is replaced. */
    fun importFrequency(openStream: () -> InputStream, onProgress: (entries: Int, bytesRead: Long) -> Unit, onFinishing: () -> Unit = {}): UserDictionary
    {
        return importMeta<ImportedFrequency>(MetaKind.FREQUENCY, openStream, onProgress, onFinishing,
                { onBytes, onRows -> YomitanParser.parseFrequency(openStream, onBytes, onRows) },
                { statement, row ->
                    statement.bindString(2, row.term)
                    statement.bindString(3, row.reading)
                    statement.bindDouble(4, row.rank)
                })
    }

    /** Imports a Yomitan pitch accent zip, like [importFrequency] */
    fun importPitch(openStream: () -> InputStream, onProgress: (entries: Int, bytesRead: Long) -> Unit, onFinishing: () -> Unit = {}): UserDictionary
    {
        return importMeta<ImportedPitch>(MetaKind.PITCH, openStream, onProgress, onFinishing,
                { onBytes, onRows -> YomitanParser.parsePitch(openStream, onBytes, onRows) },
                { statement, row ->
                    statement.bindString(2, row.term)
                    statement.bindString(3, row.reading)
                    statement.bindString(4, row.positions)
                })
    }

    private fun <T> importMeta(kind: MetaKind, openStream: () -> InputStream, onProgress: (entries: Int, bytesRead: Long) -> Unit, onFinishing: () -> Unit,
                               parse: ((Long) -> Unit, (List<T>) -> Unit) -> Pair<DictionaryMeta, Int>,
                               bind: (SQLiteStatement, T) -> Unit): UserDictionary
    {
        val db = writableDatabase
        db.beginTransaction()
        try
        {
            db.execSQL("DROP INDEX IF EXISTS ${kind.index}")

            // Reads the title first so an unusable file fails before anything is written
            val created = ContentValues()
            created.put("title", YomitanParser.readIndex(openStream).title)
            created.put("entries", 0)
            val dictId = db.insertOrThrow(kind.sources, null, created)

            val statement = db.compileStatement("INSERT INTO ${kind.table} (dict_id, term, reading, ${kind.valueColumn}) VALUES (?, ?, ?, ?)")
            var imported = 0
            var bytesRead = 0L

            val (meta, count) = parse({ bytesRead = it }) { rows ->
                for (row in rows)
                {
                    statement.clearBindings()
                    statement.bindLong(1, dictId)
                    bind(statement, row)
                    statement.executeInsert()
                }
                imported += rows.size
                onProgress(imported, bytesRead)
            }

            val update = ContentValues()
            update.put("title", meta.title)
            update.put("entries", count)
            db.update(kind.sources, update, "id = ?", arrayOf(dictId.toString()))

            // Replace older copies of the same dictionary
            val replaced = ArrayList<Long>()
            db.rawQuery("SELECT id, title FROM ${kind.sources} WHERE id != ?", arrayOf(dictId.toString())).use { c ->
                while (c.moveToNext())
                {
                    if (sameDictionary(c.getString(1), meta.title)) replaced.add(c.getLong(0))
                }
            }
            for (old in replaced)
            {
                db.delete(kind.table, "dict_id = ?", arrayOf(old.toString()))
                db.delete(kind.sources, "id = ?", arrayOf(old.toString()))
            }

            onFinishing()
            db.execSQL("CREATE INDEX ${kind.index} ON ${kind.table} (dict_id, term)")
            db.setTransactionSuccessful()
            return UserDictionary(dictId, meta.title, "", count)
        }
        finally
        {
            db.endTransaction()
        }
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
     * @param onProgress called with the number of entries imported so far and the bytes of the zip read so far
     * @param onFinishing called when all entries are read and the index is being built
     */
    fun import(openStream: () -> InputStream, onProgress: (entries: Int, bytesRead: Long) -> Unit, onFinishing: () -> Unit = {}): ImportResult
    {
        val db = writableDatabase
        val startTime = System.nanoTime()
        var insertNanos = 0L

        db.beginTransaction()
        try
        {
            var imported = 0

            // Building the index once at the end is much faster than maintaining it for every inserted row
            db.execSQL("DROP INDEX IF EXISTS terms_lookup")
            db.execSQL("DROP INDEX IF EXISTS terms_reading")

            // Reads the title first so an unusable file fails before anything is written
            val title = YomitanParser.readIndex(openStream).title
            val created = ContentValues()
            created.put("title", title)
            created.put("revision", "")
            created.put("entries", 0)
            val dictId = db.insertOrThrow("dictionaries", null, created)

            val statement = db.compileStatement("INSERT INTO terms (dict_id, term, reading, tags, rules, score, sequence, glossary) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")

            var bytesRead = 0L

            val (meta, count) = YomitanParser.parse(openStream, { bytesRead = it }) { rows ->
                val insertStart = System.nanoTime()
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

                insertNanos += System.nanoTime() - insertStart
                imported += rows.size
                onProgress(imported, bytesRead)
            }

            val update = ContentValues()
            update.put("title", meta.title)
            update.put("revision", meta.revision)
            update.put("entries", count)
            db.update("dictionaries", update, "id = ?", arrayOf(dictId.toString()))

            // Replace older copies of the same dictionary
            val replaced = ArrayList<Long>()
            db.rawQuery("SELECT id, title FROM dictionaries WHERE id != ?", arrayOf(dictId.toString())).use { c ->
                while (c.moveToNext())
                {
                    if (sameDictionary(c.getString(1), meta.title)) replaced.add(c.getLong(0))
                }
            }
            for (old in replaced)
            {
                db.delete("terms", "dict_id = ?", arrayOf(old.toString()))
                db.delete("dictionaries", "id = ?", arrayOf(old.toString()))
            }

            onFinishing()
            db.execSQL("CREATE INDEX terms_lookup ON terms (dict_id, term)")
            db.execSQL("CREATE INDEX terms_reading ON terms (dict_id, reading)")

            db.setTransactionSuccessful()

            val totalMs = (System.nanoTime() - startTime) / 1_000_000
            Log.d(TAG, "Imported $count entries of \"${meta.title}\" in $totalMs ms (inserting: ${insertNanos / 1_000_000} ms)")
            return ImportResult(UserDictionary(dictId, meta.title, meta.revision, count), replaced)
        }
        finally
        {
            db.endTransaction()
        }
    }

    /**
     * Daily builds put the date in the title ("JMdict [2026-10-03]"), so a trailing [...] is ignored
     * when deciding whether an imported dictionary is a newer copy of an installed one.
     */
    private fun sameDictionary(a: String, b: String): Boolean
    {
        val suffix = Regex("\\s*\\[[^\\]]*\\]\\s*$")
        return a.replace(suffix, "").trim().equals(b.replace(suffix, "").trim(), ignoreCase = true)
    }

    /**
     * Entries of [dictionaryId] written as one of [candidates] or read as one of them, shaped like the bundled
     * dictionary's entries so the existing matching and display code can use them. Both lookups use an index.
     * Kana can be written either way, so the reading is looked up as hiragana and as katakana.
     */
    fun search(dictionaryId: Long, candidates: Collection<String>): List<EntryOptimized>
    {
        val title = list().firstOrNull { it.id == dictionaryId }?.title ?: return emptyList()

        class Group(val term: String, val reading: String, val sequence: Long, val matchedTerm: Boolean, val senses: MutableList<String>, val tags: MutableList<String>, val rules: MutableSet<String>)

        val groups = LinkedHashMap<String, Group>()
        var unique = 0L
        val seenRows = HashSet<String>()
        val written = candidates.toHashSet()
        val readingCandidates = JmTask.readingKeys(candidates).flatMap { listOf(it, toKatakana(it)) }.distinct()

        fun collect(where: String, args: List<String>)
        {
            readableDatabase.rawQuery(
                    "SELECT term, reading, tags, rules, sequence, glossary FROM terms WHERE dict_id = ? AND $where ORDER BY score DESC",
                    (listOf(dictionaryId.toString()) + args).toTypedArray()).use { c ->
                while (c.moveToNext())
                {
                    val term = c.getString(0)
                    val reading = c.getString(1)
                    val sequence = c.getLong(4)

                    // A row that both lookups find is added once
                    val glossary = c.getString(5)
                    if (!seenRows.add("$term\u0000$reading\u0000$sequence\u0000$glossary")) continue

                    // Rows with the same sequence number are the separate senses of one word
                    val key = if (sequence != 0L) "$term\u0000$reading\u0000$sequence" else "u${unique++}"
                    val group = groups.getOrPut(key) { Group(term, reading, sequence, term in written, ArrayList(), ArrayList(), LinkedHashSet()) }

                    group.senses.add(glossary)
                    group.tags.add(c.getString(2))
                    c.getString(3).split(" ").filter { it.isNotEmpty() }.forEach { group.rules.add(it) }
                }
            }
        }

        for (chunk in written.chunked(JmTask.SQL_CHUNK)) collect("term IN (${JmTask.placeholders(chunk.size)})", chunk)
        for (chunk in readingCandidates.chunked(JmTask.SQL_CHUNK)) collect("reading IN (${JmTask.placeholders(chunk.size)})", chunk)

        // The Yomitan build of JMdict has a kana-only row for words that are usually written in kana. Where one
        // was found by its written form, the kanji row of the same word (same sequence) found by its reading is a duplicate.
        val kanaSequences = groups.values.filter { it.matchedTerm && (it.reading.isEmpty() || it.term == it.reading) && it.sequence != 0L }.map { it.sequence }.toSet()
        val kept = groups.values.filter { it.matchedTerm || it.sequence == 0L || it.sequence !in kanaSequences }

        return kept.map {
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
        private const val TAG = "UserDictionaryStore"

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
        val id = context.getSharedPreferences(KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE).getLong(KAKUKAKU_PREF_SELECTED_DICTIONARY, BUILT_IN)
        if (id == BUILT_IN) return BUILT_IN

        // Fall back to the bundled dictionary if the selected one has been deleted
        return if (UserDictionaryStore.get(context).list().any { it.id == id }) id else BUILT_IN
    }

    fun set(context: Context, id: Long)
    {
        context.getSharedPreferences(KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE).edit().putLong(KAKUKAKU_PREF_SELECTED_DICTIONARY, id).apply()
    }
}

/** Which pitch accent or frequency dictionary is in use; [NONE] switches the feature off */
object MetaSelection
{
    const val NONE = 0L

    private fun key(kind: MetaKind) = when (kind)
    {
        MetaKind.PITCH -> "SelectedPitch"
        MetaKind.FREQUENCY -> "SelectedFrequency"
    }

    fun get(context: Context, kind: MetaKind): Long
    {
        val prefs = context.getSharedPreferences(KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE)
        val list = UserDictionaryStore.get(context).metaList(kind)

        if (!prefs.contains(key(kind)))
        {
            // Nothing chosen yet. Before there were several, the one imported dictionary was always in use (pitch accent
            // unless its switch was off), so carry on with it
            if (kind == MetaKind.PITCH && !prefs.getBoolean(KAKUKAKU_PREF_PITCH_ACCENT, true)) return NONE
            return list.firstOrNull()?.id ?: NONE
        }

        // Fall back to none if the chosen dictionary has been deleted
        val id = prefs.getLong(key(kind), NONE)
        return if (id == NONE || list.any { it.id == id }) id else NONE
    }

    fun set(context: Context, kind: MetaKind, id: Long)
    {
        context.getSharedPreferences(KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE).edit().putLong(key(kind), id).apply()
    }
}
