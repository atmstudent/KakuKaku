package io.github.atmstudent.kakukaku

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import io.github.atmstudent.kakukaku.Furigana.Furigana
import io.github.atmstudent.kakukaku.Search.BackgroundTask
import io.github.atmstudent.kakukaku.Windows.InformationWindow
import io.github.atmstudent.kakukaku.Windows.WindowCoordinator

class PassthroughActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (!Settings.canDrawOverlays(this))
        {
            Toast.makeText(this, R.string.overlay_needed, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupKakuKakuDatabasesAndFiles(this)

        var processText : String? = null
        when {
            intent?.action == Intent.ACTION_PROCESS_TEXT ->
            {
                processText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            }
            intent?.action == Intent.ACTION_SEND ->
            {
                if ("text/plain" == intent.type)
                {
                    processText = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
            }
        }

        // Line breaks (e.g. from selecting stacked vertical text) would show up as empty character boxes
        processText = processText?.replace(Regex("[\\r\\n\\u2028\\u2029]+"), "")

        if (!processText.isNullOrEmpty())
        {
            // Taking the furigana out reads the dictionary, so it happens off the main thread; the popup does not need this activity
            val appContext = applicationContext
            val text: String = processText
            object : BackgroundTask<String>()
            {
                override fun doInBackground(): String = try { Furigana.strip(appContext, text) } catch (e: Exception) { text }

                override fun onPostExecute(result: String)
                {
                    val windowCoordinator = WindowCoordinator(appContext)
                    val infoWindow = windowCoordinator.getWindow(WINDOW_INFO) as InformationWindow

                    infoWindow.setResult(result)
                    infoWindow.show()
                }
            }.execute()

            finish()
        }
    }
}
