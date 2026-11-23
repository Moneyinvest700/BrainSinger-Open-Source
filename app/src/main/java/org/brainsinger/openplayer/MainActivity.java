package org.brainsinger.openplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent client for the existing Brain Singer public lecture feed. */
public final class MainActivity extends Activity {
    private static final String FEED_URL = "http://165.22.215.253:8090/userlectures";
    private static final int BG = Color.rgb(17, 22, 34);
    private static final int FG = Color.rgb(239, 243, 250);
    private static final int MUTED = Color.rgb(174, 188, 210);
    private static final int ACCENT = Color.rgb(84, 201, 196);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Lecture> all = new ArrayList<>();
    private final List<Lecture> visible = new ArrayList<>();
    private final Map<Integer, String> categoryNames = new HashMap<>();
    private final List<Integer> categoryIds = new ArrayList<>();
    private LinearLayout root;
    private ListView list;
    private TextView status;
    private Spinner category;
    private EditText search;
    private LectureAdapter adapter;
    private VideoView player;
    private boolean playerOpen;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        showCatalog();
        loadCatalog();
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        return t;
    }
    private LinearLayout column() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(BG);
        return box;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); return b;
    }
    private void showCatalog() {
        playerOpen = false;
        root = column(); root.setPadding(dp(16), dp(12), dp(16), dp(8));
        setContentView(root);
        TextView title = text("Brain Singer Open", 25, FG);
        root.addView(title);
        TextView intro = text("Lecture library • No login or subscription", 14, MUTED);
        intro.setPadding(0, dp(4), 0, dp(10)); root.addView(intro);
        search = new EditText(this);
        search.setSingleLine(true); search.setTextColor(FG); search.setHintTextColor(MUTED);
        search.setHint("Search lectures"); root.addView(search);
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        category = new Spinner(this);
        bar.addView(category, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button reload = button("Refresh");
        bar.addView(reload); root.addView(bar);
        status = text("Loading library…", 13, MUTED);
        status.setPadding(0, dp(4), 0, dp(8)); root.addView(status);
        list = new ListView(this); list.setDividerHeight(dp(1));
        adapter = new LectureAdapter(); list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            public void afterTextChanged(Editable e) { }
        });
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { applyFilter(); }
            public void onNothingSelected(AdapterView<?> parent) { applyFilter(); }
        });
        list.setOnItemClickListener((parent, view, position, id) -> play(visible.get(position)));
        reload.setOnClickListener(v -> loadCatalog());
        updateCategories(); applyFilter();
    }

    private void updateCategories() {
        List<String> names = new ArrayList<>(); names.add("All categories");
        for (Integer id : categoryIds) names.add(categoryNames.get(id));
        ArrayAdapter<String> choices = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names) {
            @Override public View getView(int p, View v, ViewGroup parent) {
                TextView t = (TextView) super.getView(p, v, parent); t.setTextColor(FG); return t;
            }
        };
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        category.setAdapter(choices);
    }

    private void applyFilter() {
        if (search == null || adapter == null || category == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int chosen = category.getSelectedItemPosition();
        Integer categoryId = chosen > 0 && chosen <= categoryIds.size() ? categoryIds.get(chosen - 1) : null;
        visible.clear();
        for (Lecture lecture : all) {
            if ((categoryId == null || lecture.category == categoryId) &&
                    lecture.title.toLowerCase(Locale.ROOT).contains(query)) visible.add(lecture);
        }
        adapter.notifyDataSetChanged();
        status.setText(all.isEmpty() ? "Library is empty or not loaded." : visible.size() + " lectures shown • Tap to play");
    }

    private void loadCatalog() {
        if (status != null) status.setText("Loading library…");
        io.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(FEED_URL).openConnection();
                conn.setConnectTimeout(15000); conn.setReadTimeout(20000);
                conn.setRequestProperty("Accept", "application/json");
                if (conn.getResponseCode() != 200) throw new Exception("Server returned HTTP " + conn.getResponseCode());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) != -1) {
                        if (bytes.size() + n > 2_000_000) throw new Exception("Feed is too large");
                        bytes.write(buf, 0, n);
                    }
                }
                JSONObject doc = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                JSONArray cats = doc.getJSONArray("categories"); JSONArray items = doc.getJSONArray("lectures");
                Map<Integer, String> incomingNames = new HashMap<>(); List<Integer> incomingIds = new ArrayList<>();
                for (int i = 0; i < cats.length(); i++) {
                    JSONObject c = cats.getJSONObject(i); int id = c.optInt("id", -1);
                    if (id >= 0) { incomingIds.add(id); incomingNames.put(id, c.optString("categname", "Category " + id)); }
                }
                List<Lecture> incoming = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String url = item.optString("link", "");
                    Uri uri = Uri.parse(url);
                    if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null) {
                        incoming.add(new Lecture(item.optInt("id"), item.optInt("category"),
                                item.optString("lecturetitle", "Untitled"), item.optString("length", ""), url));
                    }
                }
                ui.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    all.clear(); all.addAll(incoming); categoryNames.clear(); categoryNames.putAll(incomingNames);
                    categoryIds.clear(); categoryIds.addAll(incomingIds);
                    if (!playerOpen) { updateCategories(); applyFilter(); }
                });
            } catch (Exception e) {
                String message = e.getMessage();
                ui.post(() -> {
                    if (!isFinishing() && !isDestroyed() && !playerOpen && status != null)
                        status.setText("Could not load library. Check your connection and tap Refresh. " + (message == null ? "" : message));
                });
            } finally { if (conn != null) conn.disconnect(); }
        });
    }

    private void play(Lecture item) {
        playerOpen = true;
        LinearLayout screen = column(); screen.setPadding(dp(16), dp(12), dp(16), dp(12)); setContentView(screen);
        Button back = button("← Library"); back.setOnClickListener(v -> closePlayer()); screen.addView(back);
        TextView title = text(item.title, 21, FG); title.setPadding(0, dp(12), 0, dp(10)); screen.addView(title);
        player = new VideoView(this);
        screen.addView(player, new LinearLayout.LayoutParams(-1, dp(260)));
        ProgressBar loading = new ProgressBar(this); screen.addView(loading);
        TextView note = text("Loading media… Some older links may no longer be available.", 14, MUTED);
        note.setPadding(0, dp(12), 0, 0); screen.addView(note);
        MediaController controls = new MediaController(this); controls.setAnchorView(player); player.setMediaController(controls);
        player.setOnPreparedListener(mp -> { loading.setVisibility(View.GONE); note.setText("Use the player controls to pause or seek."); player.start(); controls.show(5000); });
        player.setOnErrorListener((mp, what, extra) -> {
            loading.setVisibility(View.GONE); note.setText("This media link could not be played. It may be unavailable on the server.");
            return true;
        });
        player.setVideoURI(Uri.parse(item.url)); player.requestFocus();
    }
    private void closePlayer() {
        if (player != null) { player.stopPlayback(); player = null; }
        showCatalog();
    }
    @Override public void onBackPressed() {
        if (playerOpen) closePlayer(); else super.onBackPressed();
    }
    @Override protected void onStop() {
        super.onStop();
        if (player != null && player.isPlaying()) player.pause();
    }
    @Override protected void onDestroy() {
        if (player != null) player.stopPlayback(); io.shutdownNow(); super.onDestroy();
    }
    private static final class Lecture {
        final int id, category; final String title, length, url;
        Lecture(int id, int category, String title, String length, String url) {
            this.id = id; this.category = category; this.title = title; this.length = length; this.url = url;
        }
    }
    private final class LectureAdapter extends android.widget.BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int p) { return visible.get(p); }
        @Override public long getItemId(int p) { return visible.get(p).id; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = column(); row.setPadding(dp(5), dp(13), dp(5), dp(13));
            Lecture l = visible.get(position);
            row.addView(text(l.title, 16, FG));
            String categoryName = categoryNames.containsKey(l.category) ? categoryNames.get(l.category) : "Lecture";
            TextView detail = text(categoryName + (l.length.isEmpty() ? "" : "  •  " + l.length), 12, MUTED);
            detail.setPadding(0, dp(5), 0, 0); row.addView(detail);
            return row;
        }
    }
}
