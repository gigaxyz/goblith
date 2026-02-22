package com.goblith.app;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Button;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Veritabanı kur
        DBHelper dbHelper = new DBHelper();
        db = dbHelper.getWritableDatabase();

        // Ana layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Üst bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF16213E);
        topBar.setPadding(16, 16, 16, 16);

        Button btnPrev = new Button(this);
        btnPrev.setText("◀");
        btnPrev.setBackgroundColor(0xFF16213E);
        btnPrev.setTextColor(0xFFFFFFFF);

        pageInfo = new TextView(this);
        pageInfo.setTextColor(0xFFFFFFFF);
        pageInfo.setPadding(16, 0, 16, 0);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        pageInfo.setLayoutParams(infoParams);
        pageInfo.setGravity(android.view.Gravity.CENTER);

        Button btnNext = new Button(this);
        btnNext.setText("▶");
        btnNext.setBackgroundColor(0xFF16213E);
        btnNext.setTextColor(0xFFFFFFFF);

        topBar.addView(btnPrev);
        topBar.addView(pageInfo);
        topBar.addView(btnNext);

        // Sayfa gösterici
        ScrollView scrollView = new ScrollView(this);
        pageView = new ImageView(this);
        pageView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        scrollView.addView(pageView);
        LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        scrollView.setLayoutParams(svParams);

        // Alt butonlar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF16213E);
        bottomBar.setPadding(8, 8, 8, 8);

        Button btnRed = new Button(this);
        btnRed.setText("🔴 İtiraz");
        btnRed.setBackgroundColor(0xFFE94560);
        btnRed.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        btnRed.setLayoutParams(btnParams);

        Button btnBlue = new Button(this);
        btnBlue.setText("🔵 Argüman");
        btnBlue.setBackgroundColor(0xFF0F3460);
        btnBlue.setTextColor(0xFFFFFFFF);
        btnBlue.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button btnGreen = new Button(this);
        btnGreen.setText("🟢 Veri");
        btnGreen.setBackgroundColor(0xFF1B5E20);
        btnGreen.setTextColor(0xFFFFFFFF);
        btnGreen.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        bottomBar.addView(btnRed);
        bottomBar.addView(btnBlue);
        bottomBar.addView(btnGreen);

        root.addView(topBar);
        root.addView(scrollView);
        root.addView(bottomBar);
        setContentView(root);

        // PDF aç
        pdfUri = getIntent().getStringExtra("pdfUri");
        try {
            fileDescriptor = getContentResolver().openFileDescriptor(Uri.parse(pdfUri), "r");
            pdfRenderer = new PdfRenderer(fileDescriptor);
            showPage(0);
        } catch (IOException e) {
            Toast.makeText(this, "PDF açılamadı", Toast.LENGTH_SHORT).show();
        }

        // Buton olayları
        btnPrev.setOnClickListener(v -> {
            if (currentPage > 0) showPage(currentPage - 1);
        });

        btnNext.setOnClickListener(v -> {
            if (currentPage < pdfRenderer.getPageCount() - 1) showPage(currentPage + 1);
        });

        btnRed.setOnClickListener(v -> saveHighlight("red"));
        btnBlue.setOnClickListener(v -> saveHighlight("blue"));
        btnGreen.setOnClickListener(v -> saveHighlight("green"));
    }

    private void showPage(int index) {
        if (pdfRenderer == null) return;
        currentPage = index;
        PdfRenderer.Page page = pdfRenderer.openPage(index);
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = (int) ((float) page.getHeight() / page.getWidth() * width);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        pageView.setImageBitmap(bitmap);
        pageInfo.setText((index + 1) + " / " + pdfRenderer.getPageCount());
    }

    private void saveHighlight(String color) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Not ekle (isteğe bağlı)");
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Bu sayfa için notun...");
        builder.setView(input);
        builder.setPositiveButton("Kaydet", (d, w) -> {
            ContentValues values = new ContentValues();
            values.put("pdf_uri", pdfUri);
            values.put("page", currentPage);
            values.put("color", color);
            values.put("note", input.getText().toString());
            db.insert("highlights", null, values);
            Toast.makeText(this, "Kaydedildi!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("İptal", null);
        builder.show();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() {
            super(PdfViewerActivity.this, "goblith.db", null, 1);
        }
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int o, int n) {}
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
