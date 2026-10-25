package io.github.atmstudent.kakukaku

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.atmstudent.kakukaku.Anki.Marker
import io.github.atmstudent.kakukaku.Anki.MarkerLabels
import io.github.atmstudent.kakukaku.Dictionary.MetaKind
import io.github.atmstudent.kakukaku.Dictionary.MetaSelection
import io.github.atmstudent.kakukaku.WordList.WordListDisplay
import io.github.atmstudent.kakukaku.WordList.SavedTerm
import io.github.atmstudent.kakukaku.WordList.WordListExport
import io.github.atmstudent.kakukaku.WordList.WordListSettings
import io.github.atmstudent.kakukaku.WordList.WordListStore
import io.github.atmstudent.kakukaku.databinding.ActivityWordListBinding
import io.github.atmstudent.kakukaku.databinding.ItemSavedTermBinding
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** The saved words: view, delete, choose what is shown and exported, export as TSV or CSV */
class WordListActivity : AppCompatActivity()
{
    private lateinit var mBinding: ActivityWordListBinding
    private val mExecutor = Executors.newSingleThreadExecutor()
    private var mTerms: List<SavedTerm> = emptyList()
    // Whether a pitch accent dictionary is selected: only then is the accent shown
    private var mShowPitch = false
    // The terms opened to show everything that was saved about them
    private val mExpanded = HashSet<Long>()

    private val mTsvPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values")) { uri -> if (uri != null) export(uri, true) }
    private val mCsvPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) export(uri, false) }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        mBinding = ActivityWordListBinding.inflate(layoutInflater)
        supportActionBar?.hide()
        setContentView(mBinding.root)

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        mBinding.toolbar.setNavigationOnClickListener { finish() }
        mBinding.toolbar.inflateMenu(R.menu.word_list_menu)
        mBinding.toolbar.setOnMenuItemClickListener {
            when (it.itemId)
            {
                R.id.menu_word_list_information -> chooseInformation()
                R.id.menu_word_list_export_tsv -> mTsvPicker.launch("word-list.tsv")
                R.id.menu_word_list_export_csv -> mCsvPicker.launch("word-list.csv")
                R.id.menu_word_list_clear -> confirmClear()
            }
            true
        }

        refresh()
    }

    override fun onDestroy()
    {
        mExecutor.shutdown()
        super.onDestroy()
    }

    private fun refresh()
    {
        try
        {
            mExecutor.execute {
                val terms = WordListStore.get(this).list()
                val showPitch = MetaSelection.get(this, MetaKind.PITCH) != MetaSelection.NONE
                runOnUiThread { if (!isDestroyed) { mShowPitch = showPitch; show(terms) } }
            }
        }
        catch (e: RejectedExecutionException)
        {
            // The screen is closing
        }
    }

    private fun show(terms: List<SavedTerm>)
    {
        mTerms = terms
        val list = mBinding.wordList
        list.removeAllViews()
        mBinding.wordListEmpty.visibility = if (terms.isEmpty()) View.VISIBLE else View.GONE

        for (term in terms)
        {
            val row = ItemSavedTermBinding.inflate(LayoutInflater.from(this), list, false)

            // The same as the popup shows: dictionary form, kana, the pitch accent if a pitch accent dictionary is selected,
            // and the definitions. Long ones are cut short until the entry is tapped.
            row.savedTitle.text = WordListDisplay.heading(term.values, mShowPitch)
            val glossary = WordListDisplay.glossary(term.values)
            val open = term.id in mExpanded
            row.savedSubtitle.text = glossary
            row.savedSubtitle.maxLines = if (open) Int.MAX_VALUE else 2
            row.savedSubtitle.visibility = if (glossary.isEmpty()) View.GONE else View.VISIBLE

            val toggle = View.OnClickListener {
                if (open) mExpanded.remove(term.id) else mExpanded.add(term.id)
                show(mTerms)
            }
            row.root.setOnClickListener(toggle)
            row.savedTitle.setOnClickListener(toggle)
            row.savedSubtitle.setOnClickListener(toggle)
            row.savedDelete.setOnClickListener {
                mExecutor.execute {
                    WordListStore.get(this).delete(term.id)
                    runOnUiThread { mExpanded.remove(term.id); refresh() }
                }
            }
            list.addView(row.root)
        }
    }

    /** The markers shown and exported; all of them are stored, so this can be changed afterwards */
    private fun chooseInformation()
    {
        val all = Marker.values()
        val chosen = WordListSettings.markers(this).toMutableSet()
        MaterialAlertDialogBuilder(this)
                .setTitle(R.string.word_list_information_title)
                .setMultiChoiceItems(all.map { getString(MarkerLabels.label(it)) }.toTypedArray(), BooleanArray(all.size) { all[it] in chosen }) { _, which, checked ->
                    if (checked) chosen.add(all[which]) else chosen.remove(all[which])
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    WordListSettings.setMarkers(this, chosen)
                }
                .setNegativeButton(R.string.dictionary_cancel, null)
                .show()
    }

    private fun confirmClear()
    {
        if (mTerms.isEmpty()) return
        MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.word_list_clear_confirm, mTerms.size))
                .setPositiveButton(R.string.dictionary_delete_action) { _, _ ->
                    mExecutor.execute {
                        WordListStore.get(this).clear()
                        runOnUiThread { refresh() }
                    }
                }
                .setNegativeButton(R.string.dictionary_cancel, null)
                .show()
    }

    private fun export(uri: Uri, tsv: Boolean)
    {
        val terms = mTerms
        val markers = WordListSettings.markers(this)
        mExecutor.execute {
            val message = try
            {
                val table = WordListExport.table(terms, markers, true)
                val text = if (tsv) WordListExport.tsv(table) else WordListExport.csv(table)
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw java.io.IOException("The file could not be opened")
                getString(R.string.word_list_exported, terms.size)
            }
            catch (e: Exception)
            {
                getString(R.string.word_list_export_failed, e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread { if (!isDestroyed) Snackbar.make(mBinding.root, message, Snackbar.LENGTH_LONG).show() }
        }
    }
}
