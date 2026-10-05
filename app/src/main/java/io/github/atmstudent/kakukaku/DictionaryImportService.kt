package io.github.atmstudent.kakukaku

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.atmstudent.kakukaku.Dictionary.DictionaryFormatException
import io.github.atmstudent.kakukaku.Dictionary.DictionaryImport
import io.github.atmstudent.kakukaku.Dictionary.DictionarySelection
import io.github.atmstudent.kakukaku.Dictionary.UserDictionaryStore
import java.io.IOException
import java.text.NumberFormat
import kotlin.concurrent.thread

/**
 * Imports a dictionary in the background. It runs as a foreground service with a progress
 * notification, so the import carries on when you leave the dictionary screen or the app.
 */
class DictionaryImportService : Service()
{
    private var mLastNotification = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        val uri = intent?.data
        if (uri == null || sBusy)
        {
            // Already importing (or nothing to import); the running import keeps the service alive
            if (!sBusy) stopSelf()
            return START_NOT_STICKY
        }
        sBusy = true

        val totalBytes = fileSize(uri)
        val pitch = intent.getBooleanExtra(EXTRA_PITCH, false)
        val frequency = intent.getBooleanExtra(EXTRA_FREQUENCY, false)

        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(0, 0, totalBytes > 0, false), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        DictionaryImport.publish(DictionaryImport.Progress(0, 0, false, totalBytes > 0))

        thread(name = "DictionaryImport") { runImport(uri, totalBytes, pitch, frequency) }

        return START_NOT_STICKY
    }

    private fun runImport(uri: Uri, totalBytes: Long, pitch: Boolean, frequency: Boolean)
    {
        val numbers = NumberFormat.getIntegerInstance()
        var lastUpdate = 0L
        var message: String

        try
        {
            val open = { contentResolver.openInputStream(uri) ?: throw IOException("The file could not be opened") }
            val onProgress = { count: Int, bytesRead: Long ->
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 100)
                {
                    lastUpdate = now
                    val percent = if (totalBytes > 0) (bytesRead * 100 / totalBytes).toInt().coerceIn(0, 99) else 0
                    DictionaryImport.publish(DictionaryImport.Progress(percent, count, false, totalBytes > 0))
                    updateNotification(percent, count, totalBytes > 0, false)
                }
            }
            val onFinishing = {
                DictionaryImport.publish(DictionaryImport.Progress(100, 0, true, totalBytes > 0))
                updateNotification(100, 0, totalBytes > 0, true)
            }

            if (frequency)
            {
                val result = UserDictionaryStore.get(this).importFrequency(open, onProgress, onFinishing)
                message = getString(R.string.frequency_import_done, result.title, numbers.format(result.entries))
            }
            else if (pitch)
            {
                val result = UserDictionaryStore.get(this).importPitch(open, onProgress, onFinishing)
                message = getString(R.string.pitch_import_done, result.title, numbers.format(result.entries))
            }
            else
            {
                val result = UserDictionaryStore.get(this).import(open, onProgress, onFinishing)
                DictionarySelection.set(this, result.dictionary.id)
                message = getString(R.string.dictionary_import_done, result.dictionary.title, numbers.format(result.dictionary.entries))
            }
        }
        catch (e: DictionaryFormatException)
        {
            message = getString(R.string.dictionary_import_failed, e.message)
        }
        catch (e: Exception)
        {
            message = getString(R.string.dictionary_import_failed, e.message ?: e.javaClass.simpleName)
        }

        // Leave a result notification behind that can be swiped away
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, buildResultNotification(message))

        sBusy = false
        DictionaryImport.finish(message)
        stopSelf()
    }

    /** Size of the picked file in bytes, or -1 if the provider doesn't say */
    private fun fileSize(uri: Uri): Long
    {
        return try
        {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            } ?: -1L
        }
        catch (e: Exception)
        {
            -1L
        }
    }

    private fun updateNotification(percent: Int, entries: Int, hasPercent: Boolean, finishing: Boolean)
    {
        // Notifications are rate limited, and a few updates a second is plenty
        val now = System.currentTimeMillis()
        if (!finishing && now - mLastNotification < 500) return
        mLastNotification = now

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(percent, entries, hasPercent, finishing))
    }

    private fun buildNotification(percent: Int, entries: Int, hasPercent: Boolean, finishing: Boolean): Notification
    {
        val text = when
        {
            finishing -> getString(R.string.dictionary_finishing)
            entries > 0 -> getString(R.string.dictionary_importing, percent, NumberFormat.getIntegerInstance().format(entries))
            else -> getString(R.string.dictionary_notification_starting)
        }

        return NotificationCompat.Builder(this, createChannel())
                .setSmallIcon(R.drawable.kakukaku_notification_icon)
                .setContentTitle(getString(R.string.dictionary_notification_title))
                .setContentText(text)
                .setProgress(100, percent, !hasPercent || finishing)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openScreenIntent())
                .build()
    }

    private fun buildResultNotification(message: String): Notification
    {
        return NotificationCompat.Builder(this, createChannel())
                .setSmallIcon(R.drawable.kakukaku_notification_icon)
                .setContentTitle(getString(R.string.dictionary_notification_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(openScreenIntent())
                .build()
    }

    private fun openScreenIntent(): PendingIntent
    {
        return PendingIntent.getActivity(this, 0, Intent(this, DictionariesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createChannel(): String
    {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.dictionary_notification_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return CHANNEL_ID
    }

    companion object
    {
        /** True from the moment an import starts until it is over */
        @Volatile
        private var sBusy = false

        /** Boolean extra: the file is a pitch accent dictionary, not a word dictionary */
        const val EXTRA_PITCH = "pitch"

        /** Boolean extra: the file is a frequency dictionary */
        const val EXTRA_FREQUENCY = "frequency"

        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "dictionary_import_channel"
    }
}
