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
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_PDF = 1;
    private LinearLayout libraryContainer;
    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DBHelper dbHelper = new DBHelper();
        db = dbHelper.getWritableDatabase();

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
        subtitle.setPadding(24, 0, 24, 32);
        root.addView(subtitle);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(16, 0, 16, 24);

        Button btnOpenPdf = new Button(this);
        btnOpenPdf.setText("PDF AC");
        btnOpenPdf.setBackgroundColor(0xFFE94560);
        btnOpenPdf.setTextColor(0xFFFFFFFF);
        btnOpenPdf.setTextSize(14);
        btnOpenPdf.setTypeface(null, android.graphics.Typeface.BOLD);
        btnOpenPdf.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnNotes = new Button(this);
        btnNotes.setText("ALINTI BANKASI");
        btnNotes.setBackgroundColor(0xFF0F3460);
        btnNotes.setTextColor(0xFFFFFFFF);
        btnNotes.setTextSize(14);
        btnNotes.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        notesParams.setMargins(12, 0, 0, 0);
        btnNotes.setLayoutParams(notesParams);

        btnRow.addView(btnOpenPdf);
        btnRow.addView(btnNotes);
        root.addView(btnRow);

        TextView libTitle = new TextView(this);
        libTitle.setText("SON OKUNANLAR");
        libTitle.setTextColor(0xFF888888);
        libTitle.setTextSize(12);
        libTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        libTitle.setPadding(24, 0, 24, 12);
        root.addView(libTitle);

        ScrollView scrollView = new ScrollView(this);
        libraryContainer = new LinearLayout(this);
        libraryContainer.setOrientation(LinearLayout.VERTICAL);
        libraryContainer.setPadding(16, 0, 16, 24);
        scrollView.addView(libraryContainer);
        root.addView(scrollView);

        setContentView(root);
        loadLibrary();

        btnOpenPdf.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/pdf");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_PDF);
        });

        btnNotes.setOnClickListener(v -> startActivity(new Intent(this, NotesActivity.class)));
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception e) {}
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) result = result.substring(cut + 1);
        }
        if (result != null && result.toLowerCase().endsWith(".pdf")) {
            result = result.substring(0, result.length() - 4);
        }
        return result != null ? result : "Bilinmeyen Kitap";
    }

    private void loadLibrary() {
        libraryContainer.removeAllViews();
        Cursor cursor = db.rawQuery(
            "SELECT pdf_uri, custom_name, last_page, last_opened FROM library ORDER BY last_opened DESC LIMIT 30", null);

        if (cursor.getCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Henuz kitap acilmadi.\nPDF AC butonuyla basla.");
            empty.setTextColor(0xFF555555);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(32, 32, 32, 32);
            libraryContainer.addView(empty);
            cursor.close();
            return;
        }

        while (cursor.moveToNext()) {
            String uri = cursor.getString(cursor.getColumnIndexOrThrow("pdf_uri"));
            String customName = cursor.getString(cursor.getColumnIndexOrThrow("custom_name"));
            int lastPage = cursor.getInt(cursor.getColumnIndexOrThrow("last_page"));
            String lastOpened = cursor.getString(cursor.getColumnIndexOrThrow("last_opened"));

            String displayName = (customName != null && !customName.isEmpty())
                ? customName
                : getFileName(Uri.parse(uri));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 10);
            card.setLayoutParams(cardParams);

            // Üst satır: isim + butonlar
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView nameView = new TextView(this);
            nameView.setText(displayName);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(15);
            nameView.setTypeface(null, android.graphics.Typeface.BOLD);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            topRow.addView(nameView);

            Button btnRename = new Button(this);
            btnRename.setText("Yeniden Adlandir");
            btnRename.setBackgroundColor(0xFF0F3460);
            btnRename.setTextColor(0xFFFFFFFF);
            btnRename.setTextSize(10);
            btnRename.setPadding(12, 4, 12, 4);
            topRow.addView(btnRename);

            Button btnDelete = new Button(this);
            btnDelete.setText("Sil");
            btnDelete.setBackgroundColor(0xFFE94560);
            btnDelete.setTextColor(0xFFFFFFFF);
            btnDelete.setTextSize(10);
            btnDelete.setPadding(12, 4, 12, 4);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dp.setMargins(8, 0, 0, 0);
            btnDelete.setLayoutParams(dp);
            topRow.addView(btnDelete);
            card.addView(topRow);

            TextView infoView = new TextView(this);
            infoView.setText("Sayfa " + (lastPage + 1) + "  |  " + (lastOpened != null && lastOpened.length() >= 10 ? lastOpened.substring(0, 10) : ""));
            infoView.setTextColor(0xFF888888);
            infoView.setTextSize(12);
            infoView.setPadding(0, 6, 0, 0);
            card.addView(infoView);

            libraryContainer.addView(card);

            final String pdfUri = uri;
            final int resumePage = lastPage;
            final String currentName = displayName;

            card.setOnClickListener(v -> openPdf(pdfUri, resumePage));

            btnRename.setOnClickListener(v -> {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("Kitap Adini Degistir");
                EditText input = new EditText(this);
                input.setText(currentName);
                input.setSelectAllOnFocus(true);
                b.setView(input);
                b.setPositiveButton("Kaydet", (d, w) -> {
                    ContentValues values = new ContentValues();
                    values.put("custom_name", input.getText().toString().trim());
                    db.update("library", values, "pdf_uri=?", new String[]{pdfUri});
                    Toast.makeText(this, "Ad guncellendi", Toast.LENGTH_SHORT).show();
                    loadLibrary();
                });
                b.setNegativeButton("Iptal", null);
                b.show();
            });

            btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Kitabi Kaldir")
                .setMessage("\"" + currentName + "\" listeden kaldirilsin mi?\n(Dosya silinmez)")
                .setPositiveButton("Kaldir", (d, w) -> {
                    db.delete("library", "pdf_uri=?", new String[]{pdfUri});
                    Toast.makeText(this, "Kaldirildi", Toast.LENGTH_SHORT).show();
                    loadLibrary();
                })
                .setNegativeButton("Iptal", null)
                .show());
        }
        cursor.close();
    }

    private void openPdf(String uri, int page) {
        Intent viewer = new Intent(this, PdfViewerActivity.class);
        viewer.putExtra("pdfUri", uri);
        viewer.putExtra("startPage", page);
        startActivity(viewer);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PDF && resultCode == Activity.RESULT_OK && data != null) {
            Uri pdfUri = data.getData();
            getContentResolver().takePersistableUriPermission(pdfUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String uriStr = pdfUri.toString();
            String fileName = getFileName(pdfUri);

            ContentValues values = new ContentValues();
            values.put("pdf_uri", uriStr);
            values.put("custom_name", fileName);
            values.put("last_page", 0);
            values.put("last_opened", new java.util.Date().toString());
            db.insertWithOnConflict("library", null, values, SQLiteDatabase.CONFLICT_REPLACE);

            openPdf(uriStr, 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLibrary();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(MainActivity.this, "goblith.db", null, 2); }
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, last_page INTEGER DEFAULT 0, last_opened TEXT)");
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int o, int n) {
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, last_page INTEGER DEFAULT 0, last_opened TEXT)");
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception e) {}
        }
    }
}
