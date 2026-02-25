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

public class SearchActivity extends AppCompatActivity {

    private SQLiteDatabase db;
    private EditText searchInput;
    private LinearLayout resultsContainer;
    private TextView resultCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        TextView title = new TextView(this);
        title.setText("NOT ARAMA");
        title.setTextColor(0xFFE94560);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 40, 24, 8);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Notlar, alıntılar ve yer imleri içinde ara");
        sub.setTextColor(0xFF888888);
        sub.setTextSize(12);
        sub.setPadding(24, 0, 24, 16);
        root.addView(sub);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(16, 0, 16, 16);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        searchInput = new EditText(this);
        searchInput.setHint("Kelime veya cümle...");
        searchInput.setTextColor(0xFFFFFFFF);
        searchInput.setHintTextColor(0xFF555566);
        searchInput.setBackgroundColor(0xFF16213E);
        searchInput.setPadding(16, 14, 16, 14);
        searchInput.setTextSize(15);
        searchInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        searchRow.addView(searchInput);

        Button btnSearch = new Button(this);
        btnSearch.setText("ARA");
        btnSearch.setBackgroundColor(0xFFE94560);
        btnSearch.setTextColor(0xFFFFFFFF);
        btnSearch.setTextSize(14);
        btnSearch.setTypeface(null, Typeface.BOLD);
        btnSearch.setPadding(24, 14, 24, 14);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(10, 0, 0, 0);
        btnSearch.setLayoutParams(blp);
        searchRow.addView(btnSearch);
        root.addView(searchRow);

        resultCount = new TextView(this);
        resultCount.setTextColor(0xFF888888);
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
        String q = "%" + query + "%";
        int total = 0;

        // 1. Notlar
        Cursor c1 = db.rawQuery(
            "SELECT h.note, h.tag, h.color, h.page, l.custom_name, h.pdf_uri " +
            "FROM highlights h LEFT JOIN library l ON h.pdf_uri=l.pdf_uri " +
            "WHERE h.note LIKE ? ORDER BY h.created_at DESC",
            new String[]{q});
        if (c1.getCount() > 0) {
            addHeader("NOTLAR (" + c1.getCount() + ")");
            while (c1.moveToNext()) {
                addCard(c1.getString(4), c1.getInt(3), c1.getString(0),
                    c1.getString(1), c1.getString(2), c1.getString(5), query);
                total++;
            }
        }
        c1.close();

        // 2. Arşiv
        Cursor c2 = db.rawQuery(
            "SELECT quote, topic, importance, page, book_name, pdf_uri " +
            "FROM archive WHERE quote LIKE ? OR topic LIKE ? ORDER BY created_at DESC",
            new String[]{q, q});
        if (c2.getCount() > 0) {
            addHeader("ARŞİV (" + c2.getCount() + ")");
            while (c2.moveToNext()) {
                addCard(c2.getString(4), c2.getInt(3), c2.getString(0),
                    c2.getString(1), "blue", c2.getString(5), query);
                total++;
            }
        }
        c2.close();

        // 3. Yer imleri
        Cursor c3 = db.rawQuery(
            "SELECT b.title, b.page, l.custom_name, b.pdf_uri " +
            "FROM bookmarks b LEFT JOIN library l ON b.pdf_uri=l.pdf_uri " +
            "WHERE b.title LIKE ? ORDER BY b.created_at DESC",
            new String[]{q});
        if (c3.getCount() > 0) {
            addHeader("YER İMLERİ (" + c3.getCount() + ")");
            while (c3.moveToNext()) {
                addCard(c3.getString(2), c3.getInt(1), c3.getString(0),
                    "Yer İmi", "yellow", c3.getString(3), query);
                total++;
            }
        }
        c3.close();

        if (total == 0) {
            TextView empty = new TextView(this);
            empty.setText("Sonuç bulunamadı: \"" + query + "\"");
            empty.setTextColor(0xFF555555);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(24, 48, 24, 24);
            resultsContainer.addView(empty);
        }
        resultCount.setText(total + " sonuç");
    }

    private void addHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFE94560);
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(4, 16, 4, 8);
        resultsContainer.addView(tv);
    }

    private void addCard(String book, int page, String content,
                         String tag, String color, String uri, String query) {
        int accent;
        switch (color != null ? color : "yellow") {
            case "red":   accent = 0xFFE94560; break;
            case "blue":  accent = 0xFF4488FF; break;
            case "green": accent = 0xFF44CC66; break;
            default:      accent = 0xFFFFD700; break;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFF16213E);
        card.setPadding(20, 14, 20, 14);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cp);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView src = new TextView(this);
        src.setText((book != null ? book : "?") + "  —  s." + (page + 1));
        src.setTextColor(accent);
        src.setTextSize(12);
        src.setTypeface(null, Typeface.BOLD);
        src.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        topRow.addView(src);

        if (tag != null && !tag.isEmpty()) {
            TextView tagView = new TextView(this);
            tagView.setText(" " + tag + " ");
            tagView.setTextColor(0xFFFFFFFF);
            tagView.setTextSize(9);
            tagView.setBackgroundColor(0xFF6A1B9A);
            tagView.setPadding(8, 3, 8, 3);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.setMargins(6, 0, 0, 0);
            tagView.setLayoutParams(tlp);
            topRow.addView(tagView);
        }

        Button btnGo = new Button(this);
        btnGo.setText("Git");
        btnGo.setBackgroundColor(accent);
        btnGo.setTextColor(0xFFFFFFFF);
        btnGo.setTextSize(10);
        btnGo.setPadding(14, 4, 14, 4);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.setMargins(8, 0, 0, 0);
        btnGo.setLayoutParams(glp);
        topRow.addView(btnGo);
        card.addView(topRow);

        TextView contentView = new TextView(this);
        contentView.setText(content != null ? content : "");
        contentView.setTextColor(0xFFCCCCCC);
        contentView.setTextSize(13);
        contentView.setPadding(0, 8, 0, 0);
        contentView.setLineSpacing(3, 1.2f);
        card.addView(contentView);
        resultsContainer.addView(card);

        final String fUri = uri;
        final int fPage = page;
        btnGo.setOnClickListener(v -> {
            if (fUri == null) {
                Toast.makeText(this, "Kaynak bulunamadı", Toast.LENGTH_SHORT).show();
                return;
            }
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
