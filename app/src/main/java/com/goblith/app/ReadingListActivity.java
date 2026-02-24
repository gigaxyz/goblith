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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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
        title.setText("OKUMA LISTESI");
        title.setTextColor(0xFFE94560);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 32, 24, 16);
        root.addView(title);

        // Sekmeler
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(16, 0, 16, 16);

        btnOkunacak = makeTabBtn("OKUNACAK");
        btnOkunuyor  = makeTabBtn("OKUNUYOR");
        btnOkundu   = makeTabBtn("OKUNDU");

        tabRow.addView(withWeight(btnOkunacak, 6));
        tabRow.addView(withWeight(btnOkunuyor, 6));
        tabRow.addView(withWeight(btnOkundu, 0));
        root.addView(tabRow);

        // Kütüphaneden ekle
        Button btnAdd = new Button(this);
        btnAdd.setText("+ Kutuphaneden Ekle");
        btnAdd.setBackgroundColor(0xFF0F3460);
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setTextSize(13);
        btnAdd.setTypeface(null, Typeface.BOLD);
        btnAdd.setPadding(16, 14, 16, 14);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(16, 0, 16, 16);
        btnAdd.setLayoutParams(ap);
        root.addView(btnAdd);

        ScrollView sv = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(16, 0, 16, 24);
        sv.addView(listContainer);
        root.addView(sv);

        setContentView(root);
        updateTabs();
        loadList("okunacak");

        btnOkunacak.setOnClickListener(v -> { activeStatus="okunacak"; updateTabs(); loadList(activeStatus); });
        btnOkunuyor.setOnClickListener(v -> { activeStatus="okunuyor";  updateTabs(); loadList(activeStatus); });
        btnOkundu.setOnClickListener(v  -> { activeStatus="okundu";   updateTabs(); loadList(activeStatus); });
        btnAdd.setOnClickListener(v -> showAddDialog());
    }

    private Button makeTabBtn(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(11);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setPadding(8, 10, 8, 10);
        btn.setBackgroundColor(0xFF16213E);
        return btn;
    }

    private Button withWeight(Button btn, int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(leftMargin, 0, 0, 0);
        btn.setLayoutParams(lp);
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
            "SELECT r.id, r.pdf_uri, r.status, r.added_date, l.custom_name, l.last_page " +
            "FROM reading_list r LEFT JOIN library l ON r.pdf_uri=l.pdf_uri " +
            "WHERE r.status=? ORDER BY r.added_date DESC",
            new String[]{status});

        if (c.getCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText(status.equals("okunacak") ? "Okuma listeniz bos.\nKutuphaneden ekleyin."
                : status.equals("okunuyor") ? "Su an okunan kitap yok."
                : "Henuz tamamlanan kitap yok.");
            empty.setTextColor(0xFF555555);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(24, 48, 24, 24);
            listContainer.addView(empty);
            c.close();
            return;
        }

        while (c.moveToNext()) {
            int    id       = c.getInt(0);
            String uri      = c.getString(1);
            String st       = c.getString(2);
            String date     = c.getString(3);
            String bookName = c.getString(4);
            int    lastPage = c.getInt(5);
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
                TextView pv = new TextView(this);
                pv.setText("Son konum: Sayfa " + (lastPage+1));
                pv.setTextColor(0xFF888888);
                pv.setTextSize(12);
                pv.setPadding(0, 4, 0, 0);
                card.addView(pv);
            }

            if (date != null && date.length() >= 10) {
                TextView dv = new TextView(this);
                dv.setText("Eklendi: " + date.substring(0, 10));
                dv.setTextColor(0xFF555566);
                dv.setTextSize(11);
                dv.setPadding(0, 4, 0, 0);
                card.addView(dv);
            }

            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, 10, 0, 0);

            if (!st.equals("okunuyor")) {
                Button b = makeSmallBtn("Okuyorum", 0xFF1565C0);
                final int fId = id;
                b.setOnClickListener(v -> { updateStatus(fId, "okunuyor"); loadList(activeStatus); });
                btnRow.addView(b);
            }
            if (!st.equals("okundu")) {
                Button b = makeSmallBtn("Tamamladim", 0xFF2E7D32);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bp.setMargins(8, 0, 0, 0);
                b.setLayoutParams(bp);
                final int fId = id;
                b.setOnClickListener(v -> { updateStatus(fId, "okundu"); loadList(activeStatus); });
                btnRow.addView(b);
            }

            Button bDel = makeSmallBtn("Kaldir", 0xFF333333);
            LinearLayout.LayoutParams delP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            delP.setMargins(8, 0, 0, 0);
            bDel.setLayoutParams(delP);
            final int fId = id;
            bDel.setOnClickListener(v -> {
                db.delete("reading_list", "id=?", new String[]{String.valueOf(fId)});
                loadList(activeStatus);
            });
            btnRow.addView(bDel);
            card.addView(btnRow);
            listContainer.addView(card);
        }
        c.close();
    }

    private Button makeSmallBtn(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(11);
        btn.setPadding(14, 6, 14, 6);
        return btn;
    }

    private void updateStatus(int id, String status) {
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        db.update("reading_list", cv, "id=?", new String[]{String.valueOf(id)});
        Toast.makeText(this, "Guncellendi", Toast.LENGTH_SHORT).show();
    }

    private void showAddDialog() {
        Cursor c = db.rawQuery(
            "SELECT l.pdf_uri, l.custom_name FROM library l " +
            "WHERE l.pdf_uri NOT IN (SELECT pdf_uri FROM reading_list) " +
            "ORDER BY l.last_opened DESC", null);
        if (!c.moveToFirst()) {
            Toast.makeText(this, "Tum kitaplar zaten listede", Toast.LENGTH_SHORT).show();
            c.close(); return;
        }
        List<String> uris  = new ArrayList<>();
        List<String> names = new ArrayList<>();
        do {
            uris.add(c.getString(0));
            String n = c.getString(1);
            names.add(n != null ? n : "Bilinmeyen");
        } while (c.moveToNext());
        c.close();

        new AlertDialog.Builder(this)
            .setTitle("Kitap Sec")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                ContentValues cv = new ContentValues();
                cv.put("pdf_uri",    uris.get(which));
                cv.put("status",     "okunacak");
                cv.put("added_date", new java.util.Date().toString());
                db.insert("reading_list", null, cv);
                Toast.makeText(this, names.get(which) + " eklendi", Toast.LENGTH_SHORT).show();
                loadList(activeStatus);
            })
            .setNegativeButton("Iptal", null)
            .show();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(ReadingListActivity.this, "goblith.db", null, 8); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try{db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, created_at TEXT)");}catch(Exception e){}
            try { db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception ignored) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)"); } catch (Exception ignored) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)"); } catch (Exception ignored) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)"); } catch (Exception ignored) {}
        }
    }
}
