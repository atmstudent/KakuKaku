package io.github.atmstudent.kakukaku.Search

import android.content.Context
import android.util.Log
import android.database.sqlite.SQLiteDatabase
import io.github.atmstudent.kakukaku.Dictionary.DictionarySelection
import io.github.atmstudent.kakukaku.Dictionary.PitchAccent
import io.github.atmstudent.kakukaku.Dictionary.UserDictionaryStore
import io.github.atmstudent.kakukaku.Dictionary.WordFrequency
import io.github.atmstudent.kakukaku.DB_KANJIDICT_NAME
import io.github.atmstudent.kakukaku.toHiragana
import io.github.atmstudent.kakukaku.Database.JmDictDatabase.JmDatabaseHelper
import io.github.atmstudent.kakukaku.Database.JmDictDatabase.Models.EntryOptimized
import io.github.atmstudent.kakukaku.Deinflictor.DeinflectionInfo
import io.github.atmstudent.kakukaku.Deinflictor.Deinflector
import java.sql.SQLException
import java.util.ArrayList
import kotlin.collections.HashSet
import kotlin.collections.List
import kotlin.collections.filter
import kotlin.collections.sortedWith

/**
 * Created by 0xbad1d3a5 on 12/16/2016.
 */

class JmTask @Throws(SQLException::class)
constructor(private val mSearchInfo: SearchInfo, private val mSearchJmTaskDone: SearchJmTaskDone, context: Context) : BackgroundTask<List<JmSearchResult>>()
{
    private val mContext: Context = context.applicationContext

    companion object
    {
        private val TAG = JmTask::class.java.getName()

        /** Stays below SQLite's limit of 999 variables per statement */
        const val SQL_CHUNK = 500

        fun placeholders(count: Int) = List(count) { "?" }.joinToString(",")

        /** Readings are only kana, so only words that are kana can match one; kept as hiragana, which is how they are stored */
        fun readingKeys(words: Collection<String>): List<String> =
                words.filter { w -> w.isNotEmpty() && w.all { it in '\u3041'..'\u30ff' } }.map { toHiragana(it) }.distinct()
    }

    private val mJmDbHelper: JmDatabaseHelper = JmDatabaseHelper.instance(context)
    private val mDeinflector: Deinflector = Deinflector(context)

    interface SearchJmTaskDone
    {
        fun jmTaskCallback(results: List<JmSearchResult>, searchInfo: SearchInfo)
    }

    override fun doInBackground(): List<JmSearchResult>
    {
        val text = mSearchInfo.text
        val textOffset = mSearchInfo.textOffset

        val startDictTime = System.currentTimeMillis()

        // Every prefix of the text can be a word, and so can what it deinflects to; only these are looked up
        val end = if (textOffset + 80 >= text.length) text.length else textOffset + 80
        val words = ArrayList<String>()
        var word = text.substring(textOffset, end)
        while (word.isNotEmpty())
        {
            words.add(word)
            word = word.substring(0, word.length - 1)
        }
        val deinflections = words.associateWith { mDeinflector.getPotentialDeinflections(it) }
        val candidates = LinkedHashSet<String>(words)
        for (list in deinflections.values) for (deinf in list) candidates.add(deinf.word)

        val db = mJmDbHelper.readableDatabase

        // An imported dictionary replaces the bundled word dictionary; kanji information always comes from the bundled one
        val selectedDictionary = DictionarySelection.get(mContext)
        val entries: List<EntryOptimized> = if (selectedDictionary == DictionarySelection.BUILT_IN)
        {
            queryBundled(db, candidates, true)
        }
        else
        {
            queryBundled(db, candidates, false).filter { it.dictionary == DB_KANJIDICT_NAME } +
                    UserDictionaryStore.get(mContext).search(selectedDictionary, candidates)
        }
        val queryTime = System.currentTimeMillis() - startDictTime

        val matchedEntries = getMatchedEntries(words, deinflections, entries)

        // Frequency comes from the frequency dictionary the user selected, whichever word dictionary is selected
        WordFrequency.get(mContext)?.let { wordFrequency ->
            val ranks = wordFrequency.ranks(matchedEntries.map { it.entry }.filter { it.dictionary != DB_KANJIDICT_NAME })
            for (result in matchedEntries) result.frequency = ranks[result.entry]
        }

        val rankedEntries = rankResults(matchedEntries)
        loadMeanings(db, rankedEntries)

        // Pitch accent comes from the pitch accent dictionary the user selected, whichever word dictionary is selected
        PitchAccent.get(mContext)?.let { pitchAccent ->
            for (result in rankedEntries)
            {
                if (result.entry.dictionary != DB_KANJIDICT_NAME) result.pitch = pitchAccent.lookup(result.entry.kanji, result.entry.readings)
            }
        }
        Log.d(TAG, "Dict lookup time: ${System.currentTimeMillis() - startDictTime} (query $queryTime, ${candidates.size} candidates, ${entries.size} entries)")

        return rankedEntries
    }

    /**
     * The bundled entries written as one of [candidates] and, if [byReading], read as one of them: とても is the
     * reading of 迚も, which is hardly ever written in kanji. Both lookups use an index ([LookupIndex]), and
     * kana can be written either way.
     *
     * Only the columns needed for matching and ranking are read: the definitions are long. See [loadMeanings].
     */
    private fun queryBundled(db: SQLiteDatabase, candidates: Collection<String>, byReading: Boolean): List<EntryOptimized>
    {
        val columns = "e.id, e.kanji, e.readings, e.pos, e.priorities, e.dictionary, e.primaryEntry"
        val found = LinkedHashMap<Int, EntryOptimized>()

        fun collect(sql: String, args: List<String>)
        {
            db.rawQuery(sql, args.toTypedArray()).use { c ->
                while (c.moveToNext())
                {
                    val id = c.getInt(0)
                    if (found.containsKey(id)) continue
                    found[id] = EntryOptimized().also {
                        it.id = id
                        it.kanji = c.getString(1)
                        it.readings = c.getString(2)
                        it.pos = c.getString(3)
                        it.priorities = c.getString(4)
                        it.dictionary = c.getString(5)
                        it.isPrimaryEntry = c.getInt(6) != 0
                    }
                }
            }
        }

        for (chunk in candidates.chunked(SQL_CHUNK))
        {
            collect("SELECT $columns FROM entryoptimized e WHERE e.kanji IN (${placeholders(chunk.size)})", chunk)
        }
        if (byReading)
        {
            for (chunk in readingKeys(candidates).chunked(SQL_CHUNK))
            {
                collect("SELECT $columns FROM entry_reading r JOIN entryoptimized e ON e.id = r.entry_id WHERE r.reading IN (${placeholders(chunk.size)})", chunk)
            }
        }
        return found.values.toList()
    }

    /** Fills in the definitions of the matched bundled entries, which [queryBundled] left out */
    private fun loadMeanings(db: SQLiteDatabase, results: List<JmSearchResult>)
    {
        val missing = results.map { it.entry }.filter { it.meanings == null && it.id != null }.associateBy { it.id }
        for (chunk in missing.keys.chunked(SQL_CHUNK))
        {
            db.rawQuery("SELECT id, meanings FROM entryoptimized WHERE id IN (${placeholders(chunk.size)})", chunk.map { it.toString() }.toTypedArray()).use { c ->
                while (c.moveToNext()) missing[c.getInt(0)]?.meanings = c.getString(1) ?: ""
            }
        }
        for (entry in missing.values) if (entry.meanings == null) entry.meanings = ""
    }

    override fun onPostExecute(result: List<JmSearchResult>)
    {
        mSearchJmTaskDone.jmTaskCallback(result, mSearchInfo)
    }

    /** The entries by written form and, as hiragana, by reading; kanji entries have no readings worth searching */
    private class EntryIndex(entries: List<EntryOptimized>)
    {
        private val byWritten = HashMap<String, MutableList<EntryOptimized>>()
        private val byReading = HashMap<String, MutableList<EntryOptimized>>()

        init
        {
            for (entry in entries)
            {
                byWritten.getOrPut(entry.kanji) { ArrayList() }.add(entry)

                if (entry.dictionary == DB_KANJIDICT_NAME) continue
                for (reading in entry.readings.split(",").map { toHiragana(it.trim()) }.filter { it.isNotEmpty() }.distinct())
                {
                    byReading.getOrPut(reading) { ArrayList() }.add(entry)
                }
            }
        }

        /** Entries written as [word] first, then those read as [word] */
        fun find(word: String): List<EntryOptimized>
        {
            val written = byWritten[word] ?: emptyList()
            val read = byReading[toHiragana(word)] ?: return written
            return if (written.isEmpty()) read else written + read.filter { it !in written }
        }
    }

    @Throws(SQLException::class)
    private fun getMatchedEntries(words: List<String>, deinflections: Map<String, List<DeinflectionInfo>>, entries: List<EntryOptimized>): List<JmSearchResult>
    {
        val index = EntryIndex(entries)
        val seenEntries = HashSet<EntryOptimized>()
        val results = ArrayList<JmSearchResult>()

        for (word in words)
        {
            // Find deinflections and add them
            val deinfResultsList: List<DeinflectionInfo> = deinflections.getValue(word)
            var count = 0
            for (deinfInfo in deinfResultsList)
            {
                val filteredEntry: List<EntryOptimized> = index.find(deinfInfo.word)

                if (filteredEntry.isEmpty())
                {
                    continue
                }

                for (entry in filteredEntry){

                    if (seenEntries.contains(entry)){
                        continue
                    }

                    var valid = true

                    if (count > 0)
                    {
                        val posAndRules = entry.pos + " " + entry.rules
                        valid = (deinfInfo.type and 1 != 0) && (posAndRules.contains("v1")) ||
                                (deinfInfo.type and 2 != 0) && (posAndRules.contains("v5")) ||
                                (deinfInfo.type and 4 != 0) && (posAndRules.contains("adj-i")) ||
                                (deinfInfo.type and 8 != 0) && (posAndRules.contains("vk")) ||
                                (deinfInfo.type and 16 != 0) && (posAndRules.contains("vs"))
                    }

                    if (valid){
                        results.add(JmSearchResult(entry, deinfInfo, word))
                        seenEntries.add(entry)
                    }

                    count++
                }
            }

            // Add all exact matches as well
            val filteredEntry: List<EntryOptimized> = index.find(word)
            for (entry in filteredEntry)
            {
                if (seenEntries.contains(entry))
                {
                    continue
                }

                results.add(JmSearchResult(entry, DeinflectionInfo(word, 0, ""), word))
                seenEntries.add(entry)
            }
        }

        return results
    }

    private fun rankResults(results: List<JmSearchResult>) : List<JmSearchResult>
    {
        return results.sortedWith(compareBy(
                { getDictPriority(it) },
                { 0 - it.word.length }, // how much of the text the entry matched, conjugation included
                { it.frequency ?: Double.MAX_VALUE }, // the imported frequency dictionary, if any; words it lacks come last
                { getEntryPriority(it) },
                { getPriority(it) }))
    }

    private fun getDictPriority(result: JmSearchResult) : Int
    {
        return when
        {
            result.entry.dictionary == DB_KANJIDICT_NAME -> Int.MAX_VALUE - 1
            else -> Int.MAX_VALUE - 2
        }
    }

    private fun getEntryPriority(result: JmSearchResult) : Int
    {
        return if (result.entry.isPrimaryEntry) 0 else 1
    }

    private fun getPriority(result: JmSearchResult) : Int
    {
        val priorities = result.entry.priorities.split(",")
        var lowestPriority = Int.MAX_VALUE

        for (priority in priorities){

            var pri = Int.MAX_VALUE

            if (priority.contains("nf")){ // looks like the range is nf01-nf48
                pri = priority.substring(2).toInt()
            }
            else if (priority == "news1"){
                pri = 60
            }
            else if (priority == "news2"){
                pri = 70
            }
            else if (priority == "ichi1"){
                pri = 80
            }
            else if (priority == "ichi2"){
                pri = 90
            }
            else if (priority == "spec1"){
                pri = 100
            }
            else if (priority == "spec2"){
                pri = 110
            }
            else if (priority == "gai1"){
                pri = 120
            }
            else if (priority == "gai2"){
                pri = 130
            }

            lowestPriority = if (pri < lowestPriority) pri else lowestPriority
        }

        return lowestPriority
    }
}
