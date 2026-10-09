package io.github.atmstudent.kakukaku

import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.atmstudent.kakukaku.Dictionary.DictionaryImport
import io.github.atmstudent.kakukaku.Dictionary.DictionarySelection
import io.github.atmstudent.kakukaku.Dictionary.MetaKind
import io.github.atmstudent.kakukaku.Dictionary.MetaSelection
import io.github.atmstudent.kakukaku.Dictionary.UserDictionary
import io.github.atmstudent.kakukaku.Dictionary.UserDictionaryStore
import io.github.atmstudent.kakukaku.databinding.ActivityDictionariesBinding
import io.github.atmstudent.kakukaku.databinding.ItemDictionaryBinding
import io.github.atmstudent.kakukaku.databinding.SectionDictionaryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Choose which dictionary is used for word lookups, which frequency dictionary orders them and which pitch accent
 * dictionary adds accents, and import personal dictionaries (Yomitan format). The three sections work alike: a list
 * to choose from (frequency and pitch accent also have "None"), delete buttons, and an import button.
 */
class DictionariesActivity : AppCompatActivity()
{
    private lateinit var mBinding: ActivityDictionariesBinding
    private val mExecutor = Executors.newSingleThreadExecutor()

    /** One section of the screen; [kind] is null for the word dictionaries */
    private inner class Section(val kind: MetaKind?, @StringRes val title: Int, val info: () -> InfoContent, val importButtonText: Int)
    {
        val binding: SectionDictionaryBinding = SectionDictionaryBinding.inflate(layoutInflater, mBinding.sections, false)
        var importButton = binding.sectionImport
    }

    private class InfoContent(val title: Int, val message: CharSequence, val links: List<Pair<Int, String>> = emptyList())

    private val mSections = ArrayList<Section>()

    private val mPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDictionary(uri, false, false)
    }

    private val mPitchPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDictionary(uri, true, false)
    }

    private val mFrequencyPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDictionary(uri, false, true)
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        mBinding = ActivityDictionariesBinding.inflate(layoutInflater)
        supportActionBar?.hide()
        setContentView(mBinding.root)

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        mBinding.toolbar.setNavigationOnClickListener { finish() }

        addSection(Section(null, R.string.dictionary_select_header,
                { InfoContent(R.string.dictionary_select_header, TextUtils.concat(getText(R.string.dictionary_update_text), "\n\n", getText(R.string.dictionary_format_text)),
                        listOf(Pair(R.string.dictionary_get_jmdict, JMDICT_RELEASES_URL), Pair(R.string.dictionary_more, YOMITAN_DICTIONARIES_URL))) },
                R.string.dictionary_import), mPicker)
        addSection(Section(MetaKind.FREQUENCY, R.string.frequency_header,
                { InfoContent(R.string.frequency_header, getText(R.string.frequency_help)) },
                R.string.frequency_import), mFrequencyPicker)
        addSection(Section(MetaKind.PITCH, R.string.pitch_header,
                { InfoContent(R.string.pitch_header, getText(R.string.pitch_help)) },
                R.string.pitch_import), mPitchPicker)

        refresh()
    }

    private fun addSection(section: Section, picker: androidx.activity.result.ActivityResultLauncher<Array<String>>)
    {
        section.binding.sectionTitle.setText(section.title)
        section.binding.sectionImport.setText(section.importButtonText)
        section.binding.sectionImport.setOnClickListener { picker.launch(arrayOf("*/*")) }
        section.binding.sectionInfo.setOnClickListener { showInfo(section.info()) }
        mBinding.sections.addView(section.binding.root)
        mSections.add(section)
    }

    private fun showInfo(content: InfoContent)
    {
        val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(content.title)
                .setMessage(content.message)
                .setNegativeButton(R.string.dictionary_close, null)
        if (content.links.isNotEmpty()) dialog.setPositiveButton(content.links[0].first) { _, _ -> openLink(content.links[0].second) }
        if (content.links.size > 1) dialog.setNeutralButton(content.links[1].first) { _, _ -> openLink(content.links[1].second) }
        dialog.show()
    }

    override fun onDestroy()
    {
        mExecutor.shutdown()
        super.onDestroy()
    }

    /** Reads the lists off the main thread, then shows them */
    private fun refresh()
    {
        try
        {
            mExecutor.execute {
                val store = UserDictionaryStore.get(this)
                val selectedWords = DictionarySelection.get(this)
                val words = store.list()
                val frequency = store.metaList(MetaKind.FREQUENCY)
                val selectedFrequency = MetaSelection.get(this, MetaKind.FREQUENCY)
                val pitch = store.metaList(MetaKind.PITCH)
                val selectedPitch = MetaSelection.get(this, MetaKind.PITCH)

                runOnUiThread {
                    if (!isDestroyed)
                    {
                        showWords(mSections[0], selectedWords, words)
                        showMeta(mSections[1], selectedFrequency, frequency)
                        showMeta(mSections[2], selectedPitch, pitch)
                    }
                }
            }
        }
        catch (e: RejectedExecutionException)
        {
            // The screen is closing
        }
    }

    private fun showWords(section: Section, selected: Long, dictionaries: List<UserDictionary>)
    {
        section.binding.sectionList.removeAllViews()

        addRow(section, getString(R.string.dictionary_builtin_title), getString(R.string.dictionary_builtin_subtitle), selected == DictionarySelection.BUILT_IN, false,
                { DictionarySelection.set(this, DictionarySelection.BUILT_IN) }, {})

        for (dictionary in dictionaries)
        {
            addRow(section, dictionary.title, subtitle(dictionary), selected == dictionary.id, true,
                    { DictionarySelection.set(this, dictionary.id) },
                    {
                        UserDictionaryStore.get(this).delete(dictionary.id)
                        if (DictionarySelection.get(this) == dictionary.id) DictionarySelection.set(this, DictionarySelection.BUILT_IN)
                    })
        }
    }

    /** Frequency and pitch accent: "None" first, then what has been imported */
    private fun showMeta(section: Section, selected: Long, dictionaries: List<UserDictionary>)
    {
        val kind = section.kind ?: return
        section.binding.sectionList.removeAllViews()

        addRow(section, getString(R.string.dictionary_none), "", selected == MetaSelection.NONE, false, { MetaSelection.set(this, kind, MetaSelection.NONE) }, {})

        for (dictionary in dictionaries)
        {
            addRow(section, dictionary.title, subtitle(dictionary), selected == dictionary.id, true,
                    { MetaSelection.set(this, kind, dictionary.id) },
                    {
                        UserDictionaryStore.get(this).deleteMeta(kind, dictionary.id)
                        if (MetaSelection.get(this, kind) == dictionary.id) MetaSelection.set(this, kind, MetaSelection.NONE)
                    })
        }
    }

    private fun subtitle(dictionary: UserDictionary): String
    {
        val count = NumberFormat.getIntegerInstance().format(dictionary.entries)
        return if (dictionary.revision.isEmpty())
            getString(R.string.dictionary_subtitle_format, count)
        else
            getString(R.string.dictionary_subtitle_revision_format, count, dictionary.revision)
    }

    /** [onSelect] and [onDelete] change the stored state; they run off the main thread, and the screen is refreshed after */
    private fun addRow(section: Section, title: String, subtitle: String, selected: Boolean, deletable: Boolean, onSelect: () -> Unit, onDelete: () -> Unit)
    {
        val list = section.binding.sectionList
        val row = ItemDictionaryBinding.inflate(LayoutInflater.from(this), list, false)
        row.dictionaryTitle.text = title
        row.dictionarySubtitle.text = subtitle
        row.dictionarySubtitle.visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE
        row.dictionaryRadio.isChecked = selected
        row.dictionaryDelete.visibility = if (deletable) View.VISIBLE else View.GONE

        row.root.setOnClickListener {
            mExecutor.execute {
                onSelect()
                runOnUiThread { refresh() }
            }
        }
        row.dictionaryDelete.setOnClickListener { confirmDelete(title, onDelete) }

        list.addView(row.root)
    }

    private fun confirmDelete(title: String, delete: () -> Unit)
    {
        MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.dictionary_delete_confirm, title))
                .setPositiveButton(R.string.dictionary_delete_action) { _, _ ->
                    mExecutor.execute {
                        delete()
                        runOnUiThread { refresh() }
                    }
                }
                .setNegativeButton(R.string.dictionary_cancel, null)
                .show()
    }

    /**
     * The import itself runs in a foreground service, so it carries on if you leave this screen or the app
     */
    private fun importDictionary(uri: Uri, pitch: Boolean, frequency: Boolean)
    {
        if (DictionaryImport.isRunning) return

        // The service reads the file after this screen may be gone, so keep the permission to read it
        try
        {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        catch (e: SecurityException)
        {
            // Not every provider offers persistable permissions; the grant passed with the intent still works
        }

        val intent = Intent(this, DictionaryImportService::class.java)
                .setData(uri)
                .putExtra(DictionaryImportService.EXTRA_PITCH, pitch)
                .putExtra(DictionaryImportService.EXTRA_FREQUENCY, frequency)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ContextCompat.startForegroundService(this, intent)

        // Show something right away; the service publishes the real progress in a moment
        DictionaryImport.publish(DictionaryImport.Progress(0, 0, false, false))
    }

    /** Shows the progress of a running import, or the result of one that has just finished */
    private val mImportListener: () -> Unit = { renderImportState() }

    private fun renderImportState()
    {
        val progress = DictionaryImport.progress
        val running = progress != null

        for (section in mSections) section.importButton.isEnabled = !running
        mBinding.importProgress.visibility = if (running) View.VISIBLE else View.GONE
        mBinding.importStatus.visibility = if (running) View.VISIBLE else View.GONE

        if (progress != null)
        {
            if (progress.hasPercent)
            {
                mBinding.importProgress.isIndeterminate = false
                mBinding.importProgress.max = PROGRESS_STEPS
                mBinding.importProgress.setProgressCompat(progress.percent * PROGRESS_STEPS / 100, true)
            }
            else
            {
                mBinding.importProgress.isIndeterminate = true
            }

            mBinding.importStatus.text = when
            {
                progress.finishing -> getString(R.string.dictionary_finishing)
                else -> getString(R.string.dictionary_importing, progress.percent, NumberFormat.getIntegerInstance().format(progress.entries))
            }
        }

        // A result that arrived while this screen was not showing is reported once, now
        DictionaryImport.takeMessage()?.let { message ->
            refresh()
            Snackbar.make(mBinding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onStart()
    {
        super.onStart()
        DictionaryImport.addListener(mImportListener)
        renderImportState()
    }

    override fun onStop()
    {
        DictionaryImport.removeListener(mImportListener)
        super.onStop()
    }

    private fun openLink(url: String)
    {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    companion object
    {
        private const val PROGRESS_STEPS = 1000

        private const val JMDICT_RELEASES_URL = "https://github.com/yomidevs/jmdict-yomitan/releases/latest"
        private const val YOMITAN_DICTIONARIES_URL = "https://yomitan.wiki/dictionaries/"
    }
}
