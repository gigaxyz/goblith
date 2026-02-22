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

        // Başlık
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
        subtitle.setPadding(24, 0, 24, 24);
        root.addView(subtitle);

        // İlk buton satırı
        LinearLayout btnRow1 = new LinearLayout(this);
        btnRow1.setOrientation(LinearLayout.HORIZONTAL);
        btnRow1.setPadding(16, 0, 16, 12);

        Button btnOpenFile = makeButton("+ DOSYA EKLE", 0xFFE94560, 1);
        Button btnNotes = makeButton("ALINTI BANKASI", 0xFF0F3460, 1);
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        m.setMargins(12, 0, 0, 0);
        btnNotes.setLayoutParams(m);

        btnRow1.addView(btnOpenFile);
        btnRow1.addView(btnNotes);
        root.addView(btnRow1);

        // İkinci buton satırı
        LinearLayout btnRow2 = new LinearLayout(this);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);
        btnRow2.setPadding(16, 0, 16, 24);

        Button btnWords = makeButton("KELIME ANALIZI", 0xFF6A1B9A, 1);
        Button btnStats = makeButton("ISTATISTIKLER", 0xFF1A5276, 1);
        LinearLayout.LayoutParams m2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        m2.setMargins(12, 0, 0, 0);
        btnStats.setLayoutParams(m2);

        btnRow2.addView(btnWords);
        btnRow2.addView(btnStats);
        root.addView(btnRow2);

        // Kütüphane başlık
        TextView libTitle = new TextView(this);
        libTitle.setText("KUTUPHANEM");
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

        btnOpenFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf", "text/plain", "text/html", "application/epub+zip"
            });
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_FILE);
        });

        btnNotes.setOnClickListener(v -> startActivity(new Intent(this, NotesActivity.class)));
        btnWords.setOnClickListener(v -> startActivity(new Intent(this, WordAnalysisActivity.class)));
        btnStats.setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
    }

    private Button makeButton(String text, int color, float weight) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(12);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        return btn;
    }

    private String getFileName(Uri uri) {
        String result = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) result = cursor.getString(idx);
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
        if (type.contains("epub")) return "EPUB";
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
            String customName = cursor.getString(1);
            String fileType = cursor.getString(2);
            int lastPage = cursor.getInt(3);
            String lastOpened = cursor.getString(4);
            if (fileType == null) fileType = "PDF";
            String displayName = (customName != null && !customName.isEmpty()) ? customName : "Bilinmeyen";

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
            typeTag.setText(" " + fileType + " ");
            typeTag.setTextColor(0xFFFFFFFF);
            typeTag.setTextSize(10);
            typeTag.setTypeface(null, android.graphics.Typeface.BOLD);
            typeTag.setBackgroundColor(fileType.equals("PDF") ? 0xFFE94560 : 0xFF0F3460);
            typeTag.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams tagP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tagP.setMargins(0, 0, 12, 0);
            typeTag.setLayoutParams(tagP);
            topRow.addView(typeTag);

            TextView nameView = new TextView(this);
            nameView.setText(displayName);
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
            infoView.setText("Sayfa " + (lastPage+1) + "  |  " + (lastOpened != null && lastOpened.length() >= 10 ? lastOpened.substring(0, 10) : ""));
            infoView.setTextColor(0xFF888888);
            infoView.setTextSize(12);
            infoView.setPadding(0, 6, 0, 0);
            card.addView(infoView);

            libraryContainer.addView(card);

            final String fUri = uri;
            final int fPage = lastPage;
            final String fType = fileType;
            final String fName = displayName;

            card.setOnClickListener(v -> openFile(fUri, fPage, fType));

            btnRename.setOnClickListener(v -> {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("Adi Degistir");
                EditText input = new EditText(this);
                input.setText(fName);
                input.setSelectAllOnFocus(true);
                b.setView(input);
                b.setPositiveButton("Kaydet", (d, w) -> {
                    ContentValues values = new ContentValues();
                    values.put("custom_name", input.getText().toString().trim());
                    db.update("library", values, "pdf_uri=?", new String[]{fUri});
                    loadLibrary();
                });
                b.setNegativeButton("Iptal", null);
                b.show();
            });

            btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Kaldir")
                .setMessage("\"" + fName + "\" listeden kaldirilsin mi?")
                .setPositiveButton("Kaldir", (d, w) -> { db.delete("library", "pdf_uri=?", new String[]{fUri}); loadLibrary(); })
                .setNegativeButton("Iptal", null)
                .show());
        }
        cursor.close();
    }

    private void openFile(String uri, int page, String fileType) {
        Intent viewer = new Intent(this, PdfViewerActivity.class);
        viewer.putExtra("pdfUri", uri);
        viewer.putExtra("startPage", page);
        viewer.putExtra("fileType", fileType);
        startActivity(viewer);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            try { getContentResolver().takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception e) {}
            String uriStr = fileUri.toString();
            String fileName = getFileName(fileUri);
            String fileType = getFileType(fileUri);
            ContentValues values = new ContentValues();
            values.put("pdf_uri", uriStr);
            values.put("custom_name", fileName);
            values.put("file_type", fileType);
            values.put("last_page", 0);
            values.put("last_opened", new java.util.Date().toString());
            db.insertWithOnConflict("library", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            openFile(uriStr, 0, fileType);
        }
    }

    @Override protected void onResume() { super.onResume(); loadLibrary(); }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(MainActivity.this, "goblith.db", null, 4); }
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
