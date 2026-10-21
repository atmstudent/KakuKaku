package io.github.atmstudent.kakukaku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.atmstudent.kakukaku.Anki.AnkiBridge
import io.github.atmstudent.kakukaku.Anki.AnkiSettings
import io.github.atmstudent.kakukaku.Anki.Marker
import io.github.atmstudent.kakukaku.Anki.MarkerLabels
import io.github.atmstudent.kakukaku.Anki.TemplateFiller
import io.github.atmstudent.kakukaku.Anki.TermContext
import io.github.atmstudent.kakukaku.WordList.HtmlText
import io.github.atmstudent.kakukaku.databinding.ActivityAnkiBinding
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Where cards go in AnkiDroid (deck, note type) and what goes into each field of the note type: a template of text
 * and {markers}, like Yomitan's. Reads AnkiDroid's decks and note types through its provider, off the main thread.
 */
class AnkiActivity : AppCompatActivity()
{
    private lateinit var mBinding: ActivityAnkiBinding
    private val mExecutor = Executors.newSingleThreadExecutor()

    private val mPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    /** A made-up lookup that the grey example lines of the templates are filled from */
    private val mSample = TermContext(
            sentence = "昨日は友達と寿司を食べました。",
            expression = "食べる",
            reading = "たべる",
            writing = "食べる",
            clozeBody = "食べました",
            glossary = listOf(Pair("v1,vt", "to eat")),
            pitch = "[2]").values()

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        mBinding = ActivityAnkiBinding.inflate(layoutInflater)
        supportActionBar?.hide()
        setContentView(mBinding.root)

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        mBinding.toolbar.setNavigationOnClickListener { finish() }
        mBinding.toolbar.inflateMenu(R.menu.anki_menu)
        mBinding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.menu_anki_info) showMarkerInfo()
            true
        }
    }

    /** What each marker is replaced by */
    private fun showMarkerInfo()
    {
        val density = resources.displayMetrics.density
        val column = android.widget.LinearLayout(this)
        column.orientation = android.widget.LinearLayout.VERTICAL
        column.setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), 0)

        val intro = TextView(this)
        intro.setText(R.string.anki_markers_info_intro)
        intro.setPadding(0, 0, 0, (12 * density).toInt())
        column.addView(intro)

        for (marker in Marker.values())
        {
            val line = TextView(this)
            val text = android.text.SpannableStringBuilder("{${marker.id}}")
            text.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, text.length, 0)
            text.append("\n").append(getString(MarkerLabels.description(marker)))
            line.text = text
            line.setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            column.addView(line)
        }

        val scroll = android.widget.ScrollView(this)
        scroll.addView(column)
        MaterialAlertDialogBuilder(this)
                .setTitle(R.string.anki_markers_info)
                .setView(scroll)
                .setPositiveButton(R.string.dictionary_close, null)
                .show()
    }

    override fun onResume()
    {
        super.onResume()
        refresh()
    }

    override fun onDestroy()
    {
        mExecutor.shutdown()
        super.onDestroy()
    }

    /** Reads what AnkiDroid has (when it is there and allowed), then builds the screen */
    private fun refresh()
    {
        val installed = AnkiBridge.isInstalled(this)
        val allowed = installed && AnkiBridge.hasPermission(this)

        if (!allowed)
        {
            build(installed, false, emptyMap(), emptyMap(), emptyList())
            return
        }

        try
        {
            mExecutor.execute {
                val loaded = try
                {
                    val decks = AnkiBridge.decks(this)
                    val models = AnkiBridge.models(this)
                    val fields = if (AnkiSettings.modelId(this) >= 0) AnkiBridge.fieldNames(this, AnkiSettings.modelId(this)) else emptyList()
                    Triple(decks, models, fields)
                }
                catch (e: Exception)
                {
                    null
                }
                runOnUiThread { if (!isDestroyed) build(true, loaded != null, loaded?.first ?: emptyMap(), loaded?.second ?: emptyMap(), loaded?.third ?: emptyList()) }
            }
        }
        catch (e: RejectedExecutionException)
        {
            // The screen is closing
        }
    }

    private fun build(installed: Boolean, connected: Boolean, decks: Map<Long, String>, models: Map<Long, String>, fields: List<String>)
    {
        val list = mBinding.ankiList
        list.removeAllViews()

        // Status
        val status = addRow(null, getString(when { !installed -> R.string.anki_status_not_installed; !connected -> R.string.anki_status_no_permission; else -> R.string.anki_status_ok }))
        if (!connected)
        {
            val button = MaterialButton(this)
            button.setText(if (installed) R.string.anki_allow else R.string.anki_get)
            button.setOnClickListener {
                if (installed) mPermission.launch(AnkiBridge.PERMISSION)
                else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://apps.ankiweb.net")))
            }
            list.addView(button)
        }
        status.setOnClickListener(null)

        if (connected)
        {
            addHeader(R.string.anki_header_where)
            addChoice(R.string.anki_deck, AnkiSettings.deckName(this).ifEmpty { getString(R.string.anki_not_chosen) }, decks) { id, name ->
                AnkiSettings.setDeck(this, id, name)
                refresh()
            }
            addChoice(R.string.anki_note_type, AnkiSettings.modelName(this).ifEmpty { getString(R.string.anki_not_chosen) }, models) { id, name ->
                AnkiSettings.setModel(this, id, name)
                refresh()
            }

            if (fields.isNotEmpty())
            {
                addHeader(R.string.anki_header_fields)
                addNote(getString(R.string.anki_fields_help))
                for (field in fields) addField(field)
                addTags()
            }
        }
    }

    private fun addHeader(title: Int)
    {
        val header = TextView(this)
        header.setText(title)
        header.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        header.setTextColor(com.google.android.material.color.MaterialColors.getColor(header, androidx.appcompat.R.attr.colorPrimary))
        header.setPadding(0, (20 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        mBinding.ankiList.addView(header)
    }

    private fun addNote(text: String)
    {
        val note = TextView(this)
        note.text = text
        note.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
        note.setTextColor(com.google.android.material.color.MaterialColors.getColor(note, com.google.android.material.R.attr.colorOnSurfaceVariant))
        mBinding.ankiList.addView(note)
    }

    private fun addRow(title: Int?, summary: String): View
    {
        val row = layoutInflater.inflate(R.layout.item_setting, mBinding.ankiList, false)
        if (title != null) row.findViewById<TextView>(R.id.setting_title).setText(title) else row.findViewById<TextView>(R.id.setting_title).visibility = View.GONE
        row.findViewById<TextView>(R.id.setting_summary).text = summary
        mBinding.ankiList.addView(row)
        return row
    }

    private fun addChoice(title: Int, current: String, options: Map<Long, String>, onChosen: (Long, String) -> Unit)
    {
        val row = addRow(title, current)
        row.setOnClickListener {
            if (options.isEmpty())
            {
                Toast.makeText(this, R.string.anki_no_decks, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val ids = options.keys.toList()
            val names = options.values.toTypedArray()
            MaterialAlertDialogBuilder(this)
                    .setTitle(title)
                    .setItems(names) { _, which -> onChosen(ids[which], names[which]) }
                    .setNegativeButton(R.string.dictionary_cancel, null)
                    .show()
        }
    }

    /** The tags of every note */
    private fun addTags()
    {
        val row = LayoutInflater.from(this).inflate(R.layout.item_anki_field, mBinding.ankiList, false)
        row.findViewById<TextInputLayout>(R.id.field_layout).hint = getString(R.string.anki_tags)
        val edit = row.findViewById<TextInputEditText>(R.id.field_text)
        edit.setText(AnkiSettings.tags(this))
        edit.addTextChangedListener(object : TextWatcher
        {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = AnkiSettings.setTags(this@AnkiActivity, s.toString())
        })
        row.findViewById<View>(R.id.field_preview).visibility = View.GONE
        row.findViewById<View>(R.id.field_insert).visibility = View.GONE
        mBinding.ankiList.addView(row)
    }

    /** One field of the note type: an editable template with an example line and a button that inserts a marker */
    private fun addField(fieldName: String)
    {
        val row = LayoutInflater.from(this).inflate(R.layout.item_anki_field, mBinding.ankiList, false)
        val layout = row.findViewById<TextInputLayout>(R.id.field_layout)
        val edit = row.findViewById<TextInputEditText>(R.id.field_text)
        val preview = row.findViewById<TextView>(R.id.field_preview)
        layout.hint = fieldName

        fun showPreview(template: String)
        {
            val filled = HtmlText.plain(TemplateFiller.fill(template, mSample))
            preview.text = filled
            preview.visibility = if (filled.isEmpty()) View.GONE else View.VISIBLE
        }

        edit.setText(AnkiSettings.template(this, fieldName))
        showPreview(edit.text.toString())
        edit.addTextChangedListener(object : TextWatcher
        {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?)
            {
                AnkiSettings.setTemplate(this@AnkiActivity, fieldName, s.toString())
                showPreview(s.toString())
            }
        })

        // A drop-down under the button: the marker is put where the cursor is
        val insert = row.findViewById<MaterialButton>(R.id.field_insert)
        insert.setOnClickListener {
            val menu = android.widget.PopupMenu(this, insert)
            for ((index, marker) in Marker.values().withIndex()) menu.menu.add(0, index, index, "{${marker.id}}")
            menu.setOnMenuItemClickListener { item ->
                val at = edit.selectionStart.coerceAtLeast(0)
                edit.text?.insert(at, item.title)
                true
            }
            menu.show()
        }

        mBinding.ankiList.addView(row)
    }
}
