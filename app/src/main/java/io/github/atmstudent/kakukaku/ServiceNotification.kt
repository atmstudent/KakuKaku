package io.github.atmstudent.kakukaku

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * The notification of the capture service. While it runs it has the buttons Instant, Filter and Pause. Pause stops the
 * capture but leaves the notification, now with Start in place of Pause, so it can be started again from there. Instant
 * and Filter are toggles: filled with "On" when on, outlined with "Off" when off.
 */
object ServiceNotification
{
    const val NOTIFICATION_ID = 1

    private val BUTTONS = intArrayOf(R.id.notif_btn1, R.id.notif_btn2, R.id.notif_btn3)
    private val LABELS = intArrayOf(R.id.notif_btn1_label, R.id.notif_btn2_label, R.id.notif_btn3_label)
    private val STATES = intArrayOf(R.id.notif_btn1_state, R.id.notif_btn2_state, R.id.notif_btn3_state)

    /** [running]: the capture service runs; otherwise it is paused (or stopped by the system) and Start is offered */
    fun build(context: Context, running: Boolean): Notification
    {
        val title = context.getString(if (running) R.string.notification_running else R.string.notification_paused)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, createChannel(context))
                .setSmallIcon(R.drawable.kakukaku_notification_icon)
                .setContentTitle(title)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(buildControls(context, R.layout.notification_collapsed, running, title))
                .setCustomBigContentView(buildControls(context, R.layout.notification_expanded, running, title))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .build()

        // A paused notification can be swiped away; a running one belongs to the foreground service
        if (running) notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE

        return notification
    }

    fun post(context: Context, running: Boolean)
    {
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(context, running))
    }

    private fun broadcast(context: Context, requestCode: Int, receiver: Class<*>): PendingIntent
    {
        return PendingIntent.getBroadcast(context, requestCode, Intent(context, receiver), PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildControls(context: Context, layout: Int, running: Boolean, title: String): RemoteViews
    {
        val views = RemoteViews(context.packageName, layout)
        views.setTextViewText(R.id.notif_title, title)

        val prefs = getPrefs(context)
        bindToggle(context, views, 0, context.getString(R.string.notification_instant), prefs.instantModeSetting,
                broadcast(context, REQUEST_SERVICE_TOGGLE_INSTANT_MODE, MainService.ToggleInstantModeMainService::class.java), R.string.notification_instant_description)
        bindToggle(context, views, 1, context.getString(R.string.notification_filter), prefs.imageFilterSetting,
                broadcast(context, REQUEST_SERVICE_TOGGLE_IMAGE_PREVIEW, MainService.ToggleImagePreviewMainService::class.java), R.string.notification_filter_description)

        if (running)
        {
            bindButton(context, views, 2, context.getString(R.string.notification_pause), null, false, broadcast(context, REQUEST_SERVICE_PAUSE, MainService.PauseMainService::class.java), null)
        }
        else
        {
            // Android 14+ asks for the screen-sharing permission again, so Start goes through the app
            val start = Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_START_FROM_NOTIFICATION, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            bindButton(context, views, 2, context.getString(R.string.notification_start), null, true,
                    PendingIntent.getActivity(context, REQUEST_SERVICE_START, start, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT), null)
        }

        return views
    }

    private fun bindToggle(context: Context, views: RemoteViews, slot: Int, label: String, on: Boolean, click: PendingIntent, descriptionResource: Int)
    {
        val state = context.getString(if (on) R.string.notification_on else R.string.notification_off)
        val description = context.getString(descriptionResource, state, context.getString(if (on) R.string.notification_off else R.string.notification_on))
        bindButton(context, views, slot, label, state, on, click, description)
    }

    /** [filled] buttons are drawn solid (a toggle that is on, or Start); [state] is the On/Off text of a toggle, if it is one */
    private fun bindButton(context: Context, views: RemoteViews, slot: Int, label: String, state: String?, filled: Boolean, click: PendingIntent, description: String?)
    {
        val textColor = ContextCompat.getColor(context, if (filled) R.color.md_on_primary else R.color.md_on_surface)

        views.setViewVisibility(BUTTONS[slot], View.VISIBLE)
        views.setInt(BUTTONS[slot], "setBackgroundResource", if (filled) R.drawable.notif_button_on else R.drawable.notif_button_off)
        views.setTextViewText(LABELS[slot], label)
        views.setTextColor(LABELS[slot], textColor)

        if (state != null)
        {
            views.setViewVisibility(STATES[slot], View.VISIBLE)
            views.setTextViewText(STATES[slot], state)
            views.setTextColor(STATES[slot], textColor)
        }
        else
        {
            views.setViewVisibility(STATES[slot], View.GONE)
        }

        views.setContentDescription(BUTTONS[slot], description ?: label)
        views.setOnClickPendingIntent(BUTTONS[slot], click)
    }

    private fun createChannel(context: Context): String
    {
        val channel = NotificationChannel(KAKUKAKU_CHANNEL_ID, KAKUKAKU_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return KAKUKAKU_CHANNEL_ID
    }
}
