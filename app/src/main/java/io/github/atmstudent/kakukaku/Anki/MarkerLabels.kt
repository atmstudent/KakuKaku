package io.github.atmstudent.kakukaku.Anki

import io.github.atmstudent.kakukaku.R

/** The text that tells what a marker is, for the menus */
object MarkerLabels
{
    fun label(marker: Marker): Int = when (marker)
    {
        Marker.SENTENCE -> R.string.marker_sentence
        Marker.EXPRESSION -> R.string.marker_expression
        Marker.READING -> R.string.marker_reading
        Marker.FURIGANA -> R.string.marker_furigana
        Marker.FURIGANA_PLAIN -> R.string.marker_furigana_plain
        Marker.CLOZE_BODY -> R.string.marker_cloze_body
        Marker.GLOSSARY -> R.string.marker_glossary
        Marker.PITCH_ACCENT_POSITIONS -> R.string.marker_pitch_accent_positions
        Marker.WRITING -> R.string.marker_writing
        Marker.WRITING_FURIGANA -> R.string.marker_writing_furigana
        Marker.WRITING_FURIGANA_PLAIN -> R.string.marker_writing_furigana_plain
    }

    /** What the marker is replaced by, without its name */
    fun description(marker: Marker): Int = when (marker)
    {
        Marker.SENTENCE -> R.string.marker_desc_sentence
        Marker.EXPRESSION -> R.string.marker_desc_expression
        Marker.READING -> R.string.marker_desc_reading
        Marker.FURIGANA -> R.string.marker_desc_furigana
        Marker.FURIGANA_PLAIN -> R.string.marker_desc_furigana_plain
        Marker.CLOZE_BODY -> R.string.marker_desc_cloze_body
        Marker.GLOSSARY -> R.string.marker_desc_glossary
        Marker.PITCH_ACCENT_POSITIONS -> R.string.marker_desc_pitch_accent_positions
        Marker.WRITING -> R.string.marker_desc_writing
        Marker.WRITING_FURIGANA -> R.string.marker_desc_writing_furigana
        Marker.WRITING_FURIGANA_PLAIN -> R.string.marker_desc_writing_furigana_plain
    }
}
