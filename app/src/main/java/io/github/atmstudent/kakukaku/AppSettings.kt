package io.github.atmstudent.kakukaku

import android.content.Context

/** Options from the Settings screen. They are read each time they are needed, so a change applies to the next lookup. */
object AppSettings
{
    const val SIZE_SMALL = 0
    const val SIZE_NORMAL = 1
    const val SIZE_LARGE = 2

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private fun prefs(context: Context) = context.getSharedPreferences(KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE)

    /** On: line breaks are left out. Off: a break stays in the text and ends a word lookup. */
    @JvmStatic
    fun stripLineBreaks(context: Context): Boolean = prefs(context).getBoolean(KAKUKAKU_PREF_STRIP_LINE_BREAKS, true)

    fun setStripLineBreaks(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KAKUKAKU_PREF_STRIP_LINE_BREAKS, enabled).apply()

    @JvmStatic
    fun popupSize(context: Context): Int = prefs(context).getInt(KAKUKAKU_PREF_POPUP_SIZE, SIZE_NORMAL).coerceIn(SIZE_SMALL, SIZE_LARGE)

    fun setPopupSize(context: Context, size: Int) = prefs(context).edit().putInt(KAKUKAKU_PREF_POPUP_SIZE, size).apply()

    /** Factor for the character cells and the text of the popup */
    @JvmStatic
    fun popupScale(context: Context): Float = when (popupSize(context))
    {
        SIZE_SMALL -> 0.85f
        SIZE_LARGE -> 1.25f
        else -> 1f
    }

    @JvmStatic
    fun popupAtBottom(context: Context): Boolean = prefs(context).getBoolean(KAKUKAKU_PREF_POPUP_BOTTOM, false)

    fun setPopupAtBottom(context: Context, bottom: Boolean) = prefs(context).edit().putBoolean(KAKUKAKU_PREF_POPUP_BOTTOM, bottom).apply()

    @JvmStatic
    fun overlayTheme(context: Context): Int = prefs(context).getInt(KAKUKAKU_PREF_OVERLAY_THEME, THEME_SYSTEM).coerceIn(THEME_SYSTEM, THEME_DARK)

    fun setOverlayTheme(context: Context, theme: Int) = prefs(context).edit().putInt(KAKUKAKU_PREF_OVERLAY_THEME, theme).apply()
}
