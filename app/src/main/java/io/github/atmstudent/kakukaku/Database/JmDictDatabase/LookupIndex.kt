package io.github.atmstudent.kakukaku.Database.JmDictDatabase

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.github.atmstudent.kakukaku.DB_KANJIDICT_NAME
import io.github.atmstudent.kakukaku.toHiragana

/**
 * Indexes the bundled dictionary is searched by. They are made on the phone, the first time the database is
 * opened, so the shipped database file does not have to change: an index on the written form, and a table
 * with one row per reading (as hiragana), because the readings of an entry are stored as one text.
 */
object LookupIndex
{
    private const val TAG = "LookupIndex"

    fun ensure(db: SQLiteDatabase)
    {
        if (db.isReadOnly) return
        val start = System.currentTimeMillis()

        db.beginTransaction()
        try
        {
            db.execSQL("CREATE INDEX IF NOT EXISTS entry_kanji ON entryoptimized (kanji)")

            val hasReadings = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'entry_reading'", null).use { it.moveToFirst() }
            if (!hasReadings)
            {
                db.execSQL("CREATE TABLE entry_reading (reading TEXT NOT NULL, entry_id INTEGER NOT NULL)")
                val insert = db.compileStatement("INSERT INTO entry_reading (reading, entry_id) VALUES (?, ?)")
                db.rawQuery("SELECT id, readings FROM entryoptimized WHERE dictionary != ?", arrayOf(DB_KANJIDICT_NAME)).use { c ->
                    while (c.moveToNext())
                    {
                        val id = c.getLong(0)
                        val readings = c.getString(1) ?: continue
                        for (reading in readings.split(",").map { toHiragana(it.trim()) }.filter { it.isNotEmpty() }.distinct())
                        {
                            insert.clearBindings()
                            insert.bindString(1, reading)
                            insert.bindLong(2, id)
                            insert.executeInsert()
                        }
                    }
                }
                db.execSQL("CREATE INDEX entry_reading_lookup ON entry_reading (reading)")
                Log.d(TAG, "Built the reading index in ${System.currentTimeMillis() - start} ms")
            }
            db.setTransactionSuccessful()
        }
        finally
        {
            db.endTransaction()
        }
    }
}
