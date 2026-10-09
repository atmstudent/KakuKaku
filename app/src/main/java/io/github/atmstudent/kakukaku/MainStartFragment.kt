package io.github.atmstudent.kakukaku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import io.github.atmstudent.kakukaku.Furigana.Furigana
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainStartFragment : Fragment()
{
    private lateinit var mainActivity : MainActivity
    private lateinit var rootView : View

    private lateinit var supportText : TextView
    private lateinit var startButton : Button


    private fun showAbout()
    {
        val text = TextView(mainActivity).apply {
            setText(HtmlCompat.fromHtml(getString(R.string.about_text), HtmlCompat.FROM_HTML_MODE_COMPACT))
            setPadding(64, 24, 64, 0)
            setTextIsSelectable(true)
        }

        MaterialAlertDialogBuilder(mainActivity)
                .setTitle(getString(R.string.app_name))
                .setMessage(getString(R.string.about_version, BuildConfig.VERSION_NAME))
                .setView(ScrollView(mainActivity).apply { addView(text) })
                .setPositiveButton(R.string.about_close, null)
                .show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View?
    {
        mainActivity = activity as MainActivity

        rootView = inflater.inflate(R.layout.fragment_start, container, false)

        supportText = rootView.findViewById(R.id.support_text)

        startButton = rootView.findViewById(R.id.start_button)

        rootView.findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener {
            when (it.itemId)
            {
                R.id.menu_dictionaries -> startActivity(Intent(mainActivity, DictionariesActivity::class.java))
                R.id.menu_settings -> startActivity(Intent(mainActivity, SettingsActivity::class.java))
                R.id.menu_tutorial -> startActivity(Intent(mainActivity, TutorialActivity::class.java))
                R.id.menu_about -> showAbout()
            }
            true
        }
        startButton.setOnClickListener {
            if (MainService.IsRunning()) mainActivity.onStopPressed() else mainActivity.onStartPressed()
        }

        // Keep content clear of the status and navigation bars (edge-to-edge on Android 15+)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        return rootView
    }

    override fun onStart()
    {
        super.onStart()

        // The service can also be stopped from its notification, so follow its state
        ContextCompat.registerReceiver(requireContext(), stateReceiver, IntentFilter(MainService.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop()
    {
        requireContext().unregisterReceiver(stateReceiver)
        super.onStop()
    }

    override fun onResume()
    {
        super.onResume()
        refreshState()
    }

    private val stateReceiver = object : BroadcastReceiver()
    {
        override fun onReceive(context: Context, intent: Intent)
        {
            refreshState()
        }
    }

    private fun refreshState()
    {
        if (MainService.IsRunning()) onKakuKakuLoaded() else onKakuKakuIdle()
    }

    private fun onKakuKakuIdle()
    {
        startButton.isEnabled = true
        startButton.text = getString(R.string.start_button)
        supportText.text = getString(R.string.start_hint)
    }

    fun onKakuKakuLoadStart()
    {
        startButton.isEnabled = false
        supportText.text = getString(R.string.kakukaku_loading)
    }

    fun onKakuKakuLoaded()
    {
        startButton.isEnabled = true
        startButton.text = getString(R.string.stop_button)
        writeSupportText()
    }

    private fun writeSupportText()
    {
        supportText.text = getString(R.string.support_text)
    }
}
