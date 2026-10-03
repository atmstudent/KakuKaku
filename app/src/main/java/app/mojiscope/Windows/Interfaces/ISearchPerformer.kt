package app.mojiscope.Windows.Interfaces

import app.mojiscope.Windows.Data.DisplayData
import app.mojiscope.Windows.Data.ISquareChar

interface ISearchPerformer
{
    fun performSearch(squareChar: ISquareChar)
}