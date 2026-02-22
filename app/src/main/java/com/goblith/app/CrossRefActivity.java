package com.goblith.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrossRefActivity extends AppCompatActivity {

    private LinearLayout resultsContainer;
    private SQLiteDatabase db;
    private ProgressBar progressBar;
    private EditText searchInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        TextView title = new TextView(this);
        title.setText("CAPRAZ KITAP BAGLANTISI");
        title.setTextColor(0xFFE94560);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 32, 24, 4);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Bir kavram hangi kitaplarda geciyor?");
        subtitle.setTextColor(0xFF888888);
        subtitle.setTextSize(13);
        subtitle.setPadding(24, 0, 24, 24);
        root.addView(subtitle);

        // Arama kutusu
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(16, 0, 16, 16);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        searchInput = new EditText(this);
        searchInput.setHint("Kavram ara... (ornek: devlet, us, ozgurluk)");
        searchInput.setHintTextColor(0xFF888888);
        searchInput.setTextColor(0xFFFFFFFF);
        searchInput.setBackgroundColor(0xFF0F3460);
        searchInput.setPadding(20, 14, 20, 14);
        searchInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        searchRow.addView(searchInput);

        Button btnSearch = new Button(this);
        btnSearch.setText("ARA");
        btnSearch.setBackgroundColor(0xFFE94560);
        btnSearch.setTextColor(0xFFFFFFFF);
        btnSearch.setTypeface(null, Typeface.BOLD);
        btnSearch.setPadding(24, 14, 24, 14);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(8, 0, 0, 0);
        btnSearch.setLayoutParams(bp);
        searchRow.addView(btnSearch);
        root.addView(searchRow);

        // Önerilen kavramlar
        TextView suggestLabel = new TextView(this);
        suggestLabel.setText("NOTLARINIZDAN KAVRAMLAR:");
        suggestLabel.setTextColor(0xFF888888);
        suggestLabel.setTextSize(11);
        suggestLabel.setTypeface(null, Typeface.BOLD);
        suggestLabel.setPadding(24, 0, 24, 8);
        root.addView(suggestLabel);

        ScrollView suggestScroll = new ScrollView(this);
        suggestScroll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 120));
        LinearLayout suggestContainer = new LinearLayout(this);
        suggestContainer.setOrientation(LinearLayout.VERTICAL);
        suggestContainer.setPadding(16, 0, 16, 8);
        suggestScroll.addView(suggestContainer);
        root.addView(suggestScroll);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams pbP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbP.setMargins(16, 0, 16, 8);
        progressBar.setLayoutParams(pbP);
        root.addView(progressBar);

        // Sonuçlar
        ScrollView scrollView = new ScrollView(this);
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(16, 8, 16, 24);
        scrollView.addView(resultsContainer);
        root.addView(scrollView);

        setContentView(root);

        // Önerilen kavramları yükle
        loadSuggestedConcepts(suggestContainer);

        btnSearch.setOnClickListener(v -> {
            String query = searchInput.getText().toString().trim();
            if (query.length() < 2) return;
            searchCrossRef(query);
        });
    }

    private void loadSuggestedConcepts(LinearLayout container) {
        // Notlardan en çok geçen kelimeleri çek
        Map<String, Integer> wordCount = new HashMap<>();
        Cursor cursor = db.rawQuery("SELECT note FROM highlights WHERE note IS NOT NULL AND note != ''", null);
        while (cursor.moveToNext()) {
            String note = cursor.getString(0);
            String[] words = note.toLowerCase().replaceAll("[^a-zA-ZğüşıöçĞÜŞİÖÇ ]", " ").split("\\s+");
            for (String w : words) {
                if (w.length() >= 4) wordCount.put(w, wordCount.getOrDefault(w, 0) + 1);
            }
        }
        cursor.close();

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(wordCount.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        container.addView(row);

        int count = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            if (count >= 12) break;
            if (count % 6 == 0 && count > 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                container.addView(row);
            }
            Button chip = new Button(this);
            chip.setText(entry.getKey());
            chip.setTextSize(11);
            chip.setTextColor(0xFFFFFFFF);
            chip.setBackgroundColor(0xFF0F3460);
            chip.setPadding(16, 6, 16, 6);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(4, 4, 4, 4);
            chip.setLayoutParams(cp);
            chip.setOnClickListener(v -> {
                searchInput.setText(entry.getKey());
                searchCrossRef(entry.getKey());
            });
            row.addView(chip);
            count++;
        }

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("Henuz not yok. Once kitap okuyup not ekleyin.");
            empty.setTextColor(0xFF555555);
            empty.setTextSize(13);
            empty.setPadding(8, 8, 8, 8);
            container.addView(empty);
        }
    }

    private void searchCrossRef(String query) {
        resultsContainer.removeAllViews();
        progressBar.setVisibility(android.view.View.VISIBLE);

        new Thread(() -> {
            // Notlarda ara
            Map<String, List<String>> bookMatches = new HashMap<>();
            Cursor cursor = db.rawQuery(
                "SELECT h.note, h.page, h.color, h.tag, l.custom_name " +
                "FROM highlights h LEFT JOIN library l ON h.pdf_uri=l.pdf_uri " +
                "WHERE h.note LIKE ? ORDER BY l.custom_name",
                new String[]{"%" + query + "%"});

            while (cursor.moveToNext()) {
                String note = cursor.getString(0);
                int page = cursor.getInt(1);
                String color = cursor.getString(2);
                String tag = cursor.getString(3);
                String bookName = cursor.getString(4);
                if (bookName == null) bookName = "Bilinmeyen Kitap";

                String entry = "Sayfa " + (page+1) + ": " + (note != null ? note : "") +
                    (tag != null && !tag.isEmpty() ? " [" + tag + "]" : "");

                if (!bookMatches.containsKey(bookName)) bookMatches.put(bookName, new ArrayList<>());
                bookMatches.get(bookName).add(entry);
            }
            cursor.close();

            final Map<String, List<String>> results = bookMatches;
            final String q = query;

            new Handler(Looper.getMainLooper()).post(() -> {
                progressBar.setVisibility(android.view.View.GONE);
                showCrossRefResults(results, q);
            });
        }).start();
    }

    private void showCrossRefResults(Map<String, List<String>> results, String query) {
        resultsContainer.removeAllViews();

        if (results.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("\"" + query + "\" icin hicbir notunuzda eslesen bulunamadi.\n\nNotlarinda bu kavram gectigi zaman burada gorunur.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(24, 48, 24, 24);
            resultsContainer.addView(empty);
            return;
        }

        // Özet başlık
        TextView header = new TextView(this);
        header.setText("\"" + query.toUpperCase() + "\"  " + results.size() + " KITAPTA BULUNDU");
        header.setTextColor(0xFFE94560);
        header.setTextSize(14);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(8, 8, 8, 16);
        resultsContainer.addView(header);

        for (Map.Entry<String, List<String>> entry : results.entrySet()) {
            String bookName = entry.getKey();
            List<String> matches = entry.getValue();

            // Kitap kartı
            LinearLayout bookCard = new LinearLayout(this);
            bookCard.setOrientation(LinearLayout.VERTICAL);
            bookCard.setBackgroundColor(0xFF16213E);
            bookCard.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams bcp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bcp.setMargins(0, 0, 0, 12);
            bookCard.setLayoutParams(bcp);

            // Kitap adı
            LinearLayout bookHeader = new LinearLayout(this);
            bookHeader.setOrientation(LinearLayout.HORIZONTAL);
            bookHeader.setGravity(Gravity.CENTER_VERTICAL);

            TextView bookNameView = new TextView(this);
            bookNameView.setText(bookName);
            bookNameView.setTextColor(0xFFFFFFFF);
            bookNameView.setTextSize(15);
            bookNameView.setTypeface(null, Typeface.BOLD);
            bookNameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            bookHeader.addView(bookNameView);

            TextView countBadge = new TextView(this);
            countBadge.setText(" " + matches.size() + " eslesme ");
            countBadge.setTextColor(0xFFFFFFFF);
            countBadge.setTextSize(11);
            countBadge.setBackgroundColor(0xFFE94560);
            countBadge.setPadding(8, 4, 8, 4);
            bookHeader.addView(countBadge);

            bookCard.addView(bookHeader);

            // Eşleşmeler
            for (String match : matches) {
                LinearLayout matchRow = new LinearLayout(this);
                matchRow.setOrientation(LinearLayout.HORIZONTAL);
                matchRow.setBackgroundColor(0xFF0F1F3D);
                matchRow.setPadding(12, 10, 12, 10);
                LinearLayout.LayoutParams mrp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                mrp.setMargins(0, 8, 0, 0);
                matchRow.setLayoutParams(mrp);

                TextView bullet = new TextView(this);
                bullet.setText("▸  ");
                bullet.setTextColor(0xFFE94560);
                bullet.setTextSize(14);
                matchRow.addView(bullet);

                TextView matchText = new TextView(this);
                matchText.setText(match);
                matchText.setTextColor(0xFFCCCCCC);
                matchText.setTextSize(13);
                matchText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                matchRow.addView(matchText);

                bookCard.addView(matchRow);
            }

            resultsContainer.addView(bookCard);
        }
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(CrossRefActivity.this, "goblith.db", null, 6); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try { db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception e) {}
        }
    }
}
