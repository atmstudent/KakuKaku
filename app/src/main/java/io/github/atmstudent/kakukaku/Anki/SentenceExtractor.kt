package io.github.atmstudent.kakukaku.Anki

/** Finds the sentence of a text that a position is in */
object SentenceExtractor
{
    private const val ENDINGS = "。．！？!?"
    private const val CLOSING = "」』）)】”\""

    /**
     * The sentence around [index] in [text]: from after the previous sentence end (。！？ or a line break) to the end of
     * its own, closing quotes included. Without any such mark it is the whole text.
     */
    fun sentenceAt(text: String, index: Int): String
    {
        if (text.isEmpty()) return ""
        val at = index.coerceIn(0, text.length - 1)

        var start = at
        while (start > 0 && !isBoundary(text[start - 1])) start--
        // Closing quotes belong to the sentence before
        while (start < at && text[start] in CLOSING) start++

        var end = at
        while (end < text.length && !isBoundary(text[end])) end++
        if (end < text.length && text[end] != '\n')
        {
            end++
            while (end < text.length && text[end] in CLOSING) end++
        }

        return text.substring(start, end).trim()
    }

    private fun isBoundary(c: Char) = c in ENDINGS || c == '\n'
}
