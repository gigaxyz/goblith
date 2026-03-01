package com.goblith.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import androidx.core.content.ContextCompat;

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
        root.setBackgroundColor(0xFF0F0E17);

        // Başlık
        TextView title = new TextView(this);
        title.setText("Goblith");
        title.setTextColor(0xFF6D28D9);
        title.setTextSize(36);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 48, 24, 4);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Kisisel Bilgi Sistemi");
        subtitle.setTextColor(0xFF9B8EC4);
        subtitle.setTextSize(13);
        subtitle.setPadding(24, 0, 24, 16);
        root.addView(subtitle);

        // Butonlar — referansları saklıyoruz
        Button btnAdd    = makeBtn("+ DOSYA EKLE",  R.drawable.ic_add,      0xFF7C3AED);
        Button btnNotes  = makeBtn("ALINTI BANKASI", R.drawable.ic_notes,    0xFF2D2B55);
        Button btnWords  = makeBtn("KELIME ANALIZI", R.drawable.ic_words,    0xFF2D2B55);
        Button btnStats  = makeBtn("ISTATISTIKLER",  R.drawable.ic_stats,    0xFF2D2B55);
        Button btnCross  = makeBtn("CAPRAZ BAGLANTI",R.drawable.ic_cross,    0xFF2D2B55);
        Button btnFlash  = makeBtn("FLASHCARD",      R.drawable.ic_flash,    0xFF2D2B55);
        Button btnList   = makeBtn("OKUMA LISTESI",  R.drawable.ic_list,     0xFF2D2B55);
        Button btnBmarks = makeBtn("YER IMLERI",     R.drawable.ic_bookmark, 0xFF2D2B55);
        Button btnArchiveMain = makeBtn("ARSIV",          R.drawable.ic_archive,  0xFF7C3AED);
        Button btnSearch     = makeBtn("PDF ARAMA",      R.drawable.ic_search,   0xFF2D2B55);

        // DOSYA EKLE — tam genişlik, vurgulu
        root.addView(makeFullRow(btnAdd));

        // 2'li satırlar — koyu butonlar
        root.addView(makeRow(btnNotes,  btnBmarks));
        root.addView(makeRow(btnWords,  btnStats));
        root.addView(makeRow(btnCross,  btnFlash));
        root.addView(makeRow(btnList,   btnSearch));

        // ARŞİV — tam genişlik, vurgulu
        root.addView(makeFullRow(btnArchiveMain));

        TextView libTitle = new TextView(this);
        libTitle.setText("KUTUPHANEM");
        libTitle.setTextColor(0xFF9B8EC4);
        libTitle.setTextSize(12);
        libTitle.setTypeface(null, Typeface.BOLD);
        libTitle.setPadding(24, 4, 24, 10);
        root.addView(libTitle);

        ScrollView sv = new ScrollView(this);
        libraryContainer = new LinearLayout(this);
        libraryContainer.setOrientation(LinearLayout.VERTICAL);
        libraryContainer.setPadding(16, 0, 16, 24);
        sv.addView(libraryContainer);
        root.addView(sv);

        setContentView(root);
        loadLibrary();

        // Tıklama olayları
        btnAdd.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf","text/plain","application/epub+zip"});
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, PICK_FILE);
        });
        btnNotes.setOnClickListener(v  -> startActivity(new Intent(this, NotesActivity.class)));
        btnWords.setOnClickListener(v  -> startActivity(new Intent(this, WordAnalysisActivity.class)));
        btnStats.setOnClickListener(v  -> startActivity(new Intent(this, StatsActivity.class)));
        btnCross.setOnClickListener(v  -> startActivity(new Intent(this, CrossRefActivity.class)));
        btnFlash.setOnClickListener(v  -> startActivity(new Intent(this, FlashcardActivity.class)));
        btnArchiveMain.setOnClickListener(v -> startActivity(new Intent(this, ArchiveActivity.class)));
        btnSearch.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        btnList.setOnClickListener(v   -> startActivity(new Intent(this, ReadingListActivity.class)));
        btnBmarks.setOnClickListener(v -> showAllBookmarks());

    }

    private LinearLayout makeRow(Button b1, Button b2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 0, 16, 8);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, 150, 1);
        lp1.setMargins(0, 0, 4, 0);
        b1.setLayoutParams(lp1);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, 150, 1);
        lp2.setMargins(4, 0, 0, 0);
        b2.setLayoutParams(lp2);
        row.addView(b1);
        row.addView(b2);
        return row;
    }

    private LinearLayout makeFullRow(Button b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 0, 16, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 150);
        b.setLayoutParams(lp);
        row.addView(b);
        return row;
    }

    private Button makeBtn(String text, int iconRes, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setBackgroundColor(color);
        b.setTextColor(0xFFE2D9F3);
        b.setTextSize(10);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setGravity(android.view.Gravity.CENTER);
        b.setPadding(8, 20, 8, 20);
        b.setLetterSpacing(0.08f);
        try {
            android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(this, iconRes);
            if (icon != null) {
                icon.setBounds(0, 0, 52, 52);
                icon.setTint(0xFFE2D9F3);
                b.setCompoundDrawables(null, icon, null, null);
                b.setCompoundDrawablePadding(8);
            }
        } catch (Exception ignored) {}
        return b;
    }

    private void showAllBookmarks() {
        Cursor c = db.rawQuery(
            "SELECT b.page, b.title, l.custom_name, b.pdf_uri " +
            "FROM bookmarks b LEFT JOIN library l ON b.pdf_uri=l.pdf_uri " +
            "ORDER BY b.created_at DESC", null);
        if (!c.moveToFirst()) {
            Toast.makeText(this, "Henuz yer imi yok", Toast.LENGTH_SHORT).show();
            c.close(); return;
        }
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<String> uris   = new java.util.ArrayList<>();
        java.util.List<Integer> pages  = new java.util.ArrayList<>();
        do {
            String book = c.getString(2) != null ? c.getString(2) : "?";
            labels.add(book + " — " + c.getString(1));
            uris.add(c.getString(3));
            pages.add(c.getInt(0));
        } while (c.moveToNext());
        c.close();
        new AlertDialog.Builder(this)
            .setTitle("Tum Yer Imleri")
            .setItems(labels.toArray(new String[0]), (d, w) -> openFile(uris.get(w), pages.get(w), "PDF"))
            .setNegativeButton("Kapat", null)
            .show();
    }

    private String getFileName(Uri uri) {
        String r = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) r = c.getString(idx);
            }
        } catch (Exception ignored) {}
        if (r == null) r = uri.getLastPathSegment();
        if (r == null) r = "Bilinmeyen";
        int dot = r.lastIndexOf('.');
        if (dot > 0) r = r.substring(0, dot);
        return r;
    }

    private String getFileType(Uri uri) {
        String t = getContentResolver().getType(uri);
        if (t == null) t = "";
        if (t.contains("pdf")) return "PDF";
        if (t.contains("text")) return "TXT";
        String p = uri.toString().toLowerCase();
        if (p.endsWith(".pdf")) return "PDF";
        if (p.endsWith(".txt")) return "TXT";
        return "PDF";
    }

    private void loadLibrary() {
        libraryContainer.removeAllViews();
        Cursor cursor = db.rawQuery(
            "SELECT pdf_uri,custom_name,file_type,last_page,last_opened FROM library ORDER BY last_opened DESC LIMIT 30", null);
        if (cursor.getCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Kutuphaneniz bos.\nDOSYA EKLE ile baslayin.");
            empty.setTextColor(0xFF555555);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(32, 48, 32, 32);
            libraryContainer.addView(empty);
            cursor.close(); return;
        }
        while (cursor.moveToNext()) {
            String uri    = cursor.getString(0);
            String name   = cursor.getString(1);
            String type   = cursor.getString(2);
            int    page   = cursor.getInt(3);
            String opened = cursor.getString(4);
            if (type == null) type = "PDF";
            if (name == null || name.isEmpty()) name = "Bilinmeyen";

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF1A1831);
            card.setPadding(20, 16, 20, 16);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, 10);
            card.setLayoutParams(cp);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView typeTag = new TextView(this);
            typeTag.setText(" " + type + " ");
            typeTag.setTextColor(0xFFFFFFFF);
            typeTag.setTextSize(10);
            typeTag.setTypeface(null, Typeface.BOLD);
            typeTag.setBackgroundColor(type.equals("PDF") ? 0xFF6D28D9 : 0xFF1E1B4B);
            typeTag.setPadding(8, 4, 8, 4);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(0, 0, 10, 0);
            typeTag.setLayoutParams(tp);
            topRow.addView(typeTag);

            TextView nameView = new TextView(this);
            nameView.setText(name);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(15);
            nameView.setTypeface(null, Typeface.BOLD);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            topRow.addView(nameView);

            Button btnRename = new Button(this);
            btnRename.setText("✎");
            btnRename.setBackgroundColor(0xFF1E1B4B);
            btnRename.setTextColor(0xFFFFFFFF);
            btnRename.setTextSize(14);
            btnRename.setPadding(16, 4, 16, 4);
            topRow.addView(btnRename);

            Button btnDel = new Button(this);
            btnDel.setText("✕");
            btnDel.setBackgroundColor(0xFF6D28D9);
            btnDel.setTextColor(0xFFFFFFFF);
            btnDel.setTextSize(14);
            btnDel.setPadding(16, 4, 16, 4);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dp.setMargins(6, 0, 0, 0);
            btnDel.setLayoutParams(dp);
            topRow.addView(btnDel);
            card.addView(topRow);

            TextView info = new TextView(this);
            info.setText("Sayfa " + (page+1) + "  |  " + (opened != null && opened.length() >= 10 ? opened.substring(0,10) : ""));
            info.setTextColor(0xFF888888);
            info.setTextSize(12);
            info.setPadding(0, 6, 0, 0);
            card.addView(info);

            libraryContainer.addView(card);

            final String fUri = uri, fType = type, fName = name;
            final int fPage = page;

            card.setOnClickListener(v -> openFile(fUri, fPage, fType));
            btnRename.setOnClickListener(v -> {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("Adi Degistir");
                EditText input = new EditText(this);
                input.setText(fName);
                input.setSelectAllOnFocus(true);
                b.setView(input);
                b.setPositiveButton("Kaydet", (d, w) -> {
                    ContentValues val = new ContentValues();
                    val.put("custom_name", input.getText().toString().trim());
                    db.update("library", val, "pdf_uri=?", new String[]{fUri});
                    loadLibrary();
                });
                b.setNegativeButton("Iptal", null);
                b.show();
            });
            btnDel.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                    .setTitle("Kaldir")
                    .setMessage("\"" + fName + "\" listeden kaldirilsin mi?")
                    .setPositiveButton("Kaldir", (d, w) -> { db.delete("library","pdf_uri=?",new String[]{fUri}); loadLibrary(); })
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

    private android.app.ProgressDialog ocrDialog;

    private void startOcrIndexing(String uriStr, String fileType) {
        if (!fileType.equals("PDF")) return;
        ocrDialog = new android.app.ProgressDialog(this);
        ocrDialog.setTitle("Kitap Hazirlaniyor");
        ocrDialog.setMessage("Sayfalar okunuyor...");
        ocrDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        ocrDialog.setMax(100);
        ocrDialog.setCancelable(false);
        ocrDialog.show();
        new Thread(() -> {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS pdf_ocr_cache (" +
                    "pdf_uri TEXT, page INTEGER, ocr_text TEXT, PRIMARY KEY(pdf_uri, page))");
                android.os.ParcelFileDescriptor pfd = getContentResolver()
                    .openFileDescriptor(android.net.Uri.parse(uriStr), "r");
                if (pfd == null) { runOnUiThread(() -> ocrDialog.dismiss()); return; }
                android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(pfd);
                int total = renderer.getPageCount();
                com.google.mlkit.vision.text.TextRecognizer recognizer =
                    com.google.mlkit.vision.text.TextRecognition.getClient(
                        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS);
                for (int p = 0; p < total; p++) {
                    final int prog = (int)((p + 1.0) / total * 100);
                    final int pn = p;
                    runOnUiThread(() -> {
                        ocrDialog.setProgress(prog);
                        ocrDialog.setMessage("Sayfa " + (pn+1) + " / " + total);
                    });
                    android.database.Cursor chk = db.rawQuery(
                        "SELECT 1 FROM pdf_ocr_cache WHERE pdf_uri=? AND page=?",
                        new String[]{uriStr, String.valueOf(p)});
                    boolean cached = chk.moveToFirst(); chk.close();
                    if (cached) continue;
                    android.graphics.pdf.PdfRenderer.Page pg = renderer.openPage(p);
                    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                        pg.getWidth()*3, pg.getHeight()*3, android.graphics.Bitmap.Config.ARGB_8888);
                    new android.graphics.Canvas(bmp).drawColor(android.graphics.Color.WHITE);
                    pg.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                    pg.close();
                    try {
                        com.google.mlkit.vision.text.Text vt =
                            com.google.android.gms.tasks.Tasks.await(
                                recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0)));
                        android.content.ContentValues cv = new android.content.ContentValues();
                        cv.put("pdf_uri", uriStr); cv.put("page", p); cv.put("ocr_text", vt.getText());
                        db.insertWithOnConflict("pdf_ocr_cache", null, cv,
                            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
                    } catch (Exception ignored) {}
                    bmp.recycle();
                }
                renderer.close(); pfd.close(); recognizer.close();
                runOnUiThread(() -> {
                    ocrDialog.dismiss();
                    Toast.makeText(this, "Kitap arama icin hazir!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> ocrDialog.dismiss());
            }
        }).start();
    }

    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_FILE && res == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            String uriStr = uri.toString();
            String name   = getFileName(uri);
            String type   = getFileType(uri);
            ContentValues val = new ContentValues();
            val.put("pdf_uri",     uriStr);
            val.put("custom_name", name);
            val.put("file_type",   type);
            val.put("last_page",   0);
            val.put("last_opened", new java.util.Date().toString());
            db.insertWithOnConflict("library", null, val, SQLiteDatabase.CONFLICT_REPLACE);
            startOcrIndexing(uriStr, type);
            openFile(uriStr, 0, type);
        }
    }

    @Override protected void onResume() { super.onResume(); loadLibrary(); }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(MainActivity.this, "goblith.db", null, 8); }
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
