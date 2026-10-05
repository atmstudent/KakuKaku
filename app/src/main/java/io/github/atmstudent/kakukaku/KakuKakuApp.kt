package io.github.atmstudent.kakukaku

import android.app.Application
import com.google.android.material.color.DynamicColors

class KakuKakuApp : Application()
{
    override fun onCreate()
    {
        super.onCreate()

        // Wallpaper-based colors on Android 12+ (no effect on older versions)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
