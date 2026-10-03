package app.mojiscope

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import app.mojiscope.Dialogs.StarRatingDialogFragment


class MainActivity : AppCompatActivity()
{
    private var mIsActivityVisible = false
    private var mShownRating = false

    private lateinit var mPrefs : SharedPreferences
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
            finish()
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
            finish()
        }
        else
        {
            mStartMojiscopeIntent = Intent(this, MainService::class.java)
                    .putExtra(EXTRA_PROJECTION_RESULT_CODE, result.resultCode)
                    .putExtra(EXTRA_PROJECTION_RESULT_INTENT, result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        mPrefs = getSharedPreferences(MOJI_PREF_FILE, Context.MODE_PRIVATE)

        if (isFirstLaunch())
        {
            startActivity(Intent(this, TutorialActivity::class.java))
            finish()
        }
        else {
            supportActionBar?.hide()
            setContentView(R.layout.activity_main)

            setupMojiscopeDatabasesAndFiles(this)
        }
    }

    override fun onStart()
    {
        super.onStart()

        requestPermissionsInOrder()

        showRatingDialog()
    }

    override fun onPause()
    {
        super.onPause()
        Log.d(TAG, "ACTIVITY INVISIBLE")
        mIsActivityVisible = false
    }

    override fun onResume()
    {
        super.onResume()
        Log.d(TAG, "ACTIVITY VISIBLE")
        mIsActivityVisible = true
    }

    fun startMojiscope(startFragment: MainStartFragment)
    {
        if (MainService.IsRunning())
        {
            return
        }

        if (!mIsActivityVisible)
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

    private fun showRatingDialog()
    {
        if (mShownRating)
        {
            return
        }

        mShownRating = true

        val timesLaunched = mPrefs.getInt(MOJI_PREF_TIMES_LAUNCHED, 1)
        val rated = mPrefs.getBoolean(MOJI_PREF_PLAY_STORE_RATED, false)

        if (timesLaunched % 20 == 0 && !rated)
        {
            StarRatingDialogFragment().show(supportFragmentManager, "StarRating")
        }
    }

    private fun isFirstLaunch() : Boolean
    {
        return mPrefs.getBoolean(MOJI_PREF_FIRST_LAUNCH, true)
    }

    companion object
    {
        private val TAG = MainActivity::class.java.name
    }
}
