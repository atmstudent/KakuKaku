package io.github.atmstudent.kakukaku

import org.junit.Assert.assertEquals
import org.junit.Test

class KanaTest
{
    @Test
    fun katakanaBecomesHiragana()
    {
        assertEquals("ねこ", toHiragana("ネコ"))
        assertEquals("とても", toHiragana("トテモ"))
    }

    @Test
    fun otherCharactersStay()
    {
        assertEquals("猫ねこabc、ー", toHiragana("猫ねこabc、ー"))
    }

    @Test
    fun hiraganaBecomesKatakana()
    {
        assertEquals("ネコ", toKatakana("ねこ"))
        assertEquals("猫ネコ", toKatakana("猫ねこ"))
    }
}
