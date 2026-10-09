package io.github.atmstudent.kakukaku.Dictionary

import android.content.Context
import io.github.atmstudent.kakukaku.toHiragana

/**
 * The selected pitch accent dictionary (imported by the user). It works alongside whichever word dictionary is selected:
 * the downstep positions of a word are shown after its reading, for example 猫 (ねこ) [1].
 */
class PitchAccent private constructor(private val store: UserDictionaryStore, private val dictId: Long)
{
    /**
     * Downstep positions of a word, formatted like "[1]" or "[1][0]" for words with several accents.
     * [readings] is the entry's reading text ("ねこ, ネコ"); empty for words written in kana only.
     */
    fun lookup(term: String, readings: String): String
    {
        val wanted = readings.split(",")
                .map { toHiragana(it.trim()) }
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf(toHiragana(term)) }
                .toSet()

        val positions = LinkedHashSet<String>()
        for ((reading, stored) in store.pitchFor(dictId, term))
        {
            if (reading in wanted) positions.addAll(stored.split(","))
        }

        return positions.joinToString("") { "[$it]" }
    }

    companion object
    {
        /** The selected pitch accent dictionary, or null if none is selected */
        fun get(context: Context): PitchAccent?
        {
            val id = MetaSelection.get(context, MetaKind.PITCH)
            return if (id == MetaSelection.NONE) null else PitchAccent(UserDictionaryStore.get(context), id)
        }
    }
}
