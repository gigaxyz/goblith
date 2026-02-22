package com.goblith.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class NotesActivity extends AppCompatActivity {

    private LinearLayout notesContainer;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DBHelper dbHelper = new DBHelper();
        db = dbHelper.getWritableDatabase();

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
            int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
            int page = cursor.getInt(cursor.getColumnIndexOrThrow("page"));
            String color = cursor.getString(cursor.getColumnIndexOrThrow("color"));
            String note = cursor.getString(cursor.getColumnIndexOrThrow("note"));
            String date = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));

            String emoji, label;
            int borderColor;
            switch (color) {
                case "red":  emoji = "🔴"; label = "İtiraz";  borderColor = 0xFFE94560; break;
                case "blue": emoji = "🔵"; label = "Argüman"; borderColor = 0xFF1565C0; break;
                default:     emoji = "🟢"; label = "Veri";    borderColor = 0xFF2E7D32; break;
            }

            // Kart
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 12);
            card.setLayoutParams(cardParams);

            // Üst satır: etiket + butonlar
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView labelView = new TextView(this);
            labelView.setText(emoji + " " + label + "  •  Sayfa " + (page + 1));
            labelView.setTextColor(borderColor);
            labelView.setTextSize(12);
            labelView.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            labelView.setLayoutParams(labelParams);
            topRow.addView(labelView);

            // Düzenle butonu
            Button btnEdit = new Button(this);
            btnEdit.setText("✏️");
            btnEdit.setBackgroundColor(0x00000000);
            btnEdit.setTextColor(0xFFAAAAAA);
            btnEdit.setTextSize(16);
            btnEdit.setPadding(8, 0, 8, 0);
            topRow.addView(btnEdit);

            // Sil butonu
            Button btnDelete = new Button(this);
            btnDelete.setText("🗑️");
            btnDelete.setBackgroundColor(0x00000000);
            btnDelete.setTextColor(0xFFAAAAAA);
            btnDelete.setTextSize(16);
            btnDelete.setPadding(8, 0, 8, 0);
            topRow.addView(btnDelete);

            card.addView(topRow);

            // Not metni
            if (note != null && !note.isEmpty()) {
                TextView noteView = new TextView(this);
                noteView.setText(note);
                noteView.setTextColor(0xFFEEEEEE);
                noteView.setTextSize(15);
                noteView.setPadding(0, 8, 0, 4);
                card.addView(noteView);
            }

            // Tarih
            TextView dateView = new TextView(this);
            dateView.setText(date != null ? date.substring(0, 10) : "");
            dateView.setTextColor(0xFF555555);
            dateView.setTextSize(11);
            card.addView(dateView);

            notesContainer.addView(card);

            // Düzenle
            final int noteId = id;
            final String currentNote = note;
            final String currentColor = color;
            btnEdit.setOnClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Notu Düzenle");

                LinearLayout editLayout = new LinearLayout(this);
                editLayout.setOrientation(LinearLayout.VERTICAL);
                editLayout.setPadding(32, 16, 32, 16);

                // Renk seçimi
                TextView colorLabel = new TextView(this);
                colorLabel.setText("Kategori:");
                colorLabel.setTextColor(0xFF888888);
                editLayout.addView(colorLabel);

                LinearLayout colorRow = new LinearLayout(this);
                colorRow.setOrientation(LinearLayout.HORIZONTAL);
                colorRow.setPadding(0, 8, 0, 16);
                final String[] selectedColor = {currentColor};

                Button bRed = new Button(this);
                bRed.setText("🔴 İtiraz");
                bRed.setBackgroundColor(currentColor.equals("red") ? 0xFFE94560 : 0x44E94560);
                bRed.setTextColor(0xFFFFFFFF);
                bRed.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                Button bBlue = new Button(this);
                bBlue.setText("🔵 Argüman");
                bBlue.setBackgroundColor(currentColor.equals("blue") ? 0xFF1565C0 : 0x441565C0);
                bBlue.setTextColor(0xFFFFFFFF);
                bBlue.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                Button bGreen = new Button(this);
                bGreen.setText("🟢 Veri");
                bGreen.setBackgroundColor(currentColor.equals("green") ? 0xFF2E7D32 : 0x442E7D32);
                bGreen.setTextColor(0xFFFFFFFF);
                bGreen.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                bRed.setOnClickListener(cv -> { selectedColor[0] = "red"; bRed.setBackgroundColor(0xFFE94560); bBlue.setBackgroundColor(0x441565C0); bGreen.setBackgroundColor(0x442E7D32); });
                bBlue.setOnClickListener(cv -> { selectedColor[0] = "blue"; bBlue.setBackgroundColor(0xFF1565C0); bRed.setBackgroundColor(0x44E94560); bGreen.setBackgroundColor(0x442E7D32); });
                bGreen.setOnClickListener(cv -> { selectedColor[0] = "green"; bGreen.setBackgroundColor(0xFF2E7D32); bRed.setBackgroundColor(0x44E94560); bBlue.setBackgroundColor(0x441565C0); });

                colorRow.addView(bRed);
                colorRow.addView(bBlue);
                colorRow.addView(bGreen);
                editLayout.addView(colorRow);

                // Not alanı
                EditText editInput = new EditText(this);
                editInput.setText(currentNote);
                editInput.setTextColor(0xFF000000);
                editInput.setBackgroundColor(0xFFFFFFFF);
                editInput.setPadding(16, 12, 16, 12);
                editInput.setMinLines(3);
                editLayout.addView(editInput);

                builder.setView(editLayout);
                builder.setPositiveButton("Kaydet", (d, w) -> {
                    ContentValues values = new ContentValues();
                    values.put("note", editInput.getText().toString());
                    values.put("color", selectedColor[0]);
                    db.update("highlights", values, "id=?", new String[]{String.valueOf(noteId)});
                    Toast.makeText(this, "Güncellendi ✓", Toast.LENGTH_SHORT).show();
                    loadNotes("");
                });
                builder.setNegativeButton("İptal", null);
                builder.show();
            });

            // Sil
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Notu Sil")
                    .setMessage("Bu notu silmek istediğine emin misin?")
                    .setPositiveButton("Sil", (d, w) -> {
                        db.delete("highlights", "id=?", new String[]{String.valueOf(noteId)});
                        Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();
                        loadNotes("");
                    })
                    .setNegativeButton("İptal", null)
                    .show();
            });
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
