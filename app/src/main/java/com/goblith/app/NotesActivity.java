package com.goblith.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class NotesActivity extends AppCompatActivity {

    private LinearLayout notesContainer;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DBHelper dbHelper = new DBHelper();
        db = dbHelper.getReadableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Başlık
        TextView title = new TextView(this);
        title.setText("📚 Alıntı Bankası");
        title.setTextColor(0xFFE94560);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(24, 32, 24, 8);
        root.addView(title);

        // Arama kutusu
        EditText searchBox = new EditText(this);
        searchBox.setHint("Notlarda ara...");
        searchBox.setHintTextColor(0xFF888888);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setBackgroundColor(0xFF0F3460);
        searchBox.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(16, 8, 16, 16);
        searchBox.setLayoutParams(searchParams);
        root.addView(searchBox);

        // Notlar listesi
        ScrollView scrollView = new ScrollView(this);
        notesContainer = new LinearLayout(this);
        notesContainer.setOrientation(LinearLayout.VERTICAL);
        notesContainer.setPadding(16, 0, 16, 16);
        scrollView.addView(notesContainer);
        root.addView(scrollView);

        setContentView(root);
        loadNotes("");

        // Arama
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                loadNotes(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadNotes(String query) {
        notesContainer.removeAllViews();

        Cursor cursor;
        if (query.isEmpty()) {
            cursor = db.rawQuery("SELECT * FROM highlights ORDER BY created_at DESC", null);
        } else {
            cursor = db.rawQuery("SELECT * FROM highlights WHERE note LIKE ? ORDER BY created_at DESC",
                new String[]{"%" + query + "%"});
        }

        if (cursor.getCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Henüz not yok.\nPDF okurken 🔴🔵🟢 butonlarıyla not ekle.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(32, 64, 32, 32);
            notesContainer.addView(empty);
            cursor.close();
            return;
        }

        while (cursor.moveToNext()) {
            int page = cursor.getInt(cursor.getColumnIndexOrThrow("page"));
            String color = cursor.getString(cursor.getColumnIndexOrThrow("color"));
            String note = cursor.getString(cursor.getColumnIndexOrThrow("note"));
            String date = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));

            // Kart
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 12);
            card.setLayoutParams(cardParams);

            // Renk etiketi
            String emoji, label;
            int borderColor;
            switch (color) {
                case "red":   emoji = "🔴"; label = "İtiraz";   borderColor = 0xFFE94560; break;
                case "blue":  emoji = "🔵"; label = "Argüman";  borderColor = 0xFF1565C0; break;
                default:      emoji = "🟢"; label = "Veri";     borderColor = 0xFF2E7D32; break;
            }

            TextView labelView = new TextView(this);
            labelView.setText(emoji + " " + label + "  •  Sayfa " + (page + 1));
            labelView.setTextColor(borderColor);
            labelView.setTextSize(12);
            labelView.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(labelView);

            if (note != null && !note.isEmpty()) {
                TextView noteView = new TextView(this);
                noteView.setText(note);
                noteView.setTextColor(0xFFEEEEEE);
                noteView.setTextSize(15);
                noteView.setPadding(0, 8, 0, 4);
                card.addView(noteView);
            }

            TextView dateView = new TextView(this);
            dateView.setText(date != null ? date.substring(0, 10) : "");
            dateView.setTextColor(0xFF555555);
            dateView.setTextSize(11);
            card.addView(dateView);

            notesContainer.addView(card);
        }
        cursor.close();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() {
            super(NotesActivity.this, "goblith.db", null, 1);
        }
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }
}
