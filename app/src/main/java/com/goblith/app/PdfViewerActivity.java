package com.goblith.app;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;

public class PdfViewerActivity extends AppCompatActivity {

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private int currentPage = 0;
    private ImageView pageView;
    private TextView pageInfo;
    private SQLiteDatabase db;
    private String pdfUri;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DBHelper dbHelper = new DBHelper();
        db = dbHelper.getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Üst bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF0F3460);
        topBar.setPadding(8, 12, 8, 12);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button btnPrev = new Button(this);
        btnPrev.setText("◀");
        btnPrev.setBackgroundColor(0x00000000);
        btnPrev.setTextColor(0xFFFFFFFF);
        btnPrev.setTextSize(18);

        pageInfo = new TextView(this);
        pageInfo.setTextColor(0xFFFFFFFF);
        pageInfo.setTextSize(14);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        pageInfo.setLayoutParams(infoParams);
        pageInfo.setGravity(Gravity.CENTER);

        Button btnGo = new Button(this);
        btnGo.setText("Git");
        btnGo.setBackgroundColor(0xFFE94560);
        btnGo.setTextColor(0xFFFFFFFF);
        btnGo.setTextSize(12);
        btnGo.setPadding(16, 4, 16, 4);

        Button btnNext = new Button(this);
        btnNext.setText("▶");
        btnNext.setBackgroundColor(0x00000000);
        btnNext.setTextColor(0xFFFFFFFF);
        btnNext.setTextSize(18);

        topBar.addView(btnPrev);
        topBar.addView(pageInfo);
        topBar.addView(btnGo);
        topBar.addView(btnNext);

        // Sayfa
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF2A2A2A);
        pageView = new ImageView(this);
        pageView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageView.setAdjustViewBounds(true);
        scrollView.addView(pageView);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Alt butonlar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF0F3460);
        bottomBar.setPadding(8, 8, 8, 8);

        Button btnRed = new Button(this);
        btnRed.setText("🔴 İtiraz");
        btnRed.setBackgroundColor(0xFFE94560);
        btnRed.setTextColor(0xFFFFFFFF);
        btnRed.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnBlue = new Button(this);
        btnBlue.setText("🔵 Argüman");
        btnBlue.setBackgroundColor(0xFF1565C0);
        btnBlue.setTextColor(0xFFFFFFFF);
        btnBlue.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnGreen = new Button(this);
        btnGreen.setText("🟢 Veri");
        btnGreen.setBackgroundColor(0xFF2E7D32);
        btnGreen.setTextColor(0xFFFFFFFF);
        btnGreen.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bottomBar.addView(btnRed);
        bottomBar.addView(btnBlue);
        bottomBar.addView(btnGreen);

        root.addView(topBar);
        root.addView(scrollView);
        root.addView(bottomBar);
        setContentView(root);

        pdfUri = getIntent().getStringExtra("pdfUri");
        int startPage = getIntent().getIntExtra("startPage", 0);

        try {
            fileDescriptor = getContentResolver().openFileDescriptor(Uri.parse(pdfUri), "r");
            pdfRenderer = new PdfRenderer(fileDescriptor);
            showPage(startPage);
        } catch (IOException e) {
            Toast.makeText(this, "PDF açılamadı", Toast.LENGTH_SHORT).show();
        }

        btnPrev.setOnClickListener(v -> { if (currentPage > 0) showPage(currentPage - 1); });
        btnNext.setOnClickListener(v -> { if (pdfRenderer != null && currentPage < pdfRenderer.getPageCount() - 1) showPage(currentPage + 1); });

        btnGo.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Sayfaya Git");
            EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setHint("Sayfa numarası");
            b.setView(input);
            b.setPositiveButton("Git", (d, w) -> {
                try {
                    int p = Integer.parseInt(input.getText().toString()) - 1;
                    if (p >= 0 && p < pdfRenderer.getPageCount()) showPage(p);
                    else Toast.makeText(this, "Geçersiz sayfa", Toast.LENGTH_SHORT).show();
                } catch (Exception e) { Toast.makeText(this, "Sayı gir", Toast.LENGTH_SHORT).show(); }
            });
            b.setNegativeButton("İptal", null);
            b.show();
        });

        btnRed.setOnClickListener(v -> saveHighlight("red", "🔴 İtiraz"));
        btnBlue.setOnClickListener(v -> saveHighlight("blue", "🔵 Argüman"));
        btnGreen.setOnClickListener(v -> saveHighlight("green", "🟢 Veri"));
    }

    private void showPage(int index) {
        if (pdfRenderer == null) return;
        currentPage = index;
        PdfRenderer.Page page = pdfRenderer.openPage(index);
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = (int) ((float) page.getHeight() / page.getWidth() * width);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        pageView.setImageBitmap(bitmap);
        scrollView.scrollTo(0, 0);
        pageInfo.setText((index + 1) + " / " + pdfRenderer.getPageCount());

        // Kütüphaneye kaydet
        ContentValues values = new ContentValues();
        values.put("pdf_uri", pdfUri);
        values.put("last_page", index);
        values.put("last_opened", new java.util.Date().toString());
        db.insertWithOnConflict("library", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void saveHighlight(String color, String label) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(label + " — Sayfa " + (currentPage + 1));
        EditText input = new EditText(this);
        input.setHint("Bu sayfaya notun...");
        b.setView(input);
        b.setPositiveButton("Kaydet", (d, w) -> {
            ContentValues values = new ContentValues();
            values.put("pdf_uri", pdfUri);
            values.put("page", currentPage);
            values.put("color", color);
            values.put("note", input.getText().toString());
            db.insert("highlights", null, values);
            Toast.makeText(this, "Kaydedildi ✓", Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("İptal", null);
        b.show();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(PdfViewerActivity.this, "goblith.db", null, 2); }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) fileDescriptor.close();
        } catch (IOException e) { e.printStackTrace(); }
    }
}
