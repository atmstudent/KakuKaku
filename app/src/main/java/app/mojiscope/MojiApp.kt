package app.mojiscope

import android.app.Application
import com.google.android.material.color.DynamicColors

class MojiApp : Application()
{
    override fun onCreate()
    {
        super.onCreate()

        // Wallpaper-based colors on Android 12+ (no effect on older versions)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
