package app.mojiscope.Dictionary

import android.content.Context
import app.mojiscope.MOJI_PREF_FILE
import app.mojiscope.MOJI_PREF_PITCH_ACCENT
import app.mojiscope.toHiragana

/**
 * The pitch accent dictionary the user has imported. It works alongside whichever word dictionary is selected:
 * the downstep positions of a word are shown after its reading, for example 猫 (ねこ) [1].
 */
class PitchAccent private constructor(private val store: UserDictionaryStore)
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
        for ((reading, stored) in store.pitchFor(term))
        {
            if (reading in wanted) positions.addAll(stored.split(","))
        }

        return positions.joinToString("") { "[$it]" }
    }

    companion object
    {
        fun get(context: Context): PitchAccent = PitchAccent(UserDictionaryStore.get(context))

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
