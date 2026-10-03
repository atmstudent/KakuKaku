package app.mojiscope.Search

import app.mojiscope.Database.JmDictDatabase.Models.EntryOptimized
import app.mojiscope.Deinflictor.DeinflectionInfo

data class JmSearchResult(
        val entry: EntryOptimized,
        val deinfInfo: DeinflectionInfo,
        val word: String
){
    /** Pitch accent of the entry, such as "[1]"; empty if unknown or switched off */
    var pitch: String = ""
}
