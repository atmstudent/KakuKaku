package app.mojiscope

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.mojiscope.Dictionary.DictionaryFormatException
import app.mojiscope.Dictionary.DictionarySelection
import app.mojiscope.Dictionary.UserDictionaryStore
import app.mojiscope.databinding.ActivityDictionariesBinding
import app.mojiscope.databinding.ItemDictionaryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.text.NumberFormat
import java.util.concurrent.Executors

/**
 * Choose which dictionary is used for lookups and import personal dictionaries (Yomitan format)
 */
class DictionariesActivity : AppCompatActivity()
{
    private lateinit var mBinding: ActivityDictionariesBinding
    private val mExecutor = Executors.newSingleThreadExecutor()
    private var mImporting = false

    private val mPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDictionary(uri)
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
        mBinding.getJmdictButton.setOnClickListener { openLink(JMDICT_RELEASES_URL) }
        mBinding.moreDictionariesButton.setOnClickListener { openLink(YOMITAN_DICTIONARIES_URL) }

        refresh()
    }

    override fun onDestroy()
    {
        mExecutor.shutdown()
        super.onDestroy()
    }

    private fun refresh()
    {
        val list = mBinding.dictionaryList
        list.removeAllViews()

        val selected = DictionarySelection.get(this)

        addRow(DictionarySelection.BUILT_IN, getString(R.string.dictionary_builtin_title), getString(R.string.dictionary_builtin_subtitle), selected == DictionarySelection.BUILT_IN, false)

        val numbers = NumberFormat.getIntegerInstance()
        for (dictionary in UserDictionaryStore.get(this).list())
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

    private fun importDictionary(uri: Uri)
    {
        if (mImporting) return
        mImporting = true

        mBinding.importButton.isEnabled = false
        mBinding.importProgress.visibility = View.VISIBLE
        mBinding.importStatus.visibility = View.VISIBLE
        mBinding.importStatus.text = getString(R.string.dictionary_importing, 0, "0")

        val numbers = NumberFormat.getIntegerInstance()
        val totalBytes = fileSize(uri)

        // With a known file size the bar shows how much of the zip has been read
        if (totalBytes > 0)
        {
            mBinding.importProgress.isIndeterminate = false
            mBinding.importProgress.max = PROGRESS_STEPS
            mBinding.importProgress.progress = 0
        }
        else
        {
            mBinding.importProgress.isIndeterminate = true
        }

        mExecutor.execute {
            var message: String
            try
            {
                var lastUpdate = 0L

                val result = UserDictionaryStore.get(this).import({ contentResolver.openInputStream(uri) ?: throw IOException("The file could not be opened") }, { count, bytesRead ->
                    // Banks arrive a few thousand entries at a time, so this is not called too often; still skip updates that come too fast
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 100)
                    {
                        lastUpdate = now
                        val percent = if (totalBytes > 0) (bytesRead * 100 / totalBytes).toInt().coerceIn(0, 99) else 0
                        runOnUiThread {
                            if (totalBytes > 0) mBinding.importProgress.setProgressCompat((percent * PROGRESS_STEPS / 100), true)
                            mBinding.importStatus.text = getString(R.string.dictionary_importing, percent, numbers.format(count))
                        }
                    }
                }, {
                    runOnUiThread {
                        if (totalBytes > 0) mBinding.importProgress.setProgressCompat(PROGRESS_STEPS, true)
                        mBinding.importStatus.text = getString(R.string.dictionary_finishing)
                    }
                })

                runOnUiThread {
                    DictionarySelection.set(this, result.dictionary.id)
                }
                message = getString(R.string.dictionary_import_done, result.dictionary.title, numbers.format(result.dictionary.entries))
            }
            catch (e: DictionaryFormatException)
            {
                message = getString(R.string.dictionary_import_failed, e.message)
            }
            catch (e: Exception)
            {
                message = getString(R.string.dictionary_import_failed, e.message ?: e.javaClass.simpleName)
            }

            runOnUiThread {
                mImporting = false
                mBinding.importButton.isEnabled = true
                mBinding.importProgress.visibility = View.GONE
                mBinding.importStatus.visibility = View.GONE
                refresh()
                Snackbar.make(mBinding.root, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /** Size of the picked file in bytes, or -1 if the provider doesn't say */
    private fun fileSize(uri: Uri): Long
    {
        return try
        {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            } ?: -1L
        }
        catch (e: Exception)
        {
            -1L
        }
    }

    private fun openLink(url: String)
    {
        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
    }

    companion object
    {
        private const val PROGRESS_STEPS = 1000

        private const val JMDICT_RELEASES_URL = "https://github.com/yomidevs/jmdict-yomitan/releases/latest"
        private const val YOMITAN_DICTIONARIES_URL = "https://yomitan.wiki/dictionaries/"
    }
}
