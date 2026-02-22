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

        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        TextView title = new TextView(this);
        title.setText("ALINTI BANKASI");
        title.setTextColor(0xFFE94560);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(24, 32, 24, 8);
        root.addView(title);

        EditText searchBox = new EditText(this);
        searchBox.setHint("Notlarda ara...");
        searchBox.setHintTextColor(0xFF888888);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setBackgroundColor(0xFF0F3460);
        searchBox.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(16, 8, 16, 16);
        searchBox.setLayoutParams(sp);
        root.addView(searchBox);

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
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { loadNotes(s.toString()); }
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
            empty.setText("Henuz not yok.\nPDF okurken kategori butonlariyla not ekle.");
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

            String label;
            int borderColor;
            switch (color) {
                case "red":  label = "ITIRAZ";  borderColor = 0xFFE94560; break;
                case "blue": label = "ARGUMAN"; borderColor = 0xFF1565C0; break;
                default:     label = "VERI";    borderColor = 0xFF2E7D32; break;
            }

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, 12);
            card.setLayoutParams(cp);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView labelView = new TextView(this);
            labelView.setText(label + "  |  Sayfa " + (page + 1));
            labelView.setTextColor(borderColor);
            labelView.setTextSize(11);
            labelView.setTypeface(null, android.graphics.Typeface.BOLD);
            labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            topRow.addView(labelView);

            Button btnEdit = new Button(this);
            btnEdit.setText("Duzenle");
            btnEdit.setBackgroundColor(0xFF0F3460);
            btnEdit.setTextColor(0xFFFFFFFF);
            btnEdit.setTextSize(11);
            btnEdit.setPadding(16, 4, 16, 4);
            topRow.addView(btnEdit);

            Button btnDelete = new Button(this);
            btnDelete.setText("Sil");
            btnDelete.setBackgroundColor(0xFFE94560);
            btnDelete.setTextColor(0xFFFFFFFF);
            btnDelete.setTextSize(11);
            btnDelete.setPadding(16, 4, 16, 4);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dp.setMargins(8, 0, 0, 0);
            btnDelete.setLayoutParams(dp);
            topRow.addView(btnDelete);
            card.addView(topRow);

            if (note != null && !note.isEmpty()) {
                TextView noteView = new TextView(this);
                noteView.setText(note);
                noteView.setTextColor(0xFFEEEEEE);
                noteView.setTextSize(15);
                noteView.setPadding(0, 8, 0, 4);
                card.addView(noteView);
            }

            TextView dateView = new TextView(this);
            dateView.setText(date != null && date.length() >= 10 ? date.substring(0, 10) : "");
            dateView.setTextColor(0xFF555555);
            dateView.setTextSize(11);
            card.addView(dateView);

            notesContainer.addView(card);

            final int noteId = id;
            final String currentNote = note;
            final String currentColor = color;

            btnEdit.setOnClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Notu Duzenle");
                LinearLayout el = new LinearLayout(this);
                el.setOrientation(LinearLayout.VERTICAL);
                el.setPadding(32, 16, 32, 16);

                LinearLayout colorRow = new LinearLayout(this);
                colorRow.setOrientation(LinearLayout.HORIZONTAL);
                colorRow.setPadding(0, 8, 0, 16);
                final String[] sel = {currentColor};

                Button bRed = new Button(this);
                bRed.setText("ITIRAZ");
                bRed.setBackgroundColor(currentColor.equals("red") ? 0xFFE94560 : 0x44E94560);
                bRed.setTextColor(0xFFFFFFFF);
                bRed.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                Button bBlue = new Button(this);
                bBlue.setText("ARGUMAN");
                bBlue.setBackgroundColor(currentColor.equals("blue") ? 0xFF1565C0 : 0x441565C0);
                bBlue.setTextColor(0xFFFFFFFF);
                bBlue.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                Button bGreen = new Button(this);
                bGreen.setText("VERI");
                bGreen.setBackgroundColor(currentColor.equals("green") ? 0xFF2E7D32 : 0x442E7D32);
                bGreen.setTextColor(0xFFFFFFFF);
                bGreen.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                bRed.setOnClickListener(cv -> { sel[0]="red"; bRed.setBackgroundColor(0xFFE94560); bBlue.setBackgroundColor(0x441565C0); bGreen.setBackgroundColor(0x442E7D32); });
                bBlue.setOnClickListener(cv -> { sel[0]="blue"; bBlue.setBackgroundColor(0xFF1565C0); bRed.setBackgroundColor(0x44E94560); bGreen.setBackgroundColor(0x442E7D32); });
                bGreen.setOnClickListener(cv -> { sel[0]="green"; bGreen.setBackgroundColor(0xFF2E7D32); bRed.setBackgroundColor(0x44E94560); bBlue.setBackgroundColor(0x441565C0); });

                colorRow.addView(bRed);
                colorRow.addView(bBlue);
                colorRow.addView(bGreen);
                el.addView(colorRow);

                EditText editInput = new EditText(this);
                editInput.setText(currentNote);
                editInput.setTextColor(0xFF000000);
                editInput.setBackgroundColor(0xFFFFFFFF);
                editInput.setPadding(16, 12, 16, 12);
                editInput.setMinLines(3);
                el.addView(editInput);

                builder.setView(el);
                builder.setPositiveButton("Kaydet", (d, w) -> {
                    ContentValues values = new ContentValues();
                    values.put("note", editInput.getText().toString());
                    values.put("color", sel[0]);
                    db.update("highlights", values, "id=?", new String[]{String.valueOf(noteId)});
                    Toast.makeText(this, "Guncellendi", Toast.LENGTH_SHORT).show();
                    loadNotes("");
                });
                builder.setNegativeButton("Iptal", null);
                builder.show();
            });

            btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Notu Sil")
                .setMessage("Bu notu silmek istedigine emin misin?")
                .setPositiveButton("Sil", (d, w) -> {
                    db.delete("highlights", "id=?", new String[]{String.valueOf(noteId)});
                    Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();
                    loadNotes("");
                })
                .setNegativeButton("Iptal", null)
                .show());
        }
        cursor.close();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(NotesActivity.this, "goblith.db", null, 3); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception e) {}
        }
    }
}
