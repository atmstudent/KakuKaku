package io.github.atmstudent.kakukaku.Windows;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.GestureDetector;
import android.graphics.Point;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.view.animation.AnimationUtils;
import io.github.atmstudent.kakukaku.AnkiActivity;
import io.github.atmstudent.kakukaku.Anki.Marker;
import io.github.atmstudent.kakukaku.Anki.AnkiSettings;
import io.github.atmstudent.kakukaku.Anki.AnkiBridge;
import io.github.atmstudent.kakukaku.Anki.AddResult;
import android.content.Intent;
import io.github.atmstudent.kakukaku.WordList.WordListStore;
import io.github.atmstudent.kakukaku.Anki.TermContext;
import java.util.Map;
import android.widget.Toast;
import android.text.Spanned;
import android.graphics.drawable.Drawable;
import io.github.atmstudent.kakukaku.AppSettings;
import android.widget.TextView;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.github.atmstudent.kakukaku.Constants;
import androidx.core.content.ContextCompat;
import io.github.atmstudent.kakukaku.Search.BackgroundTask;
import io.github.atmstudent.kakukaku.Furigana.Furigana;
import io.github.atmstudent.kakukaku.Database.JmDictDatabase.Models.EntryOptimized;
import io.github.atmstudent.kakukaku.KakuKakuTools;
import io.github.atmstudent.kakukaku.LangUtils;
import io.github.atmstudent.kakukaku.R;
import io.github.atmstudent.kakukaku.Search.JmSearchResult;
import io.github.atmstudent.kakukaku.Search.SearchInfo;
import io.github.atmstudent.kakukaku.Search.Searcher;
import io.github.atmstudent.kakukaku.Windows.Data.DisplayData;
import io.github.atmstudent.kakukaku.Windows.Data.ISquareChar;
import io.github.atmstudent.kakukaku.Windows.Data.SquareChar;
import io.github.atmstudent.kakukaku.Windows.Interfaces.ICopyText;
import io.github.atmstudent.kakukaku.Windows.Interfaces.IRecalculateKanjiViews;
import io.github.atmstudent.kakukaku.Windows.Interfaces.ISearchPerformer;
import io.github.atmstudent.kakukaku.Windows.Views.KanjiGridView;
import io.github.atmstudent.kakukaku.Windows.Views.MaxHeightLinearLayout;

/**
 * Created by 0xbad1d3a5 on 4/23/2016.
 */
public class InformationWindow extends Window implements Searcher.SearchDictDone, IRecalculateKanjiViews, ISearchPerformer, ICopyText
{

    private static final String TAG = InformationWindow.class.getName();
    private static final float FLICK_THRESHOLD = -0.05f;
    private static final int FLICK_DISTANCE_BOTTOM_DP = 60;

    // A landscape screen shorter than this (a phone) has room for two rows of characters only
    private static final float LANDSCAPE_TALL_SCREEN_DP = 600f;

    private GestureDetector mGestureDetector;
    private float mMaxFlingVelocity;
    private LinearLayout mInfoWindow;
    private KanjiGridView mKanjiGrid;
    private LinearLayout mDictResults;
    private Searcher mSearcher;
    private boolean mTextOnlyLookup;
    private TextView mFuriganaToggle;
    // The results on show and the search they came from (for saving a term), and the terms saved since the popup opened
    private List<JmSearchResult> mLastResults = new ArrayList<>();
    private SearchInfo mLastSearch;
    private final java.util.Set<String> mSavedKeys = new java.util.HashSet<>();
    private final java.util.Set<String> mAnkiKeys = new java.util.HashSet<>();
    // The text of a share-menu lookup before furigana was stripped (null for a scan)
    private String mRawText;
    // The card is at the bottom of the screen (the setting, in portrait only); swipes and the slide-out then go down
    private boolean mAtBottom;
    // The definitions slide in when the popup shows its first result, not when another character is selected
    private boolean mFirstResultPending = true;
    private ArrayList<ISquareChar> mSearchedChars = new ArrayList<>();

    public InformationWindow(Context context, WindowCoordinator windowCoordinator)
    {
        super(context, windowCoordinator, R.layout.window_info);

        mMaxFlingVelocity = ViewConfiguration.get(this.context).getScaledMaximumFlingVelocity();
        mGestureDetector = new GestureDetector(this.context, this);
        mInfoWindow = window.findViewById(R.id.info_window);
        mKanjiGrid = window.findViewById(R.id.kanji_grid);
        mDictResults = window.findViewById(R.id.dict_results);
        mFuriganaToggle = window.findViewById(R.id.furigana_toggle);
        mFuriganaToggle.setOnClickListener(v -> toggleFurigana());

        mKanjiGrid.setDependencies(windowCoordinator, this);

        // The popup is as large as the screen, so its own size tells when the screen has been rotated. Windows opened
        // from the share menu are not told about rotation by MainService, and the display size can lag behind it.
        window.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            boolean sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop);
            if (sizeChanged && right > left && bottom > top) {
                // Not during the layout pass: changing the layout params requests another one
                window.post(this::applyOrientationLayout);
            }
        });

        try {
            mSearcher = new Searcher(context);
            mSearcher.registerCallback(this);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        show();
    }

    public void setResult(DisplayData displayData)
    {
        mSearchedChars = new ArrayList<>();
        mSavedKeys.clear();
        mAnkiKeys.clear();
        mRawText = null;

        mKanjiGrid.removeAllViews();
        mKanjiGrid.setText(displayData);

        // Tesseract scans keep the furigana, so the switch would do nothing there
        updateFuriganaToggle(AppSettings.ocrEngine(context) == AppSettings.ENGINE_ML_KIT);
    }

    /** Text from the share menu: [textResult] is shown, [rawText] (with furigana) is what the switch strips again */
    public void setResult(String textResult, String rawText)
    {
        mRawText = rawText;
        showText(textResult);
        mTextOnlyLookup = true;

        updateFuriganaToggle(true);
    }

    private void showText(String text)
    {
        mSearchedChars = new ArrayList<>();
        mSavedKeys.clear();
        mAnkiKeys.clear();

        List<String> charList = KakuKakuTools.splitTextByChar(text);
        List<ISquareChar> squareCharList = new ArrayList<>();
        DisplayData displayData = new DisplayData(squareCharList);
        for (String c : charList) squareCharList.add(new SquareChar(displayData, c));
        displayData.assignIndicies();

        mKanjiGrid.removeAllViews();
        mKanjiGrid.setText(displayData);
        if (!displayData.getSquareChars().isEmpty()) performSearch(displayData.getSquareChars().get(0));
    }

    private void updateFuriganaToggle(boolean available)
    {
        mFuriganaToggle.setVisibility(available ? View.VISIBLE : View.GONE);

        boolean on = Furigana.INSTANCE.isEnabled(context);
        mFuriganaToggle.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_off);
        mFuriganaToggle.setTextColor(ContextCompat.getColor(context, on ? R.color.window_surface : R.color.window_on_surface));
    }

    /** The switch: remembers the choice (it is the one in Settings) and shows the text again with or without furigana */
    private void toggleFurigana()
    {
        Furigana.INSTANCE.setEnabled(context, !Furigana.INSTANCE.isEnabled(context));
        updateFuriganaToggle(true);

        if (mRawText != null)
        {
            final Context appContext = context.getApplicationContext();
            final String raw = mRawText;
            new BackgroundTask<String>()
            {
                @Override
                protected String doInBackground()
                {
                    try { return Furigana.INSTANCE.strip(appContext, raw); } catch (Exception e) { return raw; }
                }

                @Override
                protected void onPostExecute(String result)
                {
                    // Not if the popup was closed or filled with something else in the meantime
                    if (mRawText == raw) showText(result);
                }
            }.execute();
        }
        else if (windowCoordinator != null && windowCoordinator.hasWindow(Constants.WINDOW_CAPTURE))
        {
            // A scan: read the same picture again, now with the new setting
            ((CaptureWindow) windowCoordinator.getWindow(Constants.WINDOW_CAPTURE)).rescan();
        }
    }

    public void copyText()
    {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(null, mKanjiGrid.getText());
        clipboard.setPrimaryClip(clip);

        hide();
    }

    @Override
    public void performSearch(@NotNull ISquareChar squareChar)
    {
        Log.d(TAG, squareChar.getChar());

        mKanjiGrid.unhighlightAll(squareChar);
        mSearcher.search(new SearchInfo(squareChar));
    }

    /**
     * InformationWindow does not need to reInit layout as its getDefaultParams() are all relative. Re-initing will cause bugs.
     */
    @Override
    public void reInit(ReinitOptions options)
    {
        options.reinitViewLayout = false;
        super.reInit(options);
        applyOrientationLayout();
    }

    /**
     * Portrait: a card across the top, never taller than two thirds of the screen.
     * Landscape: a panel from the top to the bottom of the screen on the left, at most two thirds of its width,
     * and on a phone (a screen that is short in landscape) at most two rows of characters.
     */
    private void applyOrientationLayout()
    {
        // The window covers the whole screen, so its size is the current screen size
        Point size = window.getWidth() > 0 && window.getHeight() > 0
                ? new Point(window.getWidth(), window.getHeight())
                : getRealDisplaySize();
        boolean landscape = size.x > size.y;
        mAtBottom = !landscape && AppSettings.popupAtBottom(context);
        float heightDp = size.y / context.getResources().getDisplayMetrics().density;

        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) mInfoWindow.getLayoutParams();
        MaxHeightLinearLayout infoWindow = (MaxHeightLinearLayout) mInfoWindow;
        if (landscape)
        {
            int margin = KakuKakuTools.dpToPx(context, 10);
            layout.width = size.x * 2 / 3 - margin;
            layout.height = ViewGroup.LayoutParams.MATCH_PARENT;
            layout.gravity = Gravity.TOP | Gravity.START;
            layout.setMargins(margin, margin, 0, margin);
            infoWindow.setMaxHeightPx(0);
            mKanjiGrid.setMaxRows(heightDp < LANDSCAPE_TALL_SCREEN_DP ? 2 : 0);
        }
        else
        {
            int margin = KakuKakuTools.dpToPx(context, 10);
            layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            layout.gravity = mAtBottom ? Gravity.BOTTOM : FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY;
            layout.setMargins(margin, margin, margin, mAtBottom ? margin : KakuKakuTools.dpToPx(context, 100));
            infoWindow.setMaxHeightPx(size.y * 2 / 3);
            mKanjiGrid.setMaxRows(0);
        }
        mInfoWindow.setLayoutParams(layout);
    }

    @Override
    protected WindowManager.LayoutParams getDefaultParams()
    {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT > 25 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        params.x = 0;
        params.y = 0;
        params.gravity = Gravity.TOP | Gravity.CENTER;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
        return params;
    }

    @Override
    public boolean onTouch(MotionEvent e)
    {
        mGestureDetector.onTouchEvent(e);

        if (e.getAction() == MotionEvent.ACTION_UP){
            params.y = 0;
            windowManager.updateViewLayout(window, params);
            return true;
        }

        return false;
    }

    @Override
    public void show()
    {
        mDictResults.removeAllViews();
        mFirstResultPending = true;

        applyOrientationLayout();

        window.setVisibility(View.VISIBLE);
        params.y = 0; // onScroll changes this value
        super.show();
        window.setY(0); // translationY changes this value
    }

    @Override
    public void hide()
    {
        window.animate().translationY(mAtBottom ? getRealDisplaySize().y : -getRealDisplaySize().y).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation)
            {
                window.setVisibility(View.INVISIBLE);

                if (!mTextOnlyLookup)
                {
                    InformationWindow.super.hide();
                }
                else
                {
                    // hide() may have been called multiple times - this isn't thread-safe but whatever
                    if (windowCoordinator != null)
                    {
                        windowCoordinator.stopAllWindows();
                    }
                }
            }
        });
    }

    @Override
    public void stop()
    {
        mSearcher.unregisterCallback();
        super.stop();
    }

    @Override
    public boolean onResize(MotionEvent e)
    {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent motionEvent)
    {
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e)
    {
        if (mInfoWindow.getX() < e.getX() && e.getX() < (mInfoWindow.getX() + mInfoWindow.getWidth()) &&
            mInfoWindow.getY() < e.getY() && e.getY() < (mInfoWindow.getY() + mInfoWindow.getHeight()))
        {
            // Do nothing
        }
        else
        {
            hide();
        }

        return true;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e)
    {
        if (e.getY() < mKanjiGrid.getY() + mKanjiGrid.getHeight())
        {
            int triggerAreaSize = getViewWidth() / 8;
            if (e.getX() > getViewWidth() - triggerAreaSize)
            {
                mKanjiGrid.scrollNext();
            }
            else if (e.getX() < triggerAreaSize)
            {
                mKanjiGrid.scrollPrev();
            }
        }
        else if (mInfoWindow.getX() < e.getX() && e.getX() < (mInfoWindow.getX() + mInfoWindow.getWidth()) &&
                 mInfoWindow.getY() < e.getY() && e.getY() < (mInfoWindow.getY() + mInfoWindow.getHeight()))
        {
            // Do nothing
        }
        else
        {
            hide();
        }

        return true;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1)
    {
        if (motionEvent == null || motionEvent1 == null){
            return false;
        }

        params.y = (int) (motionEvent1.getRawY() - motionEvent.getRawY());
        if (mAtBottom ? params.y < 0 : params.y > 0){
            params.y = 0;
        }
        windowManager.updateViewLayout(window, params);

        return true;
    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1)
    {
        if (motionEvent == null || motionEvent1 == null){
            return false;
        }

        float distanceMoved = motionEvent.getRawY() - motionEvent1.getRawY();

        Log.d(TAG, String.format("Fling strength: %f", v1 / mMaxFlingVelocity));
        Log.d(TAG, String.format("Distance moved: %f", distanceMoved));

        float flingStrength = v1 / mMaxFlingVelocity;
        // The window follows the finger while it is dragged, so the velocity is measured against a moving window:
        // at the bottom a downward swipe is judged by how far it went on the screen instead
        boolean dismissed = mAtBottom
                ? distanceMoved < -KakuKakuTools.dpToPx(context, FLICK_DISTANCE_BOTTOM_DP)
                : flingStrength < FLICK_THRESHOLD;
        if (dismissed)
        {
            hide();
            return true;
        }

        return false;
    }

    @Override
    public void jmResultsCallback(List<JmSearchResult> results, SearchInfo search)
    {
        windowCoordinator.getWindow(Constants.WINDOW_INSTANT_KANJI).hide();

        mLastResults = results;
        mLastSearch = search;

        if (results.size() > 0)
        {
            displayResults(results);

            if (search.getSquareChar().getUserTouched() && !mSearchedChars.contains(search.getSquareChar()))
            {
                //windowCoordinator.<HistoryWindow>getWindowOfType(Constants.WINDOW_HISTORY).addResult(search.getSquareChar(), results);
                mSearchedChars.add(search.getSquareChar());
            }
        }

        // Highlights words in the window as long as they match
        int start = search.getIndex() - mKanjiGrid.getOffset();
        if (results.size() > 0){
            String kanji = results.get(0).getWord();
            for (int i = start; i < start + kanji.codePointCount(0, kanji.length()); i++){
                if (i >= mKanjiGrid.getKanjiViewList().size()){
                    break;
                }
                mKanjiGrid.getKanjiViewList().get(i).highlight();
            }
        }
        else {
            mKanjiGrid.getKanjiViewList().get(start).highlight();
        }
    }

    @Override
    public void recalculateKanjiViews()
    {
        mKanjiGrid.recalculateKanjiViews();
    }

    private void displayResults(List<JmSearchResult> jmResults)
    {
        mDictResults.removeAllViews();

        // The text size setting may have changed since the popup was last shown
        float textSizeSp = 14f * AppSettings.popupScale(context);
        int textColor = ContextCompat.getColor(mDictResults.getContext(), R.color.window_on_surface);

        for (int index = 0; index < jmResults.size(); index++)
        {
            final JmSearchResult jmSearchResult = jmResults.get(index);

            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append(jmSearchResult.getEntry().getKanji());

            if (!jmSearchResult.getEntry().getReadings().isEmpty()){
                if (!Constants.DB_KANJIDICT_NAME.equals(jmSearchResult.getEntry().getDictionary()))
                {
                    sb.append(" (");
                }
                else {
                    sb.append(" ");
                }
                sb.append(jmSearchResult.getEntry().getReadings());
                if (!Constants.DB_KANJIDICT_NAME.equals(jmSearchResult.getEntry().getDictionary())) sb.append(")");
            }

            int pitchStart = -1;
            int pitchEnd = -1;
            if (!jmSearchResult.getPitch().isEmpty()){
                sb.append(" ");
                pitchStart = sb.length();
                sb.append(jmSearchResult.getPitch());
                pitchEnd = sb.length();
            }

            int reasonStart = -1;
            String deinfReason = jmSearchResult.getDeinfInfo().getReason();
            if (deinfReason != null && !deinfReason.isEmpty()){
                sb.append(" ");
                reasonStart = sb.length();
                sb.append(deinfReason);
            }

            HeadingStyle.apply(context, sb, 0, pitchStart, pitchEnd, reasonStart);

            LinearLayout row = new LinearLayout(mDictResults.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (index > 0) rowParams.topMargin = KakuKakuTools.dpToPx(context, 16);

            // The heading, and the icons at the right edge: send the term to Anki, save it to the word list
            LinearLayout heading = new LinearLayout(mDictResults.getContext());
            heading.setOrientation(LinearLayout.HORIZONTAL);

            TextView headingText = new TextView(mDictResults.getContext());
            headingText.setTextColor(textColor);
            headingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            headingText.setText(sb);
            heading.addView(headingText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            final List<ImageView> icons = new ArrayList<>();
            if (AnkiSettings.INSTANCE.showAnkiIcon(context))
            {
                icons.add(addIconButton(heading, mAnkiKeys.contains(termKey(jmSearchResult)) ? R.drawable.ic_check : R.drawable.ic_anki, textColor,
                        R.string.anki_add_description, () -> addToAnki(jmSearchResult)));
            }
            if (AnkiSettings.INSTANCE.showListIcon(context))
            {
                icons.add(addIconButton(heading, mSavedKeys.contains(termKey(jmSearchResult)) ? R.drawable.ic_check : R.drawable.ic_list_add, textColor,
                        R.string.word_list_save_description, () -> saveToWordList(jmSearchResult)));
            }

            // The icons are as tall as the first line of the heading and centred on it, so they neither sit lower than the
            // text nor make the row taller (a row of 40dp icons left a gap between the heading and the definitions)
            headingText.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                Layout layout = headingText.getLayout();
                if (layout == null || layout.getLineCount() == 0) return;
                final int lineHeight = layout.getLineBottom(0) - layout.getLineTop(0);
                for (ImageView icon : icons)
                {
                    if (icon.getLayoutParams().height != lineHeight)
                    {
                        icon.post(() -> {
                            icon.getLayoutParams().height = lineHeight;
                            int vertical = Math.round(lineHeight * 0.1f);
                            icon.setPadding(icon.getPaddingLeft(), vertical, icon.getPaddingRight(), vertical);
                            icon.requestLayout();
                        });
                    }
                }
            });
            row.addView(heading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView meaning = new TextView(mDictResults.getContext());
            meaning.setTextColor(textColor);
            meaning.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            meaning.setText(getMeaning(jmSearchResult.getEntry()));
            row.addView(meaning, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            mDictResults.addView(row, rowParams);
        }

        if (mFirstResultPending)
        {
            // The definitions slide in the first time; selecting another character replaces them in place
            mDictResults.startAnimation(AnimationUtils.loadAnimation(context, R.anim.slide_in));
            mFirstResultPending = false;
        }
    }

    private static String termKey(JmSearchResult result)
    {
        return result.getEntry().getKanji() + "|" + result.getEntry().getReadings();
    }

    /** An icon button at the right end of a result's heading */
    private ImageView addIconButton(LinearLayout parent, int drawableRes, int color, int descriptionRes, final Runnable onClick)
    {
        ImageView button = new ImageView(parent.getContext());
        Drawable icon = ContextCompat.getDrawable(context, drawableRes).mutate();
        icon.setTint(color);
        button.setImageDrawable(icon);
        button.setContentDescription(context.getString(descriptionRes));
        // Sized to the heading's first line once that is laid out (see displayResults)
        int horizontal = KakuKakuTools.dpToPx(context, 8);
        button.setPadding(horizontal, 0, horizontal, 0);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setOnClickListener(v -> onClick.run());
        parent.addView(button, new LinearLayout.LayoutParams(KakuKakuTools.dpToPx(context, 40), KakuKakuTools.dpToPx(context, 20)));
        return button;
    }

    /** Adds a card for the term to AnkiDroid, as set up in the Anki menu */
    private void addToAnki(final JmSearchResult result)
    {
        if (mLastSearch == null) return;

        final Context appContext = context.getApplicationContext();
        if (!AnkiBridge.INSTANCE.isInstalled(appContext))
        {
            Toast.makeText(appContext, R.string.anki_missing, Toast.LENGTH_LONG).show();
            return;
        }
        if (!AnkiBridge.INSTANCE.hasPermission(appContext) || !AnkiSettings.INSTANCE.isConfigured(appContext))
        {
            // Setting up needs a screen of its own (the permission dialog cannot be shown from here)
            Toast.makeText(appContext, R.string.anki_choose_first, Toast.LENGTH_LONG).show();
            appContext.startActivity(new Intent(appContext, AnkiActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }

        final Map<String, String> values = TermContext.Companion.from(result, mLastSearch.getText(), mLastSearch.getTextOffset()).values();
        final String expression = values.get(Marker.EXPRESSION.getId());
        new BackgroundTask<String[]>()
        {
            @Override
            protected String[] doInBackground()
            {
                try
                {
                    long modelId = AnkiSettings.INSTANCE.modelId(appContext);
                    java.util.List<String> fieldNames = AnkiBridge.INSTANCE.fieldNames(appContext, modelId);
                    java.util.List<String> fields = AnkiSettings.INSTANCE.fillFields(appContext, fieldNames, values);
                    java.util.Set<String> tags = new java.util.LinkedHashSet<>();
                    for (String tag : AnkiSettings.INSTANCE.tags(appContext).trim().split("\\s+")) if (!tag.isEmpty()) tags.add(tag);

                    AddResult added = AnkiBridge.INSTANCE.addNote(appContext, modelId, AnkiSettings.INSTANCE.modelName(appContext), fieldNames,
                            AnkiSettings.INSTANCE.deckId(appContext), fields, tags, false);
                    return new String[]{added.name(), null};
                }
                catch (Exception e)
                {
                    return new String[]{"FAILED", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()};
                }
            }

            @Override
            protected void onPostExecute(String[] outcome)
            {
                if (outcome[0].equals("FAILED"))
                {
                    Toast.makeText(appContext, appContext.getString(R.string.anki_failed, outcome[1] != null ? outcome[1] : ""), Toast.LENGTH_LONG).show();
                    return;
                }
                boolean added = outcome[0].equals("ADDED");
                Toast.makeText(appContext, appContext.getString(added ? R.string.anki_added : R.string.anki_duplicate, expression), Toast.LENGTH_SHORT).show();
                mAnkiKeys.add(termKey(result));
                if (!mLastResults.isEmpty()) displayResults(mLastResults);
            }
        }.execute();
    }

    private void saveToWordList(final JmSearchResult result)
    {
        if (mLastSearch == null) return;

        final Context appContext = context.getApplicationContext();
        final Map<String, String> values = TermContext.Companion.from(result, mLastSearch.getText(), mLastSearch.getTextOffset()).values();
        new BackgroundTask<Boolean>()
        {
            @Override
            protected Boolean doInBackground()
            {
                try { return WordListStore.Companion.get(appContext).add(values); } catch (Exception e) { return null; }
            }

            @Override
            protected void onPostExecute(Boolean added)
            {
                if (added == null)
                {
                    Toast.makeText(appContext, "Could not save the word", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(appContext, added ? R.string.word_list_saved : R.string.word_list_already, Toast.LENGTH_SHORT).show();
                mSavedKeys.add(termKey(result));
                if (!mLastResults.isEmpty()) displayResults(mLastResults);
            }
        }.execute();
    }

    private String getMeaning(EntryOptimized entry)
    {
        String[] meanings = entry.getMeanings().split("\ufffc", -1);
        String[] pos = entry.getPos().split("\ufffc", -1);

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < meanings.length; i++){
            if (i != 0){
                sb.append(" ");
            }
            sb.append(LangUtils.Companion.ConvertIntToCircledNum(i + 1));
            sb.append(" ");
            if (!Constants.DB_KANJIDICT_NAME.equals(entry.getDictionary()) && !pos[i].isEmpty()){
                sb.append(String.format("(%s) ", pos[i]));
            }
            sb.append(meanings[i]);
        }

        return sb.toString();
    }
}