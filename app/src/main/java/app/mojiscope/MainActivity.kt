package app.mojiscope

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity()
{

    private lateinit var mStartMojiscopeIntent: Intent

    private val relaunchAppText = "Relaunch Mojiscope after verifying permission"

    private val overlayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this))
        {
            requestPermissionsInOrder()
        }
        else
        {
            Toast.makeText(this, "Check Permission: Draw on Other Apps\n$relaunchAppText", Toast.LENGTH_LONG).show()
        }
    }

    // Notifications are optional for function but required to see the foreground-service controls
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestScreenCapture()
    }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null)
        {
            Toast.makeText(this, "Check Permission: Record Screen\n$relaunchAppText", Toast.LENGTH_LONG).show()
        }
        else
        {
            mStartMojiscopeIntent = Intent(this, MainService::class.java)
                    .putExtra(EXTRA_PROJECTION_RESULT_CODE, result.resultCode)
                    .putExtra(EXTRA_PROJECTION_RESULT_INTENT, result.data)
            startMojiscope()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        setupMojiscopeDatabasesAndFiles(this)
    }

    override fun onPause()
    {
        super.onPause()
        Log.d(TAG, "ACTIVITY INVISIBLE")
    }

    override fun onResume()
    {
        super.onResume()
        Log.d(TAG, "ACTIVITY VISIBLE")
    }

    /**
     * Called when the user presses Start: walks through the permission prompts and then starts capture
     */
    fun onStartPressed()
    {
        if (MainService.IsRunning())
        {
            return
        }

        requestPermissionsInOrder()
    }

    fun onStopPressed()
    {
        stopService(Intent(this, MainService::class.java))
    }

    private fun startMojiscope()
    {
        val startFragment = supportFragmentManager.findFragmentById(R.id.main_fragment) as? MainStartFragment ?: return

        if (MainService.IsRunning())
        {
            return
        }

        if (::mStartMojiscopeIntent.isInitialized)
        {
            startFragment.onMojiscopeLoadStart()

            val totalDuration = 2000
            object : CountDownTimer(totalDuration.toLong(), 10)
            {
                override fun onFinish()
                {
                    startFragment.onMojiscopeLoaded()
                    startMojiscopeService(this@MainActivity, mStartMojiscopeIntent)
                }

                override fun onTick(millisUntilFinished: Long)
                {
                }
            }.start()
        }
        else {
            Toast.makeText(this, "Unable to start Mojiscope service", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Overlay permission -> notification permission -> screen capture consent, one at a time
     */
    private fun requestPermissionsInOrder()
    {
        if (!Settings.canDrawOverlays(this))
        {
            Log.d(TAG, "Sending ACTION_MANAGE_OVERLAY_PERMISSION Intent")
            overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        requestScreenCapture()
    }

    private fun requestScreenCapture()
    {
        if (MainService.IsRunning())
        {
            return
        }

        Log.d(TAG, "Sending REQUEST_SCREENSHOT Intent")
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        {
            // OCR needs the whole screen, not a single-app share
            mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        }
        else
        {
            mediaProjectionManager.createScreenCaptureIntent()
        }
        projectionLauncher.launch(captureIntent)
    }

    companion object
    {
        private val TAG = MainActivity::class.java.name
    }
}
