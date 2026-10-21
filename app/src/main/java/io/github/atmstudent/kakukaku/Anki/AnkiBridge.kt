package io.github.atmstudent.kakukaku.Anki

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

enum class AddResult { ADDED, DUPLICATE, FAILED }

/**
 * The part of AnkiDroid that cards are added through: its ContentProvider (the "AnkiDroid API", without the library).
 * Everything here reads or writes AnkiDroid's database, so none of it is for the main thread. A missing permission
 * throws a SecurityException; check [isInstalled] and [hasPermission] first.
 */
object AnkiBridge
{
    const val PACKAGE = "com.ichi2.anki"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private const val AUTHORITY = "com.ichi2.anki.flashcards"
    private val BASE: Uri = Uri.parse("content://$AUTHORITY")
    private val DECKS: Uri = Uri.withAppendedPath(BASE, "decks")
    private val MODELS: Uri = Uri.withAppendedPath(BASE, "models")
    private val NOTES: Uri = Uri.withAppendedPath(BASE, "notes")

    private const val FIELD_SEPARATOR = '\u001f'

    fun isInstalled(context: Context): Boolean
    {
        return try
        {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        }
        catch (e: PackageManager.NameNotFoundException)
        {
            false
        }
    }

    fun hasPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** The decks by id; a filtered deck can't be added to, so those AnkiDroid lists as dynamic are left out */
    fun decks(context: Context): Map<Long, String>
    {
        val result = LinkedHashMap<Long, String>()
        context.contentResolver.query(DECKS, arrayOf("deck_id", "deck_name"), null, null, null)?.use { c ->
            while (c.moveToNext()) result[c.getLong(0)] = c.getString(1)
        }
        return result.entries.sortedBy { it.value.lowercase() }.associate { it.key to it.value }
    }

    /** The note types by id */
    fun models(context: Context): Map<Long, String>
    {
        val result = LinkedHashMap<Long, String>()
        context.contentResolver.query(MODELS, arrayOf("_id", "name"), null, null, null)?.use { c ->
            while (c.moveToNext()) result[c.getLong(0)] = c.getString(1)
        }
        return result.entries.sortedBy { it.value.lowercase() }.associate { it.key to it.value }
    }

    /** The names of the fields of a note type, in their order */
    fun fieldNames(context: Context, modelId: Long): List<String>
    {
        context.contentResolver.query(Uri.withAppendedPath(MODELS, modelId.toString()), arrayOf("field_names"), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0).split(FIELD_SEPARATOR)
        }
        return emptyList()
    }

    /**
     * Adds a note of [modelId] with [fields] (in the order of the note type's fields) to [deckId]. A note whose first
     * field is the same as one in the note type is not added again, unless [allowDuplicate].
     */
    fun addNote(context: Context, modelId: Long, modelName: String, fieldNames: List<String>, deckId: Long, fields: List<String>, tags: Set<String>, allowDuplicate: Boolean = false): AddResult
    {
        val resolver = context.contentResolver

        if (!allowDuplicate && fieldNames.isNotEmpty() && isDuplicate(context, modelName, fieldNames[0], fields[0])) return AddResult.DUPLICATE

        val values = ContentValues()
        values.put("mid", modelId)
        values.put("flds", fields.joinToString(FIELD_SEPARATOR.toString()))
        if (tags.isNotEmpty()) values.put("tags", tags.joinToString(" "))
        val noteUri = resolver.insert(NOTES, values) ?: return AddResult.FAILED

        // The note starts in the default deck: move its cards to the chosen one
        val cardsUri = Uri.withAppendedPath(noteUri, "cards")
        resolver.query(cardsUri, null, null, null, null)?.use { cards ->
            val ordColumn = cards.getColumnIndex("ord")
            while (cards.moveToNext())
            {
                val move = ContentValues()
                move.put("deck_id", deckId)
                resolver.update(Uri.withAppendedPath(cardsUri, cards.getString(ordColumn)), move, null, null)
            }
        } ?: return AddResult.FAILED

        return AddResult.ADDED
    }

    /** Anki's own search: the first field is exactly this text, in this note type */
    private fun isDuplicate(context: Context, modelName: String, firstField: String, value: String): Boolean
    {
        if (value.isEmpty()) return false

        val query = "${quote(firstField)}:\"${escape(value)}\" note:\"${escape(modelName)}\""
        context.contentResolver.query(NOTES, arrayOf("_id"), query, null, null)?.use { return it.count > 0 }
        return false
    }

    private fun quote(fieldName: String) = if (fieldName.any { it == ' ' || it == ':' || it == '"' }) "\"${escape(fieldName)}\"" else fieldName

    /** The characters that mean something in Anki's search syntax */
    private fun escape(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("*", "\\*").replace("_", "\\_")
}
