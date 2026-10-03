package app.mojiscope.Search

import app.mojiscope.Database.JmDictDatabase.Models.EntryOptimized
import app.mojiscope.Deinflictor.DeinflectionInfo

data class JmSearchResult(
        val entry: EntryOptimized,
        val deinfInfo: DeinflectionInfo,
        val word: String
)