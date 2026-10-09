package io.github.atmstudent.kakukaku

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.atmstudent.kakukaku.Furigana.Furigana
import io.github.atmstudent.kakukaku.databinding.ActivitySettingsBinding

/** Options for how text is read and how the popup looks */
class SettingsActivity : AppCompatActivity()
{
    private lateinit var mBinding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        mBinding = ActivitySettingsBinding.inflate(layoutInflater)
        supportActionBar?.hide()
        setContentView(mBinding.root)

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        mBinding.toolbar.setNavigationOnClickListener { finish() }

        addHeader(R.string.settings_text_header)
        addSwitch(R.string.furigana_title, R.string.furigana_summary, Furigana.isEnabled(this)) { Furigana.setEnabled(this, it) }
        addSwitch(R.string.line_breaks_title, R.string.line_breaks_summary, AppSettings.stripLineBreaks(this)) { AppSettings.setStripLineBreaks(this, it) }

        addHeader(R.string.settings_popup_header)
        addChoice(R.string.popup_size_title,
                listOf(R.string.popup_size_small, R.string.popup_size_normal, R.string.popup_size_large),
                { AppSettings.popupSize(this) }, { AppSettings.setPopupSize(this, it) })
        addChoice(R.string.popup_position_title,
                listOf(R.string.popup_position_top, R.string.popup_position_bottom),
                { if (AppSettings.popupAtBottom(this)) 1 else 0 }, { AppSettings.setPopupAtBottom(this, it == 1) })
        addChoice(R.string.overlay_theme_title,
                listOf(R.string.overlay_theme_system, R.string.overlay_theme_light, R.string.overlay_theme_dark),
                { AppSettings.overlayTheme(this) }, { AppSettings.setOverlayTheme(this, it) })
    }

    private fun addHeader(@StringRes title: Int)
    {
        val header = TextView(this)
        header.setText(title)
        header.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        header.setTextColor(com.google.android.material.color.MaterialColors.getColor(header, androidx.appcompat.R.attr.colorPrimary))
        val top = if (mBinding.settingsList.childCount == 0) 0 else (24 * resources.displayMetrics.density).toInt()
        header.setPadding(0, top, 0, (4 * resources.displayMetrics.density).toInt())
        mBinding.settingsList.addView(header)
    }

    private fun addRow(@StringRes title: Int, summary: CharSequence?): View
    {
        val row = layoutInflater.inflate(R.layout.item_setting, mBinding.settingsList, false)
        row.findViewById<TextView>(R.id.setting_title).setText(title)
        val summaryView = row.findViewById<TextView>(R.id.setting_summary)
        if (summary == null) summaryView.visibility = View.GONE else summaryView.text = summary
        mBinding.settingsList.addView(row)
        return row
    }

    private fun addSwitch(@StringRes title: Int, @StringRes summary: Int, initial: Boolean, onChange: (Boolean) -> Unit)
    {
        val row = addRow(title, getString(summary))
        val toggle = row.findViewById<MaterialSwitch>(R.id.setting_switch)
        toggle.visibility = View.VISIBLE
        toggle.isChecked = initial
        row.setOnClickListener {
            toggle.isChecked = !toggle.isChecked
            onChange(toggle.isChecked)
        }
    }

    /** A row showing the current choice that opens a list of the options */
    private fun addChoice(@StringRes title: Int, options: List<Int>, current: () -> Int, onChosen: (Int) -> Unit)
    {
        val row = addRow(title, getString(options[current()]))
        row.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setSingleChoiceItems(options.map { getString(it) }.toTypedArray(), current()) { dialog, which ->
                        onChosen(which)
                        row.findViewById<TextView>(R.id.setting_summary).text = getString(options[which])
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.dictionary_cancel, null)
                    .show()
        }
    }
}
