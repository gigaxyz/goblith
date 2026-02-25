package com.goblith.app;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchActivity extends AppCompatActivity {

    private SQLiteDatabase db;
    private EditText searchInput;
    private LinearLayout resultsContainer;
    private TextView resultCount;

    static class Result {
        String book, content, tag, color, uri;
        int page, score;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0E17);

        TextView title = new TextView(this);
        title.setText("ARAMA");
        title.setTextColor(0xFFE2D9F3);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 40, 24, 4);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Notlar, alıntılar ve yer imleri içinde akıllı arama");
        sub.setTextColor(0xFF6B6A8A);
        sub.setTextSize(12);
        sub.setPadding(24, 0, 24, 16);
        root.addView(sub);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(16, 0, 16, 12);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        searchInput = new EditText(this);
        searchInput.setHint("Kelime veya cümle parçası...");
        searchInput.setTextColor(0xFFE2D9F3);
        searchInput.setHintTextColor(0xFF444455);
        searchInput.setBackgroundColor(0xFF1A1831);
        searchInput.setPadding(16, 14, 16, 14);
        searchInput.setTextSize(15);
        searchInput.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        searchRow.addView(searchInput);

        Button btnSearch = new Button(this);
        btnSearch.setText("ARA");
        btnSearch.setBackgroundColor(0xFF6D28D9);
        btnSearch.setTextColor(0xFFFFFFFF);
        btnSearch.setTextSize(13);
        btnSearch.setTypeface(null, Typeface.BOLD);
        btnSearch.setPadding(24, 14, 24, 14);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(10, 0, 0, 0);
        btnSearch.setLayoutParams(blp);
        searchRow.addView(btnSearch);
        root.addView(searchRow);

        resultCount = new TextView(this);
        resultCount.setTextColor(0xFF6B6A8A);
        resultCount.setTextSize(12);
        resultCount.setPadding(24, 0, 24, 8);
        root.addView(resultCount);

        ScrollView sv = new ScrollView(this);
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(16, 8, 16, 32);
        sv.addView(resultsContainer);
        root.addView(sv);
        setContentView(root);

        btnSearch.setOnClickListener(v -> {
            String q = searchInput.getText().toString().trim();
            if (q.isEmpty()) { Toast.makeText(this, "Kelime gir", Toast.LENGTH_SHORT).show(); return; }
            doSearch(q);
        });
    }

    private void doSearch(String query) {
        resultsContainer.removeAllViews();
        String[] words = query.toLowerCase().split("\\s+");
        Map<String, Result> resultMap = new LinkedHashMap<>();

        for (String word : words) {
            if (word.length() < 2) continue;
            String w = "%" + word + "%";

            // Notlar
            Cursor c1 = db.rawQuery(
                "SELECT h.note, h.tag, h.color, h.page, l.custom_name, h.pdf_uri, h.id " +
                "FROM highlights h LEFT JOIN library l ON h.pdf_uri=l.pdf_uri " +
                "WHERE h.note LIKE ?", new String[]{w});
            while (c1.moveToNext()) {
                String key = "h_" + c1.getLong(6);
                Result r = resultMap.containsKey(key) ? resultMap.get(key) : new Result();
                r.content = c1.getString(0); r.tag = c1.getString(1);
                r.color = c1.getString(2); r.page = c1.getInt(3);
                r.book = c1.getString(4); r.uri = c1.getString(5);
                r.score++; resultMap.put(key, r);
            }
            c1.close();

            // Arşiv
            Cursor c2 = db.rawQuery(
                "SELECT quote, topic, page, book_name, pdf_uri, id FROM archive WHERE quote LIKE ? OR topic LIKE ?",
                new String[]{w, w});
            while (c2.moveToNext()) {
                String key = "a_" + c2.getLong(5);
                Result r = resultMap.containsKey(key) ? resultMap.get(key) : new Result();
                r.content = c2.getString(0); r.tag = c2.getString(1);
                r.color = "blue"; r.page = c2.getInt(2);
                r.book = c2.getString(3); r.uri = c2.getString(4);
                r.score++; resultMap.put(key, r);
            }
            c2.close();

            // Yer imleri
            Cursor c3 = db.rawQuery(
                "SELECT b.title, b.page, l.custom_name, b.pdf_uri, b.id " +
                "FROM bookmarks b LEFT JOIN library l ON b.pdf_uri=l.pdf_uri WHERE b.title LIKE ?",
                new String[]{w});
            while (c3.moveToNext()) {
                String key = "b_" + c3.getLong(4);
                Result r = resultMap.containsKey(key) ? resultMap.get(key) : new Result();
                r.content = c3.getString(0); r.tag = "Yer İmi";
                r.color = "yellow"; r.page = c3.getInt(1);
                r.book = c3.getString(2); r.uri = c3.getString(3);
                r.score++; resultMap.put(key, r);
            }
            c3.close();
        }

        List<Result> sorted = new ArrayList<>(resultMap.values());
        java.util.Collections.sort(sorted, (a, b) -> b.score - a.score);

        if (sorted.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Sonuç bulunamadı: \"" + query + "\"");
            empty.setTextColor(0xFF444455); empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER); empty.setPadding(24, 48, 24, 24);
            resultsContainer.addView(empty);
            resultCount.setText("0 sonuç");
            return;
        }

        resultCount.setText(sorted.size() + " sonuç");
        for (Result r : sorted) addCard(r, words);
    }

    private void addCard(Result r, String[] words) {
        int accent;
        switch (r.color != null ? r.color : "yellow") {
            case "red":   accent = 0xFFBB3355; break;
            case "blue":  accent = 0xFF3366CC; break;
            case "green": accent = 0xFF338855; break;
            default:      accent = 0xFF7C3AED; break;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFF1A1831);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cp);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);

        android.view.View stripe = new android.view.View(this);
        stripe.setBackgroundColor(accent);
        stripe.setLayoutParams(new LinearLayout.LayoutParams(5, ViewGroup.LayoutParams.MATCH_PARENT));
        inner.addView(stripe);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(16, 12, 16, 12);
        contentLayout.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        // Üst satır
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView src = new TextView(this);
        src.setText((r.book != null ? r.book : "?") + "  —  s." + (r.page + 1));
        src.setTextColor(0xFF9B8EC4); src.setTextSize(11);
        src.setTypeface(null, Typeface.BOLD);
        src.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        topRow.addView(src);

        TextView scoreView = new TextView(this);
        scoreView.setText(r.score + " eşleşme");
        scoreView.setTextColor(r.score >= 3 ? 0xFF44CC66 : r.score >= 2 ? 0xFFFFD700 : 0xFF888888);
        scoreView.setTextSize(10);
        scoreView.setPadding(8, 0, 8, 0);
        topRow.addView(scoreView);

        Button btnGo = new Button(this);
        btnGo.setText("Git");
        btnGo.setBackgroundColor(accent);
        btnGo.setTextColor(0xFFFFFFFF);
        btnGo.setTextSize(10);
        btnGo.setPadding(14, 4, 14, 4);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.setMargins(4, 0, 0, 0);
        btnGo.setLayoutParams(glp);
        topRow.addView(btnGo);
        contentLayout.addView(topRow);

        // İçerik metni — eşleşen kelimeleri vurgula
        String display = r.content != null ? r.content : "";
        for (String word : words) {
            if (word.length() < 2) continue;
            String lower = display.toLowerCase();
            String wLower = word.toLowerCase();
            int idx = lower.indexOf(wLower);
            while (idx >= 0) {
                String match = display.substring(idx, idx + word.length());
                String replacement = "【" + match.toUpperCase() + "】";
                display = display.substring(0, idx) + replacement + display.substring(idx + word.length());
                lower = display.toLowerCase();
                idx = lower.indexOf(wLower, idx + replacement.length());
            }
        }
        TextView contentView = new TextView(this);
        contentView.setText(display);
        contentView.setTextColor(0xFFCCBBEE);
        contentView.setTextSize(13);
        contentView.setPadding(0, 8, 0, 4);
        contentView.setLineSpacing(3, 1.2f);
        contentLayout.addView(contentView);

        if (r.tag != null && !r.tag.isEmpty()) {
            TextView tagView = new TextView(this);
            tagView.setText(" " + r.tag + " ");
            tagView.setTextColor(0xFFE2D9F3); tagView.setTextSize(9);
            tagView.setBackgroundColor(0xFF4C1D95); tagView.setPadding(8, 3, 8, 3);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMargins(0, 6, 0, 0);
            tagView.setLayoutParams(tlp);
            contentLayout.addView(tagView);
        }

        inner.addView(contentLayout);
        card.addView(inner);
        resultsContainer.addView(card);

        final String fUri = r.uri;
        final int fPage = r.page;
        btnGo.setOnClickListener(v -> {
            if (fUri == null) { Toast.makeText(this, "Kaynak bulunamadı", Toast.LENGTH_SHORT).show(); return; }
            Intent i = new Intent(this, PdfViewerActivity.class);
            i.putExtra("pdfUri", fUri);
            i.putExtra("startPage", fPage);
            i.putExtra("fileType", "PDF");
            startActivity(i);
        });
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(SearchActivity.this, "goblith.db", null, 8); }
        @Override public void onCreate(SQLiteDatabase db) {}
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }
}
