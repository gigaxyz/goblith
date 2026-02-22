package com.goblith.app;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class PdfViewerActivity extends AppCompatActivity {

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private int currentPage = 0;
    private ImageView pageView;
    private TextView pageInfo;
    private TextView txtContent;
    private SQLiteDatabase db;
    private String pdfUri;
    private String fileType;
    private ScrollView scrollView;
    private ScaleGestureDetector scaleDetector;
    private float scaleFactor = 1.0f;
    private Matrix matrix = new Matrix();
    private Handler fastScrollHandler = new Handler();
    private boolean isFastScrolling = false;
    private int totalPages = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DBHelper dbHelper = new DBHelper();
        db = dbHelper.getWritableDatabase();

        pdfUri = getIntent().getStringExtra("pdfUri");
        int startPage = getIntent().getIntExtra("startPage", 0);
        fileType = getIntent().getStringExtra("fileType");
        if (fileType == null) fileType = "PDF";

        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Üst bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF0F3460);
        topBar.setPadding(8, 8, 8, 8);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button btnPrev = new Button(this);
        btnPrev.setText("  <  ");
        btnPrev.setBackgroundColor(0xFF1A3A6A);
        btnPrev.setTextColor(0xFFFFFFFF);
        btnPrev.setTextSize(18);
        btnPrev.setTypeface(null, android.graphics.Typeface.BOLD);

        pageInfo = new TextView(this);
        pageInfo.setTextColor(0xFFFFFFFF);
        pageInfo.setTextSize(13);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        pageInfo.setLayoutParams(infoParams);
        pageInfo.setGravity(Gravity.CENTER);

        Button btnGo = new Button(this);
        btnGo.setText("Git");
        btnGo.setBackgroundColor(0xFFE94560);
        btnGo.setTextColor(0xFFFFFFFF);
        btnGo.setTextSize(11);
        btnGo.setPadding(16, 4, 16, 4);

        Button btnNext = new Button(this);
        btnNext.setText("  >  ");
        btnNext.setBackgroundColor(0xFF1A3A6A);
        btnNext.setTextColor(0xFFFFFFFF);
        btnNext.setTextSize(18);
        btnNext.setTypeface(null, android.graphics.Typeface.BOLD);

        topBar.addView(btnPrev);
        topBar.addView(pageInfo);
        topBar.addView(btnGo);
        topBar.addView(btnNext);

        // İçerik alanı
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF2A2A2A);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        if (fileType.equals("TXT")) {
            // Metin görüntüleyici
            txtContent = new TextView(this);
            txtContent.setTextColor(0xFF000000);
            txtContent.setBackgroundColor(Color.WHITE);
            txtContent.setTextSize(15);
            txtContent.setPadding(24, 24, 24, 24);
            txtContent.setLineSpacing(4, 1.3f);
            scrollView.addView(txtContent);
            topBar.setVisibility(android.view.View.GONE);
        } else {
            // PDF görüntüleyici
            pageView = new ImageView(this);
            pageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams pvParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pageView.setLayoutParams(pvParams);
            scrollView.addView(pageView);
        }

        // Alt butonlar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF0F3460);
        bottomBar.setPadding(8, 6, 8, 6);

        Button btnRed = new Button(this);
        btnRed.setText("ITIRAZ");
        btnRed.setBackgroundColor(0xFFE94560);
        btnRed.setTextColor(0xFFFFFFFF);
        btnRed.setTypeface(null, android.graphics.Typeface.BOLD);
        btnRed.setTextSize(12);
        btnRed.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnBlue = new Button(this);
        btnBlue.setText("ARGUMAN");
        btnBlue.setBackgroundColor(0xFF1565C0);
        btnBlue.setTextColor(0xFFFFFFFF);
        btnBlue.setTypeface(null, android.graphics.Typeface.BOLD);
        btnBlue.setTextSize(12);
        btnBlue.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnGreen = new Button(this);
        btnGreen.setText("VERI");
        btnGreen.setBackgroundColor(0xFF2E7D32);
        btnGreen.setTextColor(0xFFFFFFFF);
        btnGreen.setTypeface(null, android.graphics.Typeface.BOLD);
        btnGreen.setTextSize(12);
        btnGreen.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bottomBar.addView(btnRed);
        bottomBar.addView(btnBlue);
        bottomBar.addView(btnGreen);

        root.addView(topBar);
        root.addView(scrollView);
        root.addView(bottomBar);
        setContentView(root);

        // Dosyayı aç
        if (fileType.equals("TXT")) {
            openTextFile();
        } else {
            openPdfFile(startPage);
            setupZoom();
        }

        // Sayfa geçiş butonları
        btnPrev.setOnClickListener(v -> { if (currentPage > 0) showPage(currentPage - 1); });
        btnNext.setOnClickListener(v -> { if (currentPage < totalPages - 1) showPage(currentPage + 1); });

        Runnable fastPrev = new Runnable() {
            @Override public void run() {
                if (isFastScrolling && currentPage > 0) {
                    showPage(currentPage - 1);
                    fastScrollHandler.postDelayed(this, 120);
                }
            }
        };
        Runnable fastNext = new Runnable() {
            @Override public void run() {
                if (isFastScrolling && currentPage < totalPages - 1) {
                    showPage(currentPage + 1);
                    fastScrollHandler.postDelayed(this, 120);
                }
            }
        };

        btnPrev.setOnLongClickListener(v -> { isFastScrolling = true; fastScrollHandler.post(fastPrev); return true; });
        btnNext.setOnLongClickListener(v -> { isFastScrolling = true; fastScrollHandler.post(fastNext); return true; });

        btnPrev.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) isFastScrolling = false;
            return false;
        });
        btnNext.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) isFastScrolling = false;
            return false;
        });

        btnGo.setOnClickListener(v -> {
            if (fileType.equals("TXT")) return;
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Sayfaya Git");
            EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setHint("1 - " + totalPages);
            b.setView(input);
            b.setPositiveButton("Git", (d, w) -> {
                try {
                    int p = Integer.parseInt(input.getText().toString()) - 1;
                    if (p >= 0 && p < totalPages) showPage(p);
                    else Toast.makeText(this, "Gecersiz sayfa", Toast.LENGTH_SHORT).show();
                } catch (Exception e) { Toast.makeText(this, "Sayi gir", Toast.LENGTH_SHORT).show(); }
            });
            b.setNegativeButton("Iptal", null);
            b.show();
        });

        btnRed.setOnClickListener(v -> saveHighlight("red", "ITIRAZ"));
        btnBlue.setOnClickListener(v -> saveHighlight("blue", "ARGUMAN"));
        btnGreen.setOnClickListener(v -> saveHighlight("green", "VERI"));
    }

    private void openPdfFile(int startPage) {
        try {
            Uri uri = Uri.parse(pdfUri);
            fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            if (fileDescriptor == null) {
                Toast.makeText(this, "Dosya acilamadi", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            pdfRenderer = new PdfRenderer(fileDescriptor);
            totalPages = pdfRenderer.getPageCount();
            showPage(Math.min(startPage, totalPages - 1));
        } catch (Exception e) {
            Toast.makeText(this, "PDF hatasi: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void openTextFile() {
        try {
            InputStream is = getContentResolver().openInputStream(Uri.parse(pdfUri));
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            txtContent.setText(sb.toString());
            pageInfo.setText("TXT");
        } catch (Exception e) {
            Toast.makeText(this, "Dosya okunamadi", Toast.LENGTH_LONG).show();
        }
    }

    private void setupZoom() {
        if (pageView != null) {
            pageView.setOnTouchListener((v, event) -> {
                scaleDetector.onTouchEvent(event);
                return true;
            });
        }
    }

    private void showPage(int index) {
        if (pdfRenderer == null) return;
        currentPage = index;
        scaleFactor = 1.0f;
        matrix.reset();

        try {
            PdfRenderer.Page page = pdfRenderer.openPage(index);
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int w = screenWidth;
            int h = (int) ((float) page.getHeight() / page.getWidth() * w);

            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();

            pageView.setImageBitmap(bitmap);
            scrollView.scrollTo(0, 0);
            pageInfo.setText((index + 1) + " / " + totalPages);

            saveProgress(index);
        } catch (Exception e) {
            Toast.makeText(this, "Sayfa yuklenemedi", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveProgress(int page) {
        ContentValues values = new ContentValues();
        values.put("pdf_uri", pdfUri);
        values.put("last_page", page);
        values.put("last_opened", new java.util.Date().toString());
        db.insertWithOnConflict("library", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (pageView == null) return false;
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.8f, Math.min(scaleFactor, 4.0f));
            pageView.setScaleX(scaleFactor);
            pageView.setScaleY(scaleFactor);
            return true;
        }
    }

    private void saveHighlight(String color, String label) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(label + " — Sayfa " + (currentPage + 1));
        EditText input = new EditText(this);
        input.setHint("Notunu yaz...");
        b.setView(input);
        b.setPositiveButton("Kaydet", (d, w) -> {
            ContentValues values = new ContentValues();
            values.put("pdf_uri", pdfUri);
            values.put("page", currentPage);
            values.put("color", color);
            values.put("note", input.getText().toString());
            db.insert("highlights", null, values);
            Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("Iptal", null);
        b.show();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(PdfViewerActivity.this, "goblith.db", null, 3); }
        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception e) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isFastScrolling = false;
        fastScrollHandler.removeCallbacksAndMessages(null);
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) fileDescriptor.close();
        } catch (IOException e) { e.printStackTrace(); }
    }
}
