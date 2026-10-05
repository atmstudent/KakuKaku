package io.github.atmstudent.kakukaku

import android.net.Uri
import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.atmstudent.kakukaku.Dictionary.DictionaryImport
import io.github.atmstudent.kakukaku.Dictionary.DictionarySelection
import io.github.atmstudent.kakukaku.Dictionary.PitchAccent
import io.github.atmstudent.kakukaku.Dictionary.UserDictionary
import io.github.atmstudent.kakukaku.Dictionary.UserDictionaryStore
import io.github.atmstudent.kakukaku.databinding.ActivityDictionariesBinding
import io.github.atmstudent.kakukaku.databinding.ItemDictionaryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Choose which dictionary is used for lookups and import personal dictionaries (Yomitan format)
 */
class DictionariesActivity : AppCompatActivity()
{
    private lateinit var mBinding: ActivityDictionariesBinding
    private val mExecutor = Executors.newSingleThreadExecutor()

    private val mPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDictionary(uri, false)
    }

    private val mPitchPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDictionary(uri, true)
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
        mBinding.importButton.setOnClickListener { mPicker.launch(arrayOf("*/*")) }
        mBinding.importPitchButton.setOnClickListener { mPitchPicker.launch(arrayOf("*/*")) }
        mBinding.deletePitchButton.setOnClickListener { deletePitch() }
        mBinding.getJmdictButton.setOnClickListener { openLink(JMDICT_RELEASES_URL) }
        mBinding.moreDictionariesButton.setOnClickListener { openLink(YOMITAN_DICTIONARIES_URL) }

        mBinding.pitchSwitch.isChecked = PitchAccent.isEnabled(this)
        mBinding.pitchRow.setOnClickListener {
            val enabled = !mBinding.pitchSwitch.isChecked
            mBinding.pitchSwitch.isChecked = enabled
            PitchAccent.setEnabled(this, enabled)
        }

        refresh()
    }

    override fun onDestroy()
    {
        mExecutor.shutdown()
        super.onDestroy()
    }

    /** Reads the dictionary list off the main thread, then shows it */
    private fun refresh()
    {
        try
        {
            mExecutor.execute {
                val selected = DictionarySelection.get(this)
                val dictionaries = UserDictionaryStore.get(this).list()
                val pitch = UserDictionaryStore.get(this).pitchSource()

                runOnUiThread {
                    if (!isDestroyed)
                    {
                        showDictionaries(selected, dictionaries)
                        showPitch(pitch)
                    }
                }
            }
        }
        catch (e: RejectedExecutionException)
        {
            // The screen is closing
        }
    }

    private fun showPitch(pitch: UserDictionary?)
    {
        mBinding.pitchStatus.text = if (pitch == null)
            getString(R.string.pitch_none)
        else
            getString(R.string.pitch_imported, pitch.title, NumberFormat.getIntegerInstance().format(pitch.entries))
        mBinding.deletePitchButton.visibility = if (pitch == null) View.GONE else View.VISIBLE
    }

    private fun deletePitch()
    {
        mExecutor.execute {
            UserDictionaryStore.get(this).deletePitch()
            runOnUiThread { refresh() }
        }
    }

    private fun showDictionaries(selected: Long, dictionaries: List<UserDictionary>)
    {
        val list = mBinding.dictionaryList
        list.removeAllViews()

        addRow(DictionarySelection.BUILT_IN, getString(R.string.dictionary_builtin_title), getString(R.string.dictionary_builtin_subtitle), selected == DictionarySelection.BUILT_IN, false)

        val numbers = NumberFormat.getIntegerInstance()
        for (dictionary in dictionaries)
        {
            val count = numbers.format(dictionary.entries)
            val subtitle = if (dictionary.revision.isEmpty())
                getString(R.string.dictionary_subtitle_format, count)
            else
                getString(R.string.dictionary_subtitle_revision_format, count, dictionary.revision)

            addRow(dictionary.id, dictionary.title, subtitle, selected == dictionary.id, true)
        }
    }

    private fun addRow(id: Long, title: String, subtitle: String, selected: Boolean, deletable: Boolean)
    {
        val row = ItemDictionaryBinding.inflate(LayoutInflater.from(this), mBinding.dictionaryList, false)
        row.dictionaryTitle.text = title
        row.dictionarySubtitle.text = subtitle
        row.dictionaryRadio.isChecked = selected
        row.dictionaryDelete.visibility = if (deletable) View.VISIBLE else View.GONE

        row.root.setOnClickListener {
            DictionarySelection.set(this, id)
            refresh()
        }
        row.dictionaryDelete.setOnClickListener { confirmDelete(id, title) }

        mBinding.dictionaryList.addView(row.root)
    }

    private fun confirmDelete(id: Long, title: String)
    {
        MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.dictionary_delete_confirm, title))
                .setPositiveButton(R.string.dictionary_delete_action) { _, _ ->
                    mExecutor.execute {
                        UserDictionaryStore.get(this).delete(id)
                        runOnUiThread {
                            if (DictionarySelection.get(this) == id) DictionarySelection.set(this, DictionarySelection.BUILT_IN)
                            refresh()
                        }
                    }
                }
                .setNegativeButton(R.string.dictionary_cancel, null)
                .show()
    }

    /**
     * The import itself runs in a foreground service, so it carries on if you leave this screen or the app
     */
    private fun importDictionary(uri: Uri, pitch: Boolean)
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

        mBinding.importButton.isEnabled = !running
        mBinding.importPitchButton.isEnabled = !running
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
