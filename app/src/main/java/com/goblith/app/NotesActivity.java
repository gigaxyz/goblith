package com.goblith.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Arrays;
import java.util.List;

public class NotesActivity extends AppCompatActivity {

    private LinearLayout notesContainer;
    private LinearLayout tagFilterRow;
    private SQLiteDatabase db;
    private String activeTag = null;

    private static final List<String> DEFAULT_TAGS = Arrays.asList(
        "Tumu", "Felsefe", "Ekonomi", "Strateji", "Bilim", "Tarih", "Psikoloji", "Diger"
    );

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
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 32, 24, 8);
        root.addView(title);

        // Arama
        EditText searchBox = new EditText(this);
        searchBox.setHint("Notlarda ara...");
        searchBox.setHintTextColor(0xFF888888);
        searchBox.setTextColor(0xFFFFFFFF);
        searchBox.setBackgroundColor(0xFF0F3460);
        searchBox.setPadding(24, 14, 24, 14);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(16, 0, 16, 12);
        searchBox.setLayoutParams(sp);
        root.addView(searchBox);

        // Etiket filtre çubuğu
        HorizontalScrollView tagScroll = new HorizontalScrollView(this);
        tagScroll.setBackgroundColor(0xFF0A1628);
        tagScroll.setPadding(8, 8, 8, 8);
        tagFilterRow = new LinearLayout(this);
        tagFilterRow.setOrientation(LinearLayout.HORIZONTAL);
        tagFilterRow.setPadding(8, 0, 8, 0);
        tagScroll.addView(tagFilterRow);
        root.addView(tagScroll);

        buildTagFilters();

        // Notlar
        ScrollView scrollView = new ScrollView(this);
        notesContainer = new LinearLayout(this);
        notesContainer.setOrientation(LinearLayout.VERTICAL);
        notesContainer.setPadding(16, 8, 16, 16);
        scrollView.addView(notesContainer);
        root.addView(scrollView);

        setContentView(root);
        loadNotes("", null);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                loadNotes(s.toString(), activeTag);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void buildTagFilters() {
        tagFilterRow.removeAllViews();
        for (String tag : DEFAULT_TAGS) {
            Button btn = new Button(this);
            btn.setText(tag);
            btn.setTextSize(11);
            btn.setTypeface(null, Typeface.BOLD);
            boolean isActive = (tag.equals("Tumu") && activeTag == null) || tag.equals(activeTag);
            btn.setBackgroundColor(isActive ? 0xFFE94560 : 0xFF16213E);
            btn.setTextColor(0xFFFFFFFF);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(4, 0, 4, 0);
            btn.setLayoutParams(p);
            btn.setPadding(24, 8, 24, 8);

            btn.setOnClickListener(v -> {
                activeTag = tag.equals("Tumu") ? null : tag;
                buildTagFilters();
                loadNotes("", activeTag);
            });

            tagFilterRow.addView(btn);
        }
    }

    private void loadNotes(String query, String tag) {
        notesContainer.removeAllViews();
        Cursor cursor;

        if (tag != null && !query.isEmpty()) {
            cursor = db.rawQuery("SELECT * FROM highlights WHERE note LIKE ? AND tag=? ORDER BY created_at DESC",
                new String[]{"%" + query + "%", tag});
        } else if (tag != null) {
            cursor = db.rawQuery("SELECT * FROM highlights WHERE tag=? ORDER BY created_at DESC", new String[]{tag});
        } else if (!query.isEmpty()) {
            cursor = db.rawQuery("SELECT * FROM highlights WHERE note LIKE ? ORDER BY created_at DESC",
                new String[]{"%" + query + "%"});
        } else {
            cursor = db.rawQuery("SELECT * FROM highlights ORDER BY created_at DESC", null);
        }

        if (cursor.getCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText(tag != null ? "Bu etikette not yok." : "Henuz not yok.\nPDF okurken kategori butonlariyla not ekle.");
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
            String noteTag = cursor.getString(cursor.getColumnIndexOrThrow("tag"));

            String label;
            int borderColor;
            switch (color != null ? color : "") {
                case "red":  label = "ITIRAZ";  borderColor = 0xFFE94560; break;
                case "blue": label = "ARGUMAN"; borderColor = 0xFF1565C0; break;
                default:     label = "VERI";    borderColor = 0xFF2E7D32; break;
            }

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20, 14, 20, 14);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, 10);
            card.setLayoutParams(cp);

            // Üst satır
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView labelView = new TextView(this);
            labelView.setText(label + "  |  Sayfa " + (page + 1));
            labelView.setTextColor(borderColor);
            labelView.setTextSize(11);
            labelView.setTypeface(null, Typeface.BOLD);
            labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            topRow.addView(labelView);

            // Etiket
            if (noteTag != null && !noteTag.isEmpty()) {
                TextView tagView = new TextView(this);
                tagView.setText(" " + noteTag + " ");
                tagView.setTextColor(0xFFFFFFFF);
                tagView.setTextSize(10);
                tagView.setBackgroundColor(0xFF6A1B9A);
                tagView.setPadding(8, 4, 8, 4);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tp.setMargins(0, 0, 8, 0);
                tagView.setLayoutParams(tp);
                topRow.addView(tagView);
            }

            Button btnEdit = new Button(this);
            btnEdit.setText("Duzenle");
            btnEdit.setBackgroundColor(0xFF0F3460);
            btnEdit.setTextColor(0xFFFFFFFF);
            btnEdit.setTextSize(10);
            btnEdit.setPadding(16, 4, 16, 4);
            topRow.addView(btnEdit);

            Button btnDelete = new Button(this);
            btnDelete.setText("Sil");
            btnDelete.setBackgroundColor(0xFFE94560);
            btnDelete.setTextColor(0xFFFFFFFF);
            btnDelete.setTextSize(10);
            btnDelete.setPadding(16, 4, 16, 4);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dp.setMargins(6, 0, 0, 0);
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
            final String currentTag = noteTag;

            btnEdit.setOnClickListener(v -> showEditDialog(noteId, currentNote, currentColor, currentTag));
            btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Notu Sil")
                .setMessage("Bu notu silmek istedigine emin misin?")
                .setPositiveButton("Sil", (d, w) -> {
                    db.delete("highlights", "id=?", new String[]{String.valueOf(noteId)});
                    Toast.makeText(this, "Silindi", Toast.LENGTH_SHORT).show();
                    loadNotes("", activeTag);
                })
                .setNegativeButton("Iptal", null)
                .show());
        }
        cursor.close();
    }

    private void showEditDialog(int noteId, String currentNote, String currentColor, String currentTag) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Notu Duzenle");

        LinearLayout el = new LinearLayout(this);
        el.setOrientation(LinearLayout.VERTICAL);
        el.setPadding(32, 16, 32, 16);

        // Kategori seçimi
        TextView colorLabel = new TextView(this);
        colorLabel.setText("Kategori:");
        colorLabel.setTextColor(0xFF888888);
        colorLabel.setTextSize(12);
        el.addView(colorLabel);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(0, 8, 0, 16);
        final String[] sel = {currentColor != null ? currentColor : "green"};

        Button bRed = new Button(this);
        bRed.setText("ITIRAZ");
        bRed.setBackgroundColor(sel[0].equals("red") ? 0xFFE94560 : 0x44E94560);
        bRed.setTextColor(0xFFFFFFFF);
        bRed.setTextSize(11);
        bRed.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button bBlue = new Button(this);
        bBlue.setText("ARGUMAN");
        bBlue.setBackgroundColor(sel[0].equals("blue") ? 0xFF1565C0 : 0x441565C0);
        bBlue.setTextColor(0xFFFFFFFF);
        bBlue.setTextSize(11);
        bBlue.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button bGreen = new Button(this);
        bGreen.setText("VERI");
        bGreen.setBackgroundColor(sel[0].equals("green") ? 0xFF2E7D32 : 0x442E7D32);
        bGreen.setTextColor(0xFFFFFFFF);
        bGreen.setTextSize(11);
        bGreen.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bRed.setOnClickListener(cv -> { sel[0]="red"; bRed.setBackgroundColor(0xFFE94560); bBlue.setBackgroundColor(0x441565C0); bGreen.setBackgroundColor(0x442E7D32); });
        bBlue.setOnClickListener(cv -> { sel[0]="blue"; bBlue.setBackgroundColor(0xFF1565C0); bRed.setBackgroundColor(0x44E94560); bGreen.setBackgroundColor(0x442E7D32); });
        bGreen.setOnClickListener(cv -> { sel[0]="green"; bGreen.setBackgroundColor(0xFF2E7D32); bRed.setBackgroundColor(0x44E94560); bBlue.setBackgroundColor(0x441565C0); });

        colorRow.addView(bRed);
        colorRow.addView(bBlue);
        colorRow.addView(bGreen);
        el.addView(colorRow);

        // Etiket seçimi
        TextView tagLabel = new TextView(this);
        tagLabel.setText("Etiket:");
        tagLabel.setTextColor(0xFF888888);
        tagLabel.setTextSize(12);
        el.addView(tagLabel);

        LinearLayout tagRow = new LinearLayout(this);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setPadding(0, 8, 0, 16);
        final String[] selTag = {currentTag != null ? currentTag : ""};

        List<String> tags = Arrays.asList("Felsefe", "Ekonomi", "Strateji", "Bilim", "Tarih", "Psikoloji", "Diger");
        Button[] tagBtns = new Button[tags.size()];

        HorizontalScrollView tagHScroll = new HorizontalScrollView(this);
        LinearLayout tagBtnRow = new LinearLayout(this);
        tagBtnRow.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < tags.size(); i++) {
            String t = tags.get(i);
            Button tb = new Button(this);
            tb.setText(t);
            tb.setTextSize(10);
            tb.setTextColor(0xFFFFFFFF);
            tb.setBackgroundColor(t.equals(selTag[0]) ? 0xFF6A1B9A : 0x446A1B9A);
            tb.setPadding(16, 6, 16, 6);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(4, 0, 4, 0);
            tb.setLayoutParams(tp);
            tagBtns[i] = tb;
            tagBtnRow.addView(tb);

            final int idx = i;
            tb.setOnClickListener(cv -> {
                selTag[0] = t;
                for (int j = 0; j < tagBtns.length; j++) {
                    tagBtns[j].setBackgroundColor(j == idx ? 0xFF6A1B9A : 0x446A1B9A);
                }
            });
        }
        tagHScroll.addView(tagBtnRow);
        el.addView(tagHScroll);

        // Not alanı
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
            values.put("tag", selTag[0]);
            db.update("highlights", values, "id=?", new String[]{String.valueOf(noteId)});
            Toast.makeText(this, "Guncellendi", Toast.LENGTH_SHORT).show();
            loadNotes("", activeTag);
        });
        builder.setNegativeButton("Iptal", null);
        builder.show();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(NotesActivity.this, "goblith.db", null, 5); }
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
