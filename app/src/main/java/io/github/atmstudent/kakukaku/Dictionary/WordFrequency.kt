package io.github.atmstudent.kakukaku.Dictionary

import android.content.Context
import io.github.atmstudent.kakukaku.Database.JmDictDatabase.Models.EntryOptimized
import io.github.atmstudent.kakukaku.toHiragana

/**
 * The selected frequency dictionary (imported by the user). It orders the results of a lookup: of two words that match
 * the same amount of text, the more common one comes first. Works with whichever word dictionary is selected.
 */
class WordFrequency private constructor(private val store: UserDictionaryStore, private val dictId: Long)
{
    /**
     * The rank of each entry (a smaller number is a more common word), for those the dictionary knows. An entry
     * has the rank of its most common reading, so 迚も, which is mostly written とても, counts as common as とても is.
     */
    fun ranks(entries: Collection<EntryOptimized>): Map<EntryOptimized, Double>
    {
        val stored = store.frequenciesFor(dictId, entries.map { it.kanji })
        val result = HashMap<EntryOptimized, Double>()

        for (entry in entries)
        {
            val rows = stored[entry.kanji] ?: continue
            val wanted = entry.readings.split(",")
                    .map { toHiragana(it.trim()) }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf(toHiragana(entry.kanji)) }
                    .toSet()

            val best = rows.filter { it.first.isEmpty() || it.first in wanted }.minOfOrNull { it.second } ?: continue
            result[entry] = best
        }

        return result
    }

    companion object
    {
        /** The selected frequency dictionary, or null if none is selected */
        fun get(context: Context): WordFrequency?
        {
            val id = MetaSelection.get(context, MetaKind.FREQUENCY)
            return if (id == MetaSelection.NONE) null else WordFrequency(UserDictionaryStore.get(context), id)
        }
    }
}
