package com.shawnxe7en.myowntv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Search: type (or say) a title, see which of this TV's streaming apps have it, and open the app on it. A title can be
 * saved to My list (the second button), which brings the same rows back later. The Home screen's Search button opens this.
 * Plain views, not the home screen's hand-drawn canvas: the TV's own keyboard, D-pad focus and TalkBack work as they do
 * everywhere.
 */
public class SearchActivity extends Activity {
    private static final int SPEECH = 1, MIN_CHARS = 2;
    private static final long TYPING_PAUSE_MS = 500;
    private static final int INK = 0xFFFFFFFF, DIM = 0xB3FFFFFF, GOOD = 0xFF8FE3AE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> posters = new LruCache<>(48);

    private float k;
    private EditText field;
    private TextView status, mineButton;
    private ListView list;
    private Rows rows;

    private final List<Finder.Hit> shown = new ArrayList<>();
    private List<MyList.Entry> mine = new ArrayList<>();
    private boolean showingMine;
    /** Bumped for each search: an answer that arrives for an earlier one is dropped. */
    private int seq;
    private final Runnable searchNow = () -> search(field.getText().toString().trim());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        k = dm.widthPixels / 1920f;
        mine = MyList.parse(Prefs.myList());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0F14);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(px(96), px(56), px(96), px(32));
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        TextView title = text(Ui.tr("Search"), 44, Ui.semibold, INK);
        column.addView(title, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, px(88));
        barLp.topMargin = px(20);
        column.addView(bar, barLp);

        field = new EditText(this);
        field.setSingleLine(true);
        field.setTextColor(INK);
        field.setHintTextColor(0x80FFFFFF);
        field.setHint(Ui.tr("A movie or show"));
        field.setTypeface(Ui.medium);
        field.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(30));
        field.setPadding(px(32), 0, px(32), 0);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setBackground(box(0x1FFFFFFF, 0));
        field.setOnFocusChangeListener((v, focus) -> field.setBackground(box(focus ? 0x38FFFFFF : 0x1FFFFFFF, focus ? 0xFFFFFFFF : 0)));
        field.setOnEditorActionListener((v, action, event) -> {
            handler.removeCallbacks(searchNow);
            search(field.getText().toString().trim());
            return false;   // the keyboard closes
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                handler.removeCallbacks(searchNow);
                handler.postDelayed(searchNow, TYPING_PAUSE_MS);
            }
        });
        bar.addView(field, new LinearLayout.LayoutParams(0, -1, 1));

        if (speechAvailable()) {
            TextView mic = pill(Ui.tr("Speak"));
            mic.setOnClickListener(v -> listen());
            bar.addView(mic, gap());
        }
        mineButton = pill("");
        mineButton.setOnClickListener(v -> toggleMine());
        bar.addView(mineButton, gap());

        status = text("", 24, Ui.regular, DIM);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = px(18);
        statusLp.bottomMargin = px(10);
        column.addView(status, statusLp);

        list = new ListView(this);
        rows = new Rows();
        list.setAdapter(rows);
        list.setDivider(null);
        list.setSelector(box(0x2EFFFFFF, 0));
        list.setDrawSelectorOnTop(false);
        list.setClipToPadding(false);
        list.setOnItemClickListener((p, v, i, id) -> choose(shown.get(i)));
        column.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        field.requestFocus();
        labelMine();
        hint();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        pool.shutdownNow();
        super.onDestroy();
    }

    // MARK: - Searching

    private void hint() {
        if (Recs.key().isEmpty()) {
            say(Ui.tr("Search needs a free TMDB key. Add it in MyOwnTV Settings › TMDB key."));
        } else {
            say(Ui.tr("Type a title, or press Speak."));
        }
    }

    private void search(String query) {
        showingMine = false;
        labelMine();
        int mySeq = ++seq;
        if (Recs.key().isEmpty()) {
            clear();
            hint();
            return;
        }
        if (query.length() < MIN_CHARS) {
            clear();
            hint();
            return;
        }
        say(Ui.tr("Searching…"));
        pool.execute(() -> {
            List<Finder.Hit> hits;
            try {
                hits = Finder.search(query);
            } catch (Exception e) {
                AppLog.w("search: " + e.getMessage());
                handler.post(() -> {
                    if (mySeq != seq) return;
                    clear();
                    say(Ui.trf("Couldn't search now: {error}", "error", e.getMessage() == null ? e.toString() : e.getMessage()));
                });
                return;
            }
            handler.post(() -> {
                if (mySeq != seq) return;
                shown.clear();
                shown.addAll(hits);
                rows.notifyDataSetChanged();
                list.setSelection(0);
                say(hits.isEmpty() ? Ui.trf("No titles found for “{query}”.", "query", query) : "");
            });
            for (Finder.Hit h : hits) fill(h, mySeq);
        });
    }

    /** Asks which of the TV's apps carry `h`, off the UI thread; the row updates when it's known. */
    private void fill(Finder.Hit h, int mySeq) {
        pool.execute(() -> {
            try {
                Finder.fill(getApplicationContext(), h);
            } catch (Exception e) {
                AppLog.w("search apps: " + e.getMessage());
                h.checked = true;   // no apps known: the row says so instead of "Checking" forever
            }
            handler.post(() -> {
                if (mySeq == seq) rows.notifyDataSetChanged();
            });
        });
    }

    private void clear() {
        shown.clear();
        rows.notifyDataSetChanged();
    }

    private void say(String text) {
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // MARK: - My list

    private void labelMine() {
        mineButton.setText(showingMine ? Ui.tr("Search") : mine.isEmpty() ? Ui.tr("My list") : Ui.trf("My list ({n})", "n", mine.size()));
    }

    private void toggleMine() {
        if (showingMine) {
            search(field.getText().toString().trim());
            return;
        }
        handler.removeCallbacks(searchNow);
        showingMine = true;
        labelMine();
        int mySeq = ++seq;
        shown.clear();
        for (MyList.Entry e : mine) {
            Finder.Hit h = new Finder.Hit(e.kind, e.id, e.title);
            h.year = e.year;
            shown.add(h);
        }
        rows.notifyDataSetChanged();
        say(mine.isEmpty() ? Ui.tr("Nothing saved yet. Open a title and choose Save to My list.") : "");
        if (!Recs.key().isEmpty()) for (Finder.Hit h : new ArrayList<>(shown)) pool.execute(() -> {
            try {
                Finder.Hit more = Finder.details(new MyList.Entry(h.kind, h.id, h.title, h.year));
                h.overview = more.overview;
                h.poster = more.poster;
                if (!more.year.isEmpty()) h.year = more.year;
            } catch (Exception e) {
                AppLog.w("my list: " + e.getMessage());
            }
            fill(h, mySeq);
        });
        list.requestFocus();
    }

    private void save(Finder.Hit h, boolean on) {
        mine = on ? MyList.add(mine, new MyList.Entry(h.kind, h.id, h.title, h.year)) : MyList.remove(mine, h.kind, h.id);
        Prefs.setMyList(MyList.serialize(mine));
        Toast.makeText(this, on ? Ui.tr("Saved to My list") : Ui.tr("Removed from My list"), Toast.LENGTH_SHORT).show();
        if (showingMine && !on) {
            shown.remove(h);
            say(shown.isEmpty() ? Ui.tr("Nothing saved yet. Open a title and choose Save to My list.") : "");
        }
        labelMine();
        rows.notifyDataSetChanged();
    }

    // MARK: - A title: open it in an app, or save it

    private void choose(Finder.Hit h) {
        List<String> labels = new ArrayList<>();
        for (Finder.App a : h.apps) labels.add(Ui.trf("Open in {app}", "app", a.name));
        boolean saved = MyList.contains(mine, h.kind, h.id);
        labels.add(saved ? Ui.tr("Remove from My list") : Ui.tr("Save to My list"));
        int saveAt = labels.size() - 1;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(h.year.isEmpty() ? h.title : h.title + " (" + h.year + ")")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == saveAt) save(h, !saved);
                    else open(h, h.apps.get(which));
                })
                .setNegativeButton(Ui.tr("Cancel"), null)
                .show();
    }

    /** The title's own page in the app if Watchmode knows it, else the app itself. */
    private void open(Finder.Hit h, Finder.App app) {
        Toast.makeText(this, Ui.trf("Opening {app}…", "app", app.name), Toast.LENGTH_SHORT).show();
        pool.execute(() -> {
            String link = Finder.link(h, app);
            handler.post(() -> {
                if (!HomeApps.open(this, HomeApps.app(app.pkg, app.name, link), null)) {
                    Toast.makeText(this, Ui.trf("Couldn't open {app}.", "app", app.name), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    // MARK: - Speaking

    private boolean speechAvailable() {
        return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(getPackageManager()) != null;
    }

    private void listen() {
        Intent ask = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, Ui.tr("Say a title"))
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(ask, SPEECH);
        } catch (ActivityNotFoundException | SecurityException e) {
            say(Ui.tr("This TV has no speech service to listen with."));
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SPEECH || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (heard == null || heard.isEmpty()) return;
        handler.removeCallbacks(searchNow);
        field.setText(heard.get(0));
        field.setSelection(field.length());
        handler.removeCallbacks(searchNow);   // setText armed the typing pause: search now
        search(heard.get(0).trim());
        list.requestFocus();
    }

    // MARK: - The rows

    private final class Rows extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View convert, ViewGroup parent) {
            Row row = convert instanceof Row ? (Row) convert : new Row();
            row.bind(shown.get(i));
            return row;
        }
    }

    private final class Row extends LinearLayout {
        final ImageView poster;
        final TextView name, line, story;
        String shownPoster = "";

        Row() {
            super(SearchActivity.this);
            setOrientation(HORIZONTAL);
            setPadding(px(20), px(14), px(20), px(14));
            setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            poster = new ImageView(SearchActivity.this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(0x1FFFFFFF);
            poster.setClipToOutline(true);
            poster.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(10)); }
            });
            addView(poster, new LayoutParams(px(100), px(150)));
            LinearLayout column = new LinearLayout(SearchActivity.this);
            column.setOrientation(VERTICAL);
            column.setGravity(Gravity.CENTER_VERTICAL);
            LayoutParams columnLp = new LayoutParams(0, px(150), 1);
            columnLp.leftMargin = px(28);
            addView(column, columnLp);
            name = text("", 32, Ui.semibold, INK);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            line = text("", 24, Ui.medium, DIM);
            line.setSingleLine(true);
            line.setEllipsize(TextUtils.TruncateAt.END);
            story = text("", 21, Ui.regular, 0x80FFFFFF);
            story.setMaxLines(2);
            story.setEllipsize(TextUtils.TruncateAt.END);
            column.addView(name);
            column.addView(line);
            column.addView(story);
        }

        void bind(Finder.Hit h) {
            boolean saved = MyList.contains(mine, h.kind, h.id);
            name.setText((saved ? "★ " : "") + h.title + (h.year.isEmpty() ? "" : "  " + h.year));
            String what = h.kind.equals("movie") ? Ui.tr("Movie") : Ui.tr("Series");
            if (!h.checked) {
                line.setText(what + " · " + Ui.tr("Checking your apps…"));
                line.setTextColor(DIM);
            } else if (h.apps.isEmpty()) {
                line.setText(what + " · " + Ui.tr("Not on your apps"));
                line.setTextColor(DIM);
            } else {
                StringBuilder apps = new StringBuilder();
                for (Finder.App a : h.apps) apps.append(apps.length() == 0 ? "" : ", ").append(a.name);
                line.setText(what + " · " + Ui.trf("On {apps}", "apps", apps.toString()));
                line.setTextColor(GOOD);
            }
            story.setText(h.overview);
            if (!h.poster.equals(shownPoster)) {
                shownPoster = h.poster;
                poster.setImageDrawable(null);
                loadPoster(h.poster, poster);
            }
        }
    }

    private void loadPoster(String path, ImageView into) {
        if (path.isEmpty()) return;
        Bitmap have = posters.get(path);
        if (have != null) {
            into.setImageBitmap(have);
            return;
        }
        into.setTag(path);
        pool.execute(() -> {
            try {
                byte[] bytes = NetPicture.get("https://image.tmdb.org/t/p/w185" + path);
                Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (b == null) return;
                posters.put(path, b);
                handler.post(() -> {
                    if (path.equals(into.getTag())) into.setImageBitmap(b);
                });
            } catch (Exception e) {
                AppLog.w("poster: " + e.getMessage());
            }
        });
    }

    // MARK: - Small helpers for the look

    private int px(float v) {
        return Math.round(v * k);
    }

    private TextView text(String s, float size, Typeface face, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
        return t;
    }

    private GradientDrawable box(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(18));
        g.setColor(fill);
        if (stroke != 0) g.setStroke(px(3), stroke);
        return g;
    }

    /** A round-ended button the remote can focus: light when it has the focus. */
    private TextView pill(String label) {
        TextView t = text(label, 26, Ui.semibold, INK);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(40), 0, px(40), 0);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(box(0x24FFFFFF, 0));
        t.setOnFocusChangeListener((v, focus) -> {
            t.setBackground(box(focus ? 0xFFFFFFFF : 0x24FFFFFF, 0));
            t.setTextColor(focus ? 0xFF0B0F14 : INK);
        });
        return t;
    }

    private LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -1);
        lp.leftMargin = px(20);
        return lp;
    }
}
