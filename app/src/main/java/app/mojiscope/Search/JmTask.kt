package app.mojiscope.Search

import android.content.Context
import android.util.Log
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.RawRowMapper
import app.mojiscope.Dictionary.DictionarySelection
import app.mojiscope.Dictionary.PitchAccent
import app.mojiscope.Dictionary.UserDictionaryStore
import app.mojiscope.DB_KANJIDICT_NAME
import app.mojiscope.toHiragana
import app.mojiscope.toKatakana
import app.mojiscope.Database.JmDictDatabase.JmDatabaseHelper
import app.mojiscope.Database.JmDictDatabase.Models.EntryOptimized
import app.mojiscope.Deinflictor.DeinflectionInfo
import app.mojiscope.Deinflictor.Deinflector
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
        val entryOptimizedDao = mJmDbHelper.getDbDao<EntryOptimized>(EntryOptimized::class.java)

        val startDictTime = System.currentTimeMillis()
        val firstChar = String(intArrayOf(text.codePointAt(textOffset)), 0, 1)

        // What the flying fuck? Wasn't the entire point of using an ORM is so shit would be escaped for me?
        var character = firstChar.replace("%", "\\%")
        character = character.replace("_", "\\_")
        character = character.replace("'", "''")

        // An imported dictionary replaces the bundled word dictionary; kanji information always comes from the bundled one
        val selectedDictionary = DictionarySelection.get(mContext)
        val entries: List<EntryOptimized> = if (selectedDictionary == DictionarySelection.BUILT_IN)
        {
            queryBundled(entryOptimizedDao, character, true)
        }
        else
        {
            queryBundled(entryOptimizedDao, character, false).filter { it.dictionary == DB_KANJIDICT_NAME } +
                    UserDictionaryStore.get(mContext).search(selectedDictionary, firstChar)
        }

        val matchedEntries = rankResults(getMatchedEntries(text, textOffset, entries))
        loadMeanings(entryOptimizedDao, matchedEntries)

        // Pitch accent comes from the pitch accent dictionary the user imported, whichever word dictionary is selected
        if (PitchAccent.isEnabled(mContext))
        {
            val pitchAccent = PitchAccent.get(mContext)
            for (result in matchedEntries)
            {
                if (result.entry.dictionary != DB_KANJIDICT_NAME) result.pitch = pitchAccent.lookup(result.entry.kanji, result.entry.readings)
            }
        }
        Log.d(TAG, "Dict lookup time: ${System.currentTimeMillis() - startDictTime}")

        return matchedEntries
    }

    /**
     * The bundled entries that start with [character] (already escaped for LIKE) in their written form and, if
     * [byReading], in their reading too: とても is the reading of 迚も, which is hardly ever written in kanji.
     * Readings are stored as one text ("ねこ, ネコ"), so a reading that starts with the character is either at the
     * start of that text or after ", ". Kana can be written either way.
     *
     * Only the columns needed for matching and ranking are read; the long definitions of the thousands of entries
     * that merely share a first character would make the lookup slow. See [loadMeanings].
     */
    private fun queryBundled(dao: Dao<EntryOptimized, Int>, character: String, byReading: Boolean): List<EntryOptimized>
    {
        val starts = if (byReading) listOf(character, toHiragana(character), toKatakana(character)).distinct() else emptyList()
        val sql = StringBuilder("SELECT id, kanji, readings, pos, priorities, dictionary, primaryEntry FROM entryoptimized WHERE kanji LIKE ?")
        val args = arrayListOf("$character%")
        for (start in starts)
        {
            sql.append(" OR readings LIKE ? OR readings LIKE ?")
            args.add("$start%")
            args.add("%, $start%")
        }

        val mapper = RawRowMapper<EntryOptimized> { _, columns ->
            EntryOptimized().also {
                it.id = columns[0].toInt()
                it.kanji = columns[1]
                it.readings = columns[2]
                it.pos = columns[3]
                it.priorities = columns[4]
                it.dictionary = columns[5]
                it.isPrimaryEntry = columns[6] == "1" || columns[6] == "true"
            }
        }

        return dao.queryRaw(sql.toString(), mapper, *args.toTypedArray()).use { it.toList() }
    }

    /** Fills in the definitions of the matched bundled entries, which [queryBundled] left out */
    private fun loadMeanings(dao: Dao<EntryOptimized, Int>, results: List<JmSearchResult>)
    {
        for (result in results)
        {
            val entry = result.entry
            if (entry.meanings == null && entry.id != null)
            {
                entry.meanings = dao.queryForId(entry.id)?.meanings ?: ""
            }
        }
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
    private fun getMatchedEntries(text: String, textOffset: Int, entries: List<EntryOptimized>): List<JmSearchResult>
    {
        val index = EntryIndex(entries)
        val end = if (textOffset + 80 >= text.length) text.length else textOffset + 80
        var word = text.substring(textOffset, end)
        val seenEntries = HashSet<EntryOptimized>()
        val results = ArrayList<JmSearchResult>()

        while (word.isNotEmpty())
        {
            // Find deinflections and add them
            val deinfResultsList: List<DeinflectionInfo> = mDeinflector.getPotentialDeinflections(word)
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

            word = word.substring(0, word.length - 1)
        }

        return results
    }

    private fun rankResults(results: List<JmSearchResult>) : List<JmSearchResult>
    {
        return results.sortedWith(compareBy(
                { getDictPriority(it) },
                { 0 - it.word.length }, // how much of the text the entry matched, conjugation included
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
