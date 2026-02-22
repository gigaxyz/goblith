package com.goblith.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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

        // Başlık
        TextView title = new TextView(this);
        title.setText("Goblith");
        title.setTextColor(0xFFE94560);
        title.setTextSize(36);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(24, 48, 24, 4);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Kişisel Bilgi Sistemi");
        subtitle.setTextColor(0xFF888888);
        subtitle.setTextSize(13);
        subtitle.setPadding(24, 0, 24, 32);
        root.addView(subtitle);

        // Butonlar
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(16, 0, 16, 24);

        Button btnOpenPdf = new Button(this);
        btnOpenPdf.setText("📖 PDF Aç");
        btnOpenPdf.setBackgroundColor(0xFFE94560);
        btnOpenPdf.setTextColor(0xFFFFFFFF);
        btnOpenPdf.setTextSize(15);
        btnOpenPdf.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnNotes = new Button(this);
        btnNotes.setText("📚 Alıntılar");
        btnNotes.setBackgroundColor(0xFF0F3460);
        btnNotes.setTextColor(0xFFFFFFFF);
        btnNotes.setTextSize(15);
        LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        notesParams.setMargins(12, 0, 0, 0);
        btnNotes.setLayoutParams(notesParams);

        btnRow.addView(btnOpenPdf);
        btnRow.addView(btnNotes);
        root.addView(btnRow);

        // Son Okunanlar başlığı
        TextView libTitle = new TextView(this);
        libTitle.setText("📂 Son Okunanlar");
        libTitle.setTextColor(0xFFFFFFFF);
        libTitle.setTextSize(16);
        libTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        libTitle.setPadding(24, 0, 24, 12);
        root.addView(libTitle);

        // Kitap listesi
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

        btnNotes.setOnClickListener(v -> {
            startActivity(new Intent(this, NotesActivity.class));
        });
    }

    private void loadLibrary() {
        libraryContainer.removeAllViews();
        Cursor cursor = db.rawQuery(
            "SELECT DISTINCT pdf_uri, last_opened, last_page FROM library ORDER BY last_opened DESC LIMIT 20", null);

        if (cursor.getCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Henüz kitap açılmadı.\nPDF Aç butonuyla başla.");
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
            int lastPage = cursor.getInt(cursor.getColumnIndexOrThrow("last_page"));
            String lastOpened = cursor.getString(cursor.getColumnIndexOrThrow("last_opened"));

            // Dosya adını URI'dan çıkar
            String fileName = uri;
            if (uri.contains("/")) fileName = uri.substring(uri.lastIndexOf("/") + 1);
            if (fileName.contains("%")) {
                try { fileName = java.net.URLDecoder.decode(fileName, "UTF-8"); } catch (Exception e) {}
            }
            if (fileName.endsWith(".pdf")) fileName = fileName.substring(0, fileName.length() - 4);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 10);
            card.setLayoutParams(cardParams);

            TextView nameView = new TextView(this);
            nameView.setText("📄 " + fileName);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(15);
            nameView.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(nameView);

            TextView infoView = new TextView(this);
            infoView.setText("Son sayfa: " + (lastPage + 1) + "  •  " + (lastOpened != null ? lastOpened.substring(0, 10) : ""));
            infoView.setTextColor(0xFF888888);
            infoView.setTextSize(12);
            infoView.setPadding(0, 4, 0, 0);
            card.addView(infoView);

            libraryContainer.addView(card);

            final String pdfUri = uri;
            final int resumePage = lastPage;
            card.setOnClickListener(v -> openPdf(pdfUri, resumePage));
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
            getContentResolver().takePersistableUriPermission(pdfUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String uriStr = pdfUri.toString();

            ContentValues values = new ContentValues();
            values.put("pdf_uri", uriStr);
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
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, last_page INTEGER DEFAULT 0, last_opened TEXT)");
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int o, int n) {
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, last_page INTEGER DEFAULT 0, last_opened TEXT)");
        }
    }
}
