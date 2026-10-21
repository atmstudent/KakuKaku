package io.github.atmstudent.kakukaku.Anki

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.atmstudent.kakukaku.KAKUKAKU_PREF_FILE

/** What is sent to Anki: the deck and note type, what goes into each field of the note type, and the tags */
object AnkiSettings
{
    private const val DECK_ID = "AnkiDeckId"
    private const val DECK_NAME = "AnkiDeckName"
    private const val MODEL_ID = "AnkiModelId"
    private const val MODEL_NAME = "AnkiModelName"
    private const val TEMPLATES = "AnkiTemplates"
    private const val TAGS = "AnkiTags"
    private const val SHOW_ANKI_ICON = "AnkiShowIcon"
    private const val SHOW_LIST_ICON = "WordListShowIcon"

    private fun prefs(context: Context) = context.getSharedPreferences(KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE)

    fun deckId(context: Context): Long = prefs(context).getLong(DECK_ID, -1)
    fun deckName(context: Context): String = prefs(context).getString(DECK_NAME, "") ?: ""
    fun setDeck(context: Context, id: Long, name: String) = prefs(context).edit().putLong(DECK_ID, id).putString(DECK_NAME, name).apply()

    fun modelId(context: Context): Long = prefs(context).getLong(MODEL_ID, -1)
    fun modelName(context: Context): String = prefs(context).getString(MODEL_NAME, "") ?: ""
    fun setModel(context: Context, id: Long, name: String) = prefs(context).edit().putLong(MODEL_ID, id).putString(MODEL_NAME, name).apply()

    /** A deck and a note type are chosen */
    fun isConfigured(context: Context): Boolean = deckId(context) >= 0 && modelId(context) >= 0

    /** The template of a field of the chosen note type; empty if nothing goes there */
    fun template(context: Context, fieldName: String): String = templates(context)[templateKey(context, fieldName)] ?: ""

    fun setTemplate(context: Context, fieldName: String, template: String)
    {
        val all = templates(context).toMutableMap()
        all[templateKey(context, fieldName)] = template
        prefs(context).edit().putString(TEMPLATES, Gson().toJson(all)).apply()
    }

    /** The tags added to every note, separated by spaces */
    fun tags(context: Context): String = prefs(context).getString(TAGS, "kakukaku") ?: ""
    fun setTags(context: Context, tags: String) = prefs(context).edit().putString(TAGS, tags).apply()

    fun showAnkiIcon(context: Context): Boolean = prefs(context).getBoolean(SHOW_ANKI_ICON, true)
    fun setShowAnkiIcon(context: Context, show: Boolean) = prefs(context).edit().putBoolean(SHOW_ANKI_ICON, show).apply()

    fun showListIcon(context: Context): Boolean = prefs(context).getBoolean(SHOW_LIST_ICON, true)
    fun setShowListIcon(context: Context, show: Boolean) = prefs(context).edit().putBoolean(SHOW_LIST_ICON, show).apply()

    /** The fields of [fieldNames], filled with the markers of [values] */
    fun fillFields(context: Context, fieldNames: List<String>, values: Map<String, String>): List<String> =
            fieldNames.map { TemplateFiller.fill(template(context, it), values) }

    /** Templates are kept per note type, so changing the note type does not lose what was set for another */
    private fun templateKey(context: Context, fieldName: String) = "${modelId(context)}/$fieldName"

    private fun templates(context: Context): Map<String, String>
    {
        val json = prefs(context).getString(TEMPLATES, null) ?: return emptyMap()
        return try { Gson().fromJson(json, object : TypeToken<Map<String, String>>() {}.type) } catch (e: Exception) { emptyMap() }
    }
}
