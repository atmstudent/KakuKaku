package io.github.atmstudent.kakukaku.Search

import io.github.atmstudent.kakukaku.Database.JmDictDatabase.Models.EntryOptimized
import io.github.atmstudent.kakukaku.Deinflictor.DeinflectionInfo

data class JmSearchResult(
        val entry: EntryOptimized,
        val deinfInfo: DeinflectionInfo,
        val word: String
){
    /** Pitch accent of the entry, such as "[1]"; empty if unknown or switched off */
    var pitch: String = ""
}
