package app.mojiscope

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar

class MainStartFragment : Fragment()
{
    private lateinit var mainActivity : MainActivity
    private lateinit var rootView : View

    private lateinit var supportText : TextView
    private lateinit var startButton : Button


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
        if (MainService.IsRunning()) onMojiscopeLoaded() else onMojiscopeIdle()
    }

    private fun onMojiscopeIdle()
    {
        startButton.isEnabled = true
        startButton.text = getString(R.string.start_button)
        supportText.text = getString(R.string.start_hint)
    }

    fun onMojiscopeLoadStart()
    {
        startButton.isEnabled = false
        supportText.text = getString(R.string.moji_loading)
    }

    fun onMojiscopeLoaded()
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
