package com.goblith.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_FILE = 1;
    private LinearLayout libraryContainer;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        TextView title = new TextView(this);
        title.setText("Goblith");
        title.setTextColor(0xFFE94560);
        title.setTextSize(36);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(24, 48, 24, 4);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Kisisel Bilgi Sistemi");
        subtitle.setTextColor(0xFF888888);
        subtitle.setTextSize(13);
        subtitle.setPadding(24, 0, 24, 20);
        root.addView(subtitle);

        // Buton satırı 1
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(16, 0, 16, 10);
        Button btnAdd = makeBtn("+ DOSYA EKLE", 0xFFE94560);
        Button btnNotes = makeBtn("ALINTI BANKASI", 0xFF0F3460);
        setMarginLeft(btnNotes, 10);
        row1.addView(btnAdd);
        row1.addView(btnNotes);
        root.addView(row1);

        // Buton satırı 2
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(16, 0, 16, 10);
        Button btnWords = makeBtn("KELIME ANALIZI", 0xFF6A1B9A);
        Button btnStats = makeBtn("ISTATISTIKLER", 0xFF1A5276);
        setMarginLeft(btnStats, 10);
        row2.addView(btnWords);
        row2.addView(btnStats);
        root.addView(row2);

        // Buton satırı 3
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(16, 0, 16, 20);
        Button btnCross = makeBtn("CAPRAZ KITAP BAGLANTISI", 0xFF1B5E20);
        row3.addView(btnCross);
        root.addView(row3);

        TextView libTitle = new TextView(this);
        libTitle.setText("KUTUPHANEM");
        libTitle.setTextColor(0xFF888888);
        libTitle.setTextSize(12);
        libTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        libTitle.setPadding(24, 0, 24, 10);
        root.addView(libTitle);

        ScrollView sv = new ScrollView(this);
        libraryContainer = new LinearLayout(this);
        libraryContainer.setOrientation(LinearLayout.VERTICAL);
        libraryContainer.setPadding(16, 0, 16, 24);
        sv.addView(libraryContainer);
        root.addView(sv);

        setContentView(root);
        loadLibrary();

        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf","text/plain","text/html","application/epub+zip"
            });
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_FILE);
        });

        btnNotes.setOnClickListener(v -> startActivity(new Intent(this, NotesActivity.class)));
        btnWords.setOnClickListener(v -> startActivity(new Intent(this, WordAnalysisActivity.class)));
        btnStats.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
        btnCross.setOnClickListener(v -> startActivity(new Intent(this, CrossRefActivity.class)));
    }

    private Button makeBtn(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(12);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return btn;
    }

    private void setMarginLeft(Button btn, int dp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        p.setMargins(dp, 0, 0, 0);
        btn.setLayoutParams(p);
    }

    private String getFileName(Uri uri) {
        String result = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = c.getString(idx);
            }
        } catch (Exception e) {}
        if (result == null) result = uri.getLastPathSegment();
        if (result == null) result = "Bilinmeyen";
        int dot = result.lastIndexOf('.');
        if (dot > 0) result = result.substring(0, dot);
        return result;
    }

    private String getFileType(Uri uri) {
        String type = getContentResolver().getType(uri);
        if (type == null) type = "";
        if (type.contains("pdf")) return "PDF";
        if (type.contains("text")) return "TXT";
        String path = uri.toString().toLowerCase();
        if (path.endsWith(".pdf")) return "PDF";
        if (path.endsWith(".txt")) return "TXT";
        return "PDF";
    }

    private void loadLibrary() {
        libraryContainer.removeAllViews();
        Cursor cursor = db.rawQuery(
            "SELECT pdf_uri, custom_name, file_type, last_page, last_opened FROM library ORDER BY last_opened DESC LIMIT 30", null);

        if (cursor.getCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Kutuphaneniz bos.\nDOSYA EKLE ile baslayin.");
            empty.setTextColor(0xFF555555);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(32, 48, 32, 32);
            libraryContainer.addView(empty);
            cursor.close();
            return;
        }

        while (cursor.moveToNext()) {
            String uri = cursor.getString(0);
            String name = cursor.getString(1);
            String type = cursor.getString(2);
            int page = cursor.getInt(3);
            String opened = cursor.getString(4);
            if (type == null) type = "PDF";
            if (name == null || name.isEmpty()) name = "Bilinmeyen";

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, 10);
            card.setLayoutParams(cp);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView typeTag = new TextView(this);
            typeTag.setText(" "+type+" ");
            typeTag.setTextColor(0xFFFFFFFF);
            typeTag.setTextSize(10);
            typeTag.setTypeface(null, android.graphics.Typeface.BOLD);
            typeTag.setBackgroundColor(type.equals("PDF") ? 0xFFE94560 : 0xFF0F3460);
            typeTag.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(0, 0, 10, 0);
            typeTag.setLayoutParams(tp);
            topRow.addView(typeTag);

            TextView nameView = new TextView(this);
            nameView.setText(name);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(15);
            nameView.setTypeface(null, android.graphics.Typeface.BOLD);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            topRow.addView(nameView);

            Button btnRename = new Button(this);
            btnRename.setText("Duzenle");
            btnRename.setBackgroundColor(0xFF0F3460);
            btnRename.setTextColor(0xFFFFFFFF);
            btnRename.setTextSize(10);
            btnRename.setPadding(12, 4, 12, 4);
            topRow.addView(btnRename);

            Button btnDel = new Button(this);
            btnDel.setText("Sil");
            btnDel.setBackgroundColor(0xFFE94560);
            btnDel.setTextColor(0xFFFFFFFF);
            btnDel.setTextSize(10);
            btnDel.setPadding(12, 4, 12, 4);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dp.setMargins(6, 0, 0, 0);
            btnDel.setLayoutParams(dp);
            topRow.addView(btnDel);
            card.addView(topRow);

            TextView info = new TextView(this);
            info.setText("Sayfa "+(page+1)+"  |  "+(opened!=null&&opened.length()>=10?opened.substring(0,10):""));
            info.setTextColor(0xFF888888);
            info.setTextSize(12);
            info.setPadding(0, 6, 0, 0);
            card.addView(info);

            libraryContainer.addView(card);

            final String fUri=uri, fType=type, fName=name;
            final int fPage=page;

            card.setOnClickListener(v -> openFile(fUri, fPage, fType));

            btnRename.setOnClickListener(v -> {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("Adi Degistir");
                EditText input = new EditText(this);
                input.setText(fName);
                input.setSelectAllOnFocus(true);
                b.setView(input);
                b.setPositiveButton("Kaydet", (d,w) -> {
                    ContentValues val = new ContentValues();
                    val.put("custom_name", input.getText().toString().trim());
                    db.update("library", val, "pdf_uri=?", new String[]{fUri});
                    loadLibrary();
                });
                b.setNegativeButton("Iptal", null);
                b.show();
            });

            btnDel.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Kaldir")
                .setMessage("\""+fName+"\" listeden kaldirilsin mi?")
                .setPositiveButton("Kaldir", (d,w) -> { db.delete("library","pdf_uri=?",new String[]{fUri}); loadLibrary(); })
                .setNegativeButton("Iptal", null)
                .show());
        }
        cursor.close();
    }

    private void openFile(String uri, int page, String type) {
        Intent i = new Intent(this, PdfViewerActivity.class);
        i.putExtra("pdfUri", uri);
        i.putExtra("startPage", page);
        i.putExtra("fileType", type);
        startActivity(i);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req==PICK_FILE && res==Activity.RESULT_OK && data!=null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception e) {}
            String uriStr = uri.toString();
            String name = getFileName(uri);
            String type = getFileType(uri);
            ContentValues val = new ContentValues();
            val.put("pdf_uri", uriStr);
            val.put("custom_name", name);
            val.put("file_type", type);
            val.put("last_page", 0);
            val.put("last_opened", new java.util.Date().toString());
            db.insertWithOnConflict("library", null, val, SQLiteDatabase.CONFLICT_REPLACE);
            openFile(uriStr, 0, type);
        }
    }

    @Override protected void onResume() { super.onResume(); loadLibrary(); }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(MainActivity.this, "goblith.db", null, 5); }
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
