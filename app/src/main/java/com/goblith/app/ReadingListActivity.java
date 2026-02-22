package com.goblith.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class ReadingListActivity extends AppCompatActivity {

    private SQLiteDatabase db;
    private LinearLayout listContainer;
    private String activeStatus = "okunacak";
    private Button btnOkunacak, btnOkunuyor, btnOkundu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        TextView title = new TextView(this);
        title.setText("OKUMA LİSTESİ");
        title.setTextColor(0xFFE94560);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 32, 24, 8);
        root.addView(title);

        // Sekme butonları
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(16, 0, 16, 16);

        btnOkunacak = makeTabBtn("📚 OKUNACAK", true);
        btnOkunuyor  = makeTabBtn("📖 OKUNUYOR",  false);
        btnOkundu   = makeTabBtn("✓ OKUNDU",   false);

        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.setMargins(0, 0, 6, 0);
        btnOkunacak.setLayoutParams(tp);
        LinearLayout.LayoutParams tp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp2.setMargins(0, 0, 6, 0);
        btnOkunuyor.setLayoutParams(tp2);
        btnOkundu.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        tabRow.addView(btnOkunacak);
        tabRow.addView(btnOkunuyor);
        tabRow.addView(btnOkundu);
        root.addView(tabRow);

        // Kütüphaneden ekle butonu
        Button btnAddFromLib = new Button(this);
        btnAddFromLib.setText("+ Kütüphaneden Ekle");
        btnAddFromLib.setBackgroundColor(0xFF0F3460);
        btnAddFromLib.setTextColor(0xFFFFFFFF);
        btnAddFromLib.setTextSize(13);
        btnAddFromLib.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams addP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addP.setMargins(16, 0, 16, 16);
        btnAddFromLib.setLayoutParams(addP);
        btnAddFromLib.setPadding(16, 12, 16, 12);
        root.addView(btnAddFromLib);

        ScrollView sv = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(16, 0, 16, 24);
        sv.addView(listContainer);
        root.addView(sv);

        setContentView(root);
        loadList("okunacak");

        btnOkunacak.setOnClickListener(v -> { activeStatus="okunacak"; updateTabs(); loadList("okunacak"); });
        btnOkunuyor.setOnClickListener(v -> { activeStatus="okunuyor"; updateTabs(); loadList("okunuyor"); });
        btnOkundu.setOnClickListener(v -> { activeStatus="okundu"; updateTabs(); loadList("okundu"); });

        btnAddFromLib.setOnClickListener(v -> showAddFromLibDialog());
    }

    private Button makeTabBtn(String text, boolean active) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(active ? 0xFFE94560 : 0xFF16213E);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(11);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setPadding(8, 10, 8, 10);
        return btn;
    }

    private void updateTabs() {
        btnOkunacak.setBackgroundColor(activeStatus.equals("okunacak") ? 0xFFE94560 : 0xFF16213E);
        btnOkunuyor.setBackgroundColor(activeStatus.equals("okunuyor")  ? 0xFFE94560 : 0xFF16213E);
        btnOkundu.setBackgroundColor(activeStatus.equals("okundu")   ? 0xFFE94560 : 0xFF16213E);
    }

    private void loadList(String status) {
        listContainer.removeAllViews();
        Cursor c = db.rawQuery(
            "SELECT r.id, r.pdf_uri, r.status, r.notes_text, r.added_date, l.custom_name, l.last_page " +
            "FROM reading_list r LEFT JOIN library l ON r.pdf_uri=l.pdf_uri " +
            "WHERE r.status=? ORDER BY r.added_date DESC",
            new String[]{status});

        if (c.getCount() == 0) {
            TextView empty = new TextView(this);
            String msg = status.equals("okunacak") ? "Okuma listeniz boş.\nKütüphaneden kitap ekleyin."
                : status.equals("okunuyor") ? "Şu an okuduğunuz kitap yok." : "Henüz tamamlanan kitap yok.";
            empty.setText(msg);
            empty.setTextColor(0xFF555555);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(24, 48, 24, 24);
            listContainer.addView(empty);
            c.close();
            return;
        }

        while (c.moveToNext()) {
            int id = c.getInt(0);
            String uri = c.getString(1);
            String st = c.getString(2);
            String notes = c.getString(3);
            String date = c.getString(4);
            String bookName = c.getString(5);
            int lastPage = c.getInt(6);
            if (bookName == null) bookName = "Bilinmeyen Kitap";

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, 10);
            card.setLayoutParams(cp);

            TextView nameView = new TextView(this);
            nameView.setText(bookName);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(15);
            nameView.setTypeface(null, Typeface.BOLD);
            card.addView(nameView);

            if (lastPage > 0) {
                TextView pageView = new TextView(this);
                pageView.setText("Son konum: Sayfa " + (lastPage+1));
                pageView.setTextColor(0xFF888888);
                pageView.setTextSize(12);
                pageView.setPadding(0, 4, 0, 0);
                card.addView(pageView);
            }

            if (notes != null && !notes.isEmpty()) {
                TextView notesView = new TextView(this);
                notesView.setText(notes);
                notesView.setTextColor(0xFF6688AA);
                notesView.setTextSize(13);
                notesView.setPadding(0, 6, 0, 0);
                card.addView(notesView);
            }

            // Durum değiştirme butonları
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, 10, 0, 0);

            if (!st.equals("okunuyor")) {
                Button btnReading = new Button(this);
                btnReading.setText("📖 Okuyorum");
                btnReading.setBackgroundColor(0xFF1565C0);
                btnReading.setTextColor(0xFFFFFFFF);
                btnReading.setTextSize(11);
                btnReading.setPadding(12, 6, 12, 6);
                btnReading.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                btnRow.addView(btnReading);
                final int fId = id;
                btnReading.setOnClickListener(v -> { updateStatus(fId, "okunuyor"); loadList(activeStatus); });
            }

            if (!st.equals("okundu")) {
                Button btnDone = new Button(this);
                btnDone.setText("✓ Tamamladım");
                btnDone.setBackgroundColor(0xFF2E7D32);
                btnDone.setTextColor(0xFFFFFFFF);
                btnDone.setTextSize(11);
                btnDone.setPadding(12, 6, 12, 6);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dp.setMargins(8, 0, 0, 0);
                btnDone.setLayoutParams(dp);
                btnRow.addView(btnDone);
                final int fId = id;
                btnDone.setOnClickListener(v -> { updateStatus(fId, "okundu"); loadList(activeStatus); });
            }

            Button btnRemove = new Button(this);
            btnRemove.setText("✕");
            btnRemove.setBackgroundColor(0xFF333333);
            btnRemove.setTextColor(0xFF888888);
            btnRemove.setTextSize(11);
            btnRemove.setPadding(12, 6, 12, 6);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(8, 0, 0, 0);
            btnRemove.setLayoutParams(rp);
            btnRow.addView(btnRemove);
            final int fId = id;
            btnRemove.setOnClickListener(v -> {
                db.delete("reading_list", "id=?", new String[]{String.valueOf(fId)});
                loadList(activeStatus);
            });

            card.addView(btnRow);
            listContainer.addView(card);
        }
        c.close();
    }

    private void updateStatus(int id, String status) {
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        db.update("reading_list", cv, "id=?", new String[]{String.valueOf(id)});
        Toast.makeText(this, "Güncellendi", Toast.LENGTH_SHORT).show();
    }

    private void showAddFromLibDialog() {
        Cursor c = db.rawQuery("SELECT pdf_uri, custom_name FROM library ORDER BY last_opened DESC", null);
        if (!c.moveToFirst()) {
            Toast.makeText(this, "Kütüphanede kitap yok", Toast.LENGTH_SHORT).show();
            c.close(); return;
        }

        List<String> uris = new ArrayList<>();
        List<String> names = new ArrayList<>();
        do {
            uris.add(c.getString(0));
            String name = c.getString(1);
            names.add(name != null ? name : "Bilinmeyen");
        } while (c.moveToNext());
        c.close();

        String[] nameArr = names.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Kitap Seç")
            .setItems(nameArr, (d, which) -> {
                // Zaten listede mi?
                Cursor ex = db.rawQuery("SELECT id FROM reading_list WHERE pdf_uri=?", new String[]{uris.get(which)});
                boolean exists = ex.moveToFirst();
                ex.close();
                if (exists) {
                    Toast.makeText(this, "Bu kitap zaten listede", Toast.LENGTH_SHORT).show();
                    return;
                }
                ContentValues cv = new ContentValues();
                cv.put("pdf_uri", uris.get(which));
                cv.put("status", "okunacak");
                cv.put("added_date", new java.util.Date().toString());
                db.insert("reading_list", null, cv);
                Toast.makeText(this, names.get(which) + " listeye eklendi", Toast.LENGTH_SHORT).show();
                loadList(activeStatus);
            })
            .setNegativeButton("İptal", null)
            .show();
    }

    // ArrayList import için

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(ReadingListActivity.this, "goblith.db", null, 6); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try { db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception e) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)"); } catch (Exception e) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)"); } catch (Exception e) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)"); } catch (Exception e) {}
        }
    }
}
