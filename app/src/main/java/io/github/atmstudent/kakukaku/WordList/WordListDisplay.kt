package io.github.atmstudent.kakukaku.WordList

import io.github.atmstudent.kakukaku.Anki.Marker
import io.github.atmstudent.kakukaku.LangUtils

/** A saved term as the popup shows it: the heading (dictionary form, kana, pitch accent) and the definitions */
object WordListDisplay
{
    /** "食べる (たべる) [2]"; the pitch accent only when [showPitch] */
    fun heading(values: Map<String, String>, showPitch: Boolean): String
    {
        val expression = values[Marker.EXPRESSION.id] ?: ""
        val reading = values[Marker.READING.id] ?: ""
        val sb = StringBuilder(expression)
        if (reading.isNotEmpty() && reading != expression) sb.append(" (").append(reading).append(")")

        if (showPitch)
        {
            val positions = (values[Marker.PITCH_ACCENT_POSITIONS.id] ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (positions.isNotEmpty()) sb.append(" ").append(positions.joinToString("") { "[$it]" })
        }
        return sb.toString()
    }

    /** "① (v1,vt) to eat ② (v1,vt) to live on", like under the heading in the popup */
    fun glossary(values: Map<String, String>): String
    {
        val html = values[Marker.GLOSSARY.id] ?: ""
        val items = Regex("<li>(.*?)</li>").findAll(html).map { HtmlText.plain(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList()
        if (items.isEmpty()) return HtmlText.plain(html)
        return items.withIndex().joinToString(" ") { (i, item) -> LangUtils.ConvertIntToCircledNum(i + 1) + " " + item }
    }
}
