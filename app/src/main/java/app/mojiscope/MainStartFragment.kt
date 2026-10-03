package app.mojiscope

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

class MainStartFragment : Fragment()
{
    private lateinit var mainActivity : MainActivity
    private lateinit var rootView : View

    private lateinit var supportText : TextView
    private lateinit var progressBar : ProgressBar
    private lateinit var startButton : Button


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View?
    {
        mainActivity = activity as MainActivity

        rootView = inflater.inflate(R.layout.fragment_start, container, false)

        supportText = rootView.findViewById(R.id.support_text)
        progressBar = rootView.findViewById(R.id.progress_bar)

        startButton = rootView.findViewById(R.id.start_button)

        rootView.findViewById<View>(R.id.menu_button).setOnClickListener { showMenu(it) }
        startButton.setOnClickListener { mainActivity.onStartPressed() }

        // Keep content clear of the status and navigation bars (edge-to-edge on Android 15+)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        return rootView
    }

    private fun showMenu(anchor: View)
    {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.home_menu, popup.menu)
        popup.setOnMenuItemClickListener {
            when (it.itemId)
            {
                R.id.menu_tutorial -> startActivity(Intent(mainActivity, TutorialActivity::class.java))
                R.id.menu_source -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/0xbad1d3a5/Kaku")))
            }
            true
        }
        popup.show()
    }

    override fun onResume()
    {
        super.onResume()

        if (MainService.IsRunning())
        {
            onMojiscopeLoaded()
        }
        else
        {
            onMojiscopeIdle()
        }
    }

    private fun onMojiscopeIdle()
    {
        progressBar.isIndeterminate = false
        progressBar.progress = 0
        startButton.isEnabled = true
        startButton.visibility = View.VISIBLE
        supportText.text = getString(R.string.start_hint)
    }

    fun onMojiscopeLoadStart()
    {
        progressBar.isIndeterminate = true
        progressBar.progress = 0
        startButton.isEnabled = false
        supportText.text = getString(R.string.moji_loading)
    }

    fun onMojiscopeLoaded()
    {
        progressBar.isIndeterminate = false
        progressBar.progress = 100
        startButton.visibility = View.GONE
        writeSupportText()
    }

    private fun writeSupportText()
    {
        supportText.text = getString(R.string.support_text)
    }
}
