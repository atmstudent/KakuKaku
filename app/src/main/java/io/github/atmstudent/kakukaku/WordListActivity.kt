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
import androidx.core.view.doOnLayout
import io.github.atmstudent.kakukaku.WordList.CenteredImageSpan
import io.github.atmstudent.kakukaku.WordList.RightEdgeIconSpan
import io.github.atmstudent.kakukaku.WordList.TwoLineCut
import io.github.atmstudent.kakukaku.WordList.TappableSelectionMethod
import io.github.atmstudent.kakukaku.WordList.WordListDisplay
import io.github.atmstudent.kakukaku.WordList.SavedTerm
import io.github.atmstudent.kakukaku.WordList.WordListExport
import io.github.atmstudent.kakukaku.WordList.WordListSettings
import io.github.atmstudent.kakukaku.WordList.WordListStore
import io.github.atmstudent.kakukaku.databinding.ActivityWordListBinding
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
        mBinding.wordListEmpty.visibility = if (terms.isEmpty()) View.VISIBLE else View.GONE

        // Where a definition is cut depends on the width of the lines, which is known once the text has been laid out
        val text = mBinding.wordListText
        if (text.width == 0)
        {
            text.doOnLayout { if (mTerms === terms) show(terms) }
            return
        }

        val onSurface = com.google.android.material.color.MaterialColors.getColor(text, com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(text, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val iconSize = (22 * resources.displayMetrics.density).toInt()
        val lineWidth = text.width - text.totalPaddingLeft - text.totalPaddingRight

        fun icon(drawable: Int, size: Int = iconSize): android.graphics.drawable.Drawable
        {
            val d = androidx.core.content.ContextCompat.getDrawable(this, drawable)!!.mutate()
            d.setTint(onSurfaceVariant)
            d.setBounds(0, 0, size, size)
            return d
        }
        // Not taller than a line of text, or the line with the arrow gets more space than the others
        val arrowSize = (18 * resources.displayMetrics.density).toInt()

        val sb = android.text.SpannableStringBuilder()
        for (term in terms)
        {
            val entryStart = sb.length

            // Like the popup: dictionary form, kana, the pitch accent if a pitch accent dictionary is selected, then the
            // definitions. Definitions of more than two lines are cut short and end in an arrow; tapping the entry
            // opens or closes it.
            val glossary = WordListDisplay.glossary(term.values)
            val cut = TwoLineCut.cut(glossary, text.paint, lineWidth, arrowSize)
            val expandable = cut != null
            val open = term.id in mExpanded

            val headingStart = sb.length
            sb.append(WordListDisplay.heading(term.values, mShowPitch))
            sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), headingStart, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.RelativeSizeSpan(1.2f), headingStart, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.ForegroundColorSpan(onSurface), headingStart, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // The trash icon at the right edge (a space is its text, so copied text has no stray symbols)
            val trash = RightEdgeIconSpan(icon(R.drawable.ic_delete), text) {
                mExecutor.execute {
                    WordListStore.get(this@WordListActivity).delete(term.id)
                    runOnUiThread { mExpanded.remove(term.id); refresh() }
                }
            }
            sb.append(" ")
            sb.setSpan(trash, sb.length - 1, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            if (glossary.isNotEmpty())
            {
                sb.append("\n")
                if (expandable)
                {
                    sb.append(if (open) glossary else cut + "…")
                    sb.append(" ")
                    sb.setSpan(CenteredImageSpan(icon(if (open) R.drawable.ic_arrow_drop_up else R.drawable.ic_arrow_drop_down, arrowSize)), sb.length - 1, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else
                {
                    sb.append(glossary)
                }
            }

            if (expandable)
            {
                sb.setSpan(object : android.text.style.ClickableSpan()
                {
                    override fun onClick(widget: View)
                    {
                        if (open) mExpanded.remove(term.id) else mExpanded.add(term.id)
                        show(mTerms)
                    }

                    override fun updateDrawState(ds: android.text.TextPaint) {}
                }, entryStart, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            sb.append("\n\n")
        }

        text.movementMethod = TappableSelectionMethod()
        text.setText(sb.trimEnd(), android.widget.TextView.BufferType.SPANNABLE)
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
