package com.goblith.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class StatsActivity extends AppCompatActivity {

    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        TextView title = new TextView(this);
        title.setText("OKUMA ISTATISTIKLERI");
        title.setTextColor(0xFFE94560);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 32, 24, 8);
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(16, 16, 16, 32);
        sv.addView(content);
        root.addView(sv);

        setContentView(root);

        // Toplam kitap
        int totalBooks = 0;
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM library", null);
        if (c.moveToFirst()) totalBooks = c.getInt(0);
        c.close();

        // Toplam not
        int totalNotes = 0;
        c = db.rawQuery("SELECT COUNT(*) FROM highlights", null);
        if (c.moveToFirst()) totalNotes = c.getInt(0);
        c.close();

        // İtiraz / Argüman / Veri sayıları
        int redCount = 0, blueCount = 0, greenCount = 0;
        c = db.rawQuery("SELECT color, COUNT(*) as cnt FROM highlights GROUP BY color", null);
        while (c.moveToNext()) {
            String col = c.getString(0);
            int cnt = c.getInt(1);
            if ("red".equals(col)) redCount = cnt;
            else if ("blue".equals(col)) blueCount = cnt;
            else if ("green".equals(col)) greenCount = cnt;
        }
        c.close();

        // En çok okunan kitap
        String mostRead = "Yok";
        c = db.rawQuery("SELECT custom_name, last_page FROM library ORDER BY last_page DESC LIMIT 1", null);
        if (c.moveToFirst()) {
            String name = c.getString(0);
            int page = c.getInt(1);
            if (name != null) mostRead = name + " (" + (page+1) + ". sayfaya kadar)";
        }
        c.close();

        // En çok not alınan kitap
        String mostNoted = "Yok";
        c = db.rawQuery("SELECT h.pdf_uri, l.custom_name, COUNT(*) as cnt FROM highlights h LEFT JOIN library l ON h.pdf_uri=l.pdf_uri GROUP BY h.pdf_uri ORDER BY cnt DESC LIMIT 1", null);
        if (c.moveToFirst()) {
            String name = c.getString(1);
            int cnt = c.getInt(2);
            if (name != null) mostNoted = name + " (" + cnt + " not)";
        }
        c.close();

        // Kartları ekle
        addStatCard(content, "KUTUPHANENIZ", String.valueOf(totalBooks), "kitap", 0xFFE94560);
        addStatCard(content, "TOPLAM NOT", String.valueOf(totalNotes), "not alindi", 0xFF1565C0);

        // Kategori dağılımı
        addSectionTitle(content, "NOT KATEGORILERI");
        addCategoryBar(content, "ITIRAZ", redCount, totalNotes, 0xFFE94560);
        addCategoryBar(content, "ARGUMAN", blueCount, totalNotes, 0xFF1565C0);
        addCategoryBar(content, "VERI", greenCount, totalNotes, 0xFF2E7D32);

        addSectionTitle(content, "EN COK OKUNAN");
        addInfoCard(content, mostRead, 0xFF0F3460);

        addSectionTitle(content, "EN COK NOT ALINAN");
        addInfoCard(content, mostNoted, 0xFF0F3460);

        // Tüm kitapların özeti
        addSectionTitle(content, "KITAP BAZLI OZET");
        Cursor bookCursor = db.rawQuery(
            "SELECT l.custom_name, l.last_page, COUNT(h.id) as note_count " +
            "FROM library l LEFT JOIN highlights h ON l.pdf_uri=h.pdf_uri " +
            "GROUP BY l.pdf_uri ORDER BY l.last_opened DESC", null);

        while (bookCursor.moveToNext()) {
            String name = bookCursor.getString(0);
            int page = bookCursor.getInt(1);
            int notes = bookCursor.getInt(2);
            if (name == null) name = "Bilinmeyen";

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, 10);
            card.setLayoutParams(cp);

            TextView nameView = new TextView(this);
            nameView.setText(name);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(15);
            nameView.setTypeface(null, Typeface.BOLD);
            card.addView(nameView);

            TextView infoView = new TextView(this);
            infoView.setText((page+1) + ". sayfaya kadar  |  " + notes + " not");
            infoView.setTextColor(0xFF888888);
            infoView.setTextSize(12);
            infoView.setPadding(0, 6, 0, 0);
            card.addView(infoView);

            content.addView(card);
        }
        bookCursor.close();
    }

    private void addStatCard(LinearLayout parent, String label, String value, String unit, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundColor(0xFF16213E);
        card.setPadding(20, 20, 20, 20);
        card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(0xFF888888);
        labelView.setTextSize(11);
        labelView.setTypeface(null, Typeface.BOLD);
        left.addView(labelView);

        TextView unitView = new TextView(this);
        unitView.setText(unit);
        unitView.setTextColor(0xFF666666);
        unitView.setTextSize(12);
        left.addView(unitView);

        card.addView(left);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(color);
        valueView.setTextSize(36);
        valueView.setTypeface(null, Typeface.BOLD);
        card.addView(valueView);

        parent.addView(card);
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF888888);
        tv.setTextSize(11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(4, 24, 4, 8);
        parent.addView(tv);
    }

    private void addInfoCard(LinearLayout parent, String text, int bgColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(14);
        tv.setBackgroundColor(bgColor);
        tv.setPadding(20, 16, 20, 16);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, 8);
        tv.setLayoutParams(p);
        parent.addView(tv);
    }

    private void addCategoryBar(LinearLayout parent, String label, int count, int total, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(0xFF16213E);
        row.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, 0, 0, 8);
        row.setLayoutParams(rp);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(color);
        labelView.setTextSize(13);
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        topRow.addView(labelView);

        TextView countView = new TextView(this);
        countView.setText(count + " not");
        countView.setTextColor(0xFFAAAAAA);
        countView.setTextSize(12);
        topRow.addView(countView);

        row.addView(topRow);

        float ratio = total > 0 ? (float) count / total : 0;

        LinearLayout barBg = new LinearLayout(this);
        barBg.setBackgroundColor(0xFF0A1628);
        LinearLayout.LayoutParams bgP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 10);
        bgP.setMargins(0, 8, 0, 0);
        barBg.setLayoutParams(bgP);

        if (ratio > 0) {
            LinearLayout bar = new LinearLayout(this);
            bar.setBackgroundColor(color);
            bar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, ratio));
            barBg.addView(bar);
        }

        LinearLayout spacer = new LinearLayout(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1 - ratio));
        barBg.addView(spacer);

        row.addView(barBg);
        parent.addView(row);
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(StatsActivity.this, "goblith.db", null, 8); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try{db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, created_at TEXT)");}catch(Exception e){}
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception e) {}
        }
    }
}
