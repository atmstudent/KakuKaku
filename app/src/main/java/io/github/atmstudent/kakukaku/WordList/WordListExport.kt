package io.github.atmstudent.kakukaku.WordList

import io.github.atmstudent.kakukaku.Anki.Marker

/** The saved words as a table for a file: a header row of marker names, then one row per term */
object WordListExport
{
    /** [plainText]: turns the HTML of the values (definitions, ruby) into text */
    fun table(terms: List<SavedTerm>, markers: List<Marker>, plainText: Boolean): List<List<String>>
    {
        val rows = ArrayList<List<String>>()
        rows.add(markers.map { it.id })
        for (term in terms)
        {
            rows.add(markers.map { marker ->
                val value = term.values[marker.id] ?: ""
                if (plainText) HtmlText.plain(value) else value
            })
        }
        return rows
    }

    /** Tab separated, one line per row; tabs and line breaks inside a value become spaces (what Anki's import expects) */
    fun tsv(rows: List<List<String>>): String =
            rows.joinToString("\n", postfix = "\n") { row -> row.joinToString("\t") { it.replace(Regex("[\\t\\r\\n]+"), " ") } }

    /** Comma separated with quotes where needed (RFC 4180) */
    fun csv(rows: List<List<String>>): String =
            rows.joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { quote(it) } }

    private fun quote(value: String): String =
            if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

/** HTML of the values as plain text */
object HtmlText
{
    fun plain(html: String): String
    {
        if (html.isEmpty() || !html.contains('<')) return unescape(html)

        var text = html
        text = Regex("<ruby>(.*?)<rt>(.*?)</rt></ruby>").replace(text) { "${it.groupValues[1]}[${it.groupValues[2]}]" }
        // A list of definitions: "1. (v1) to eat; 2. to live on"
        if (text.startsWith("<ol>"))
        {
            var n = 0
            text = Regex("<li>(.*?)</li>").replace(text.removePrefix("<ol>").removeSuffix("</ol>")) { "${++n}. ${it.groupValues[1]}; " }.trimEnd(';', ' ')
        }
        text = Regex("<br\\s*/?>").replace(text, " ")
        text = Regex("<[^>]*>").replace(text, "")
        return unescape(text).trim()
    }

    private fun unescape(text: String) = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
