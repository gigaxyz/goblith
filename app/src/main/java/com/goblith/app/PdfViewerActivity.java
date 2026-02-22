package com.goblith.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PdfViewerActivity extends AppCompatActivity {

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private int currentPage = 0;
    private int totalPages = 1;
    private ImageView pageView;
    private HighlightOverlay highlightOverlay;
    private TextView pageInfo;
    private TextView txtContent;
    private SQLiteDatabase db;
    private String pdfUri;
    private String fileType;

    // Zoom + Pan
    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private float lastTouchX, lastTouchY;
    private static final int TOUCH_NONE=0, TOUCH_DRAG=1, TOUCH_ZOOM=2;
    private int touchMode = TOUCH_NONE;
    private static final float MIN_ZOOM=1.0f, MAX_ZOOM=5.0f;

    // Highlight modu
    private boolean highlightMode = false;
    private String highlightColor = "yellow";
    private Button btnHighlightToggle;
    private LinearLayout highlightColorBar;
    private int imgWidth = 1, imgHeight = 1;

    // Hızlı geçiş
    private Handler fastScrollHandler = new Handler();
    private boolean isFastScrolling = false;

    private static final List<String> TAGS = Arrays.asList(
        "Felsefe","Ekonomi","Strateji","Bilim","Tarih","Psikoloji","Diger"
    );

    // ————— HIGHLIGHT OVERLAY VIEW —————
    class HighlightOverlay extends View {
        private List<float[]> highlights = new ArrayList<>();
        private Paint paint = new Paint();
        private float startX, startY, endX, endY;
        private boolean drawing = false;
        private int viewW, viewH;

        HighlightOverlay(Context ctx) {
            super(ctx);
            setBackgroundColor(Color.TRANSPARENT);
        }

        void setSize(int w, int h) { viewW=w; viewH=h; }

        void loadHighlights(List<float[]> list) {
            highlights = new ArrayList<>(list);
            invalidate();
        }

        void startDraw(float x, float y) {
            startX=x; startY=y; endX=x; endY=y; drawing=true;
        }

        void updateDraw(float x, float y) {
            endX=x; endY=y; invalidate();
        }

        float[] finishDraw() {
            drawing=false;
            float x1=Math.min(startX,endX), y1=Math.min(startY,endY);
            float x2=Math.max(startX,endX), y2=Math.max(startY,endY);
            if (Math.abs(x2-x1)<20 || Math.abs(y2-y1)<10) { invalidate(); return null; }
            float[] rect = {x1/getWidth(), y1/getHeight(), x2/getWidth(), y2/getHeight()};
            highlights.add(new float[]{rect[0],rect[1],rect[2],rect[3],colorToFloat(highlightColor)});
            invalidate();
            return rect;
        }

        void removeHighlight(float tx, float ty) {
            float nx = tx/getWidth(), ny = ty/getHeight();
            for (int i=highlights.size()-1; i>=0; i--) {
                float[] h = highlights.get(i);
                if (nx>=h[0] && nx<=h[2] && ny>=h[1] && ny<=h[3]) {
                    highlights.remove(i);
                    invalidate();
                    return;
                }
            }
        }

        private float colorToFloat(String c) {
            switch(c) {
                case "yellow": return 0;
                case "red": return 1;
                case "blue": return 2;
                case "green": return 3;
                default: return 0;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            // Kaydedilmiş highlight'lar
            for (float[] hl : highlights) {
                paint.reset();
                paint.setStyle(Paint.Style.FILL);
                switch((int)hl[4]) {
                    case 0: paint.setColor(0x88FFE066); break;
                    case 1: paint.setColor(0x88FF4560); break;
                    case 2: paint.setColor(0x884488FF); break;
                    case 3: paint.setColor(0x8844CC66); break;
                    default: paint.setColor(0x88FFE066);
                }
                canvas.drawRect(hl[0]*w, hl[1]*h, hl[2]*w, hl[3]*h, paint);
                // Kenar çizgisi
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setAlpha(180);
                canvas.drawRect(hl[0]*w, hl[1]*h, hl[2]*w, hl[3]*h, paint);
            }
            // Çizim yapılıyor
            if (drawing) {
                paint.reset();
                paint.setStyle(Paint.Style.FILL);
                switch(highlightColor) {
                    case "yellow": paint.setColor(0x66FFE066); break;
                    case "red":    paint.setColor(0x66FF4560); break;
                    case "blue":   paint.setColor(0x664488FF); break;
                    case "green":  paint.setColor(0x6644CC66); break;
                }
                float x1=Math.min(startX,endX), y1=Math.min(startY,endY);
                float x2=Math.max(startX,endX), y2=Math.max(startY,endY);
                canvas.drawRect(x1,y1,x2,y2,paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setColor(0xFFFFFFFF);
                canvas.drawRect(x1,y1,x2,y2,paint);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();
        pdfUri = getIntent().getStringExtra("pdfUri");
        int startPage = getIntent().getIntExtra("startPage", 0);
        fileType = getIntent().getStringExtra("fileType");
        if (fileType==null) fileType="PDF";

        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Üst bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF0F3460);
        topBar.setPadding(8,8,8,8);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button btnPrev = makeTopBtn("  <  ", 0xFF1A3A6A);
        pageInfo = new TextView(this);
        pageInfo.setTextColor(0xFFFFFFFF);
        pageInfo.setTextSize(13);
        pageInfo.setGravity(Gravity.CENTER);
        pageInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button btnGo = makeTopBtn("Git", 0xFFE94560);
        Button btnNext = makeTopBtn("  >  ", 0xFF1A3A6A);

        topBar.addView(btnPrev);
        topBar.addView(pageInfo);
        topBar.addView(btnGo);
        topBar.addView(btnNext);

        // Highlight araç çubuğu
        highlightColorBar = new LinearLayout(this);
        highlightColorBar.setOrientation(LinearLayout.HORIZONTAL);
        highlightColorBar.setBackgroundColor(0xFF0A1628);
        highlightColorBar.setPadding(8,6,8,6);
        highlightColorBar.setVisibility(View.GONE);

        btnHighlightToggle = new Button(this);
        btnHighlightToggle.setText("ISARETLE");
        btnHighlightToggle.setBackgroundColor(0xFF6A1B9A);
        btnHighlightToggle.setTextColor(0xFFFFFFFF);
        btnHighlightToggle.setTextSize(11);
        btnHighlightToggle.setTypeface(null, android.graphics.Typeface.BOLD);
        btnHighlightToggle.setPadding(16,6,16,6);
        highlightColorBar.addView(btnHighlightToggle);

        // Renk butonları
        String[] hlColors = {"Sari","Kirmizi","Mavi","Yesil"};
        int[] hlColorVals = {0xFFFFE066, 0xFFFF4560, 0xFF4488FF, 0xFF44CC66};
        String[] hlColorKeys = {"yellow","red","blue","green"};

        for (int i=0; i<hlColors.length; i++) {
            Button cb = new Button(this);
            cb.setText(hlColors[i]);
            cb.setBackgroundColor(hlColorVals[i]);
            cb.setTextColor(0xFF000000);
            cb.setTextSize(10);
            cb.setPadding(12,6,12,6);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(6,0,0,0);
            cb.setLayoutParams(cp);
            final String key = hlColorKeys[i];
            cb.setOnClickListener(v -> { highlightColor=key; });
            highlightColorBar.addView(cb);
        }

        Button btnHlDelete = new Button(this);
        btnHlDelete.setText("SIL");
        btnHlDelete.setBackgroundColor(0xFF333333);
        btnHlDelete.setTextColor(0xFFFFFFFF);
        btnHlDelete.setTextSize(10);
        btnHlDelete.setPadding(12,6,12,6);
        LinearLayout.LayoutParams delP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        delP.setMargins(6,0,0,0);
        btnHlDelete.setLayoutParams(delP);
        highlightColorBar.addView(btnHlDelete);

        // İçerik alanı
        FrameLayout contentArea = new FrameLayout(this);
        contentArea.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        contentArea.setBackgroundColor(0xFF333333);

        if (fileType.equals("TXT")) {
            ScrollView sv = new ScrollView(this);
            txtContent = new TextView(this);
            txtContent.setTextColor(0xFF111111);
            txtContent.setBackgroundColor(Color.WHITE);
            txtContent.setTextSize(15);
            txtContent.setPadding(24,24,24,24);
            txtContent.setLineSpacing(4, 1.3f);
            sv.addView(txtContent);
            sv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            contentArea.addView(sv);
            topBar.setVisibility(View.GONE);
        } else {
            pageView = new ImageView(this);
            pageView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            pageView.setScaleType(ImageView.ScaleType.MATRIX);
            pageView.setBackgroundColor(0xFF333333);
            contentArea.addView(pageView);

            highlightOverlay = new HighlightOverlay(this);
            highlightOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            contentArea.addView(highlightOverlay);

            setupTouch();
        }

        // Alt bar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF0F3460);
        bottomBar.setPadding(8,6,8,6);

        Button btnMark = new Button(this);
        btnMark.setText("ISARETLEME");
        btnMark.setBackgroundColor(0xFF6A1B9A);
        btnMark.setTextColor(0xFFFFFFFF);
        btnMark.setTypeface(null, android.graphics.Typeface.BOLD);
        btnMark.setTextSize(11);
        btnMark.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnRed = new Button(this);
        btnRed.setText("ITIRAZ");
        btnRed.setBackgroundColor(0xFFE94560);
        btnRed.setTextColor(0xFFFFFFFF);
        btnRed.setTypeface(null, android.graphics.Typeface.BOLD);
        btnRed.setTextSize(11);
        btnRed.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnBlue = new Button(this);
        btnBlue.setText("ARGUMAN");
        btnBlue.setBackgroundColor(0xFF1565C0);
        btnBlue.setTextColor(0xFFFFFFFF);
        btnBlue.setTypeface(null, android.graphics.Typeface.BOLD);
        btnBlue.setTextSize(11);
        btnBlue.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button btnGreen = new Button(this);
        btnGreen.setText("VERI");
        btnGreen.setBackgroundColor(0xFF2E7D32);
        btnGreen.setTextColor(0xFFFFFFFF);
        btnGreen.setTypeface(null, android.graphics.Typeface.BOLD);
        btnGreen.setTextSize(11);
        btnGreen.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        bottomBar.addView(btnMark);
        bottomBar.addView(btnRed);
        bottomBar.addView(btnBlue);
        bottomBar.addView(btnGreen);

        root.addView(topBar);
        root.addView(highlightColorBar);
        root.addView(contentArea);
        root.addView(bottomBar);
        setContentView(root);

        if (fileType.equals("TXT")) openTextFile();
        else openPdfFile(startPage);

        // Buton olayları
        btnPrev.setOnClickListener(v -> { if (currentPage>0) showPage(currentPage-1); });
        btnNext.setOnClickListener(v -> { if (currentPage<totalPages-1) showPage(currentPage+1); });

        Runnable fastPrev = new Runnable() {
            @Override public void run() {
                if (isFastScrolling && currentPage>0) { showPage(currentPage-1); fastScrollHandler.postDelayed(this,120); }
            }
        };
        Runnable fastNext = new Runnable() {
            @Override public void run() {
                if (isFastScrolling && currentPage<totalPages-1) { showPage(currentPage+1); fastScrollHandler.postDelayed(this,120); }
            }
        };

        btnPrev.setOnLongClickListener(v -> { isFastScrolling=true; fastScrollHandler.post(fastPrev); return true; });
        btnNext.setOnLongClickListener(v -> { isFastScrolling=true; fastScrollHandler.post(fastNext); return true; });
        btnPrev.setOnTouchListener((v,e) -> { if (e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) isFastScrolling=false; return false; });
        btnNext.setOnTouchListener((v,e) -> { if (e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) isFastScrolling=false; return false; });

        btnGo.setOnClickListener(v -> {
            if (fileType.equals("TXT")) return;
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Sayfaya Git");
            EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setHint("1 - " + totalPages);
            b.setView(input);
            b.setPositiveButton("Git", (d,w) -> {
                try {
                    int p = Integer.parseInt(input.getText().toString())-1;
                    if (p>=0&&p<totalPages) showPage(p);
                    else Toast.makeText(this,"Gecersiz sayfa",Toast.LENGTH_SHORT).show();
                } catch (Exception e) { Toast.makeText(this,"Sayi gir",Toast.LENGTH_SHORT).show(); }
            });
            b.setNegativeButton("Iptal",null);
            b.show();
        });

        // İşaretleme modu toggle
        btnMark.setOnClickListener(v -> {
            highlightMode = !highlightMode;
            highlightColorBar.setVisibility(highlightMode ? View.VISIBLE : View.GONE);
            btnMark.setBackgroundColor(highlightMode ? 0xFFE94560 : 0xFF6A1B9A);
            btnMark.setText(highlightMode ? "BITTI" : "ISARETLEME");
        });

        // Highlight sil butonu
        btnHlDelete.setOnClickListener(v -> {
            Toast.makeText(this, "Silmek icin isarete dokun", Toast.LENGTH_SHORT).show();
        });

        // Not butonları
        btnRed.setOnClickListener(v -> saveHighlight("red","ITIRAZ"));
        btnBlue.setOnClickListener(v -> saveHighlight("blue","ARGUMAN"));
        btnGreen.setOnClickListener(v -> saveHighlight("green","VERI"));
    }

    private Button makeTopBtn(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(16);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setPadding(16,4,16,4);
        return btn;
    }

    private void setupTouch() {
        // Overlay touch — highlight modu
        highlightOverlay.setOnTouchListener((v, event) -> {
            if (!highlightMode) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    highlightOverlay.startDraw(event.getX(), event.getY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    highlightOverlay.updateDraw(event.getX(), event.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                    float[] rect = highlightOverlay.finishDraw();
                    if (rect != null) {
                        // Kaydet
                        ContentValues values = new ContentValues();
                        values.put("pdf_uri", pdfUri);
                        values.put("page", currentPage);
                        values.put("x1", rect[0]);
                        values.put("y1", rect[1]);
                        values.put("x2", rect[2]);
                        values.put("y2", rect[3]);
                        values.put("color", highlightColor);
                        db.insert("page_highlights", null, values);
                        Toast.makeText(this, "Isaretlendi", Toast.LENGTH_SHORT).show();
                    }
                    return true;
            }
            return false;
        });

        // PageView touch — zoom+pan modu
        pageView.setOnTouchListener((v, event) -> {
            if (highlightMode) return false;
            scaleDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix);
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    touchMode = TOUCH_DRAG;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    touchMode = TOUCH_ZOOM;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode==TOUCH_DRAG && !scaleDetector.isInProgress()) {
                        float dx = event.getX()-lastTouchX;
                        float dy = event.getY()-lastTouchY;
                        matrix.set(savedMatrix);
                        matrix.postTranslate(dx, dy);
                        clampMatrix();
                        pageView.setImageMatrix(matrix);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    touchMode = TOUCH_NONE;
                    break;
            }
            return true;
        });
    }

    private void clampMatrix() {
        if (pageView.getDrawable()==null) return;
        float[] values = new float[9];
        matrix.getValues(values);
        float scaleX = values[Matrix.MSCALE_X];
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];

        int viewW = pageView.getWidth();
        int viewH = pageView.getHeight();
        float scaledW = imgWidth * scaleX;
        float scaledH = imgHeight * scaleX;

        float minTX, maxTX, minTY, maxTY;

        if (scaledW <= viewW) {
            minTX = maxTX = (viewW - scaledW) / 2f;
        } else {
            minTX = viewW - scaledW;
            maxTX = 0;
        }

        if (scaledH <= viewH) {
            minTY = maxTY = (viewH - scaledH) / 2f;
        } else {
            minTY = viewH - scaledH;
            maxTY = 0;
        }

        transX = Math.max(minTX, Math.min(transX, maxTX));
        transY = Math.max(minTY, Math.min(transY, maxTY));

        values[Matrix.MTRANS_X] = transX;
        values[Matrix.MTRANS_Y] = transY;
        matrix.setValues(values);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector detector) {
            float[] values = new float[9];
            matrix.getValues(values);
            float currentScale = values[Matrix.MSCALE_X];
            float newScale = Math.max(MIN_ZOOM, Math.min(currentScale*detector.getScaleFactor(), MAX_ZOOM));
            float realScale = newScale/currentScale;
            matrix.postScale(realScale, realScale, detector.getFocusX(), detector.getFocusY());
            clampMatrix();
            pageView.setImageMatrix(matrix);
            return true;
        }
    }

    private void openPdfFile(int startPage) {
        try {
            fileDescriptor = getContentResolver().openFileDescriptor(Uri.parse(pdfUri),"r");
            if (fileDescriptor==null) { Toast.makeText(this,"Dosya acilamadi",Toast.LENGTH_LONG).show(); finish(); return; }
            pdfRenderer = new PdfRenderer(fileDescriptor);
            totalPages = pdfRenderer.getPageCount();
            showPage(Math.min(startPage, totalPages-1));
        } catch (Exception e) { Toast.makeText(this,"PDF hatasi: "+e.getMessage(),Toast.LENGTH_LONG).show(); finish(); }
    }

    private void openTextFile() {
        try {
            InputStream is = getContentResolver().openInputStream(Uri.parse(pdfUri));
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line=reader.readLine())!=null) sb.append(line).append("\n");
            reader.close();
            txtContent.setText(sb.toString());
        } catch (Exception e) { Toast.makeText(this,"Dosya okunamadi",Toast.LENGTH_LONG).show(); }
    }

    private void showPage(int index) {
        if (pdfRenderer==null) return;
        currentPage = index;
        matrix.reset();
        pageView.setImageMatrix(matrix);

        try {
            PdfRenderer.Page page = pdfRenderer.openPage(index);
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = (int)((float)page.getHeight()/page.getWidth()*w);
            imgWidth=w; imgHeight=h;
            Bitmap bitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            pageView.setImageBitmap(bitmap);
            pageInfo.setText((index+1)+" / "+totalPages);

            // Highlight'ları yükle
            loadPageHighlights(index);

            ContentValues values = new ContentValues();
            values.put("pdf_uri",pdfUri);
            values.put("last_page",index);
            values.put("last_opened",new java.util.Date().toString());
            db.insertWithOnConflict("library",null,values,SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) { Toast.makeText(this,"Sayfa yuklenemedi",Toast.LENGTH_SHORT).show(); }
    }

    private void loadPageHighlights(int page) {
        if (highlightOverlay==null) return;
        List<float[]> list = new ArrayList<>();
        Cursor c = db.rawQuery(
            "SELECT x1,y1,x2,y2,color FROM page_highlights WHERE pdf_uri=? AND page=?",
            new String[]{pdfUri, String.valueOf(page)});
        while (c.moveToNext()) {
            float x1=c.getFloat(0), y1=c.getFloat(1), x2=c.getFloat(2), y2=c.getFloat(3);
            String color = c.getString(4);
            float colorFloat;
            switch(color!=null?color:"yellow") {
                case "red": colorFloat=1; break;
                case "blue": colorFloat=2; break;
                case "green": colorFloat=3; break;
                default: colorFloat=0;
            }
            list.add(new float[]{x1,y1,x2,y2,colorFloat});
        }
        c.close();
        highlightOverlay.loadHighlights(list);
    }

    private void saveHighlight(String color, String label) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(label+" — Sayfa "+(currentPage+1));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32,16,32,8);

        EditText input = new EditText(this);
        input.setHint("Notunu yaz...");
        input.setMinLines(2);
        layout.addView(input);

        TextView tagLabel = new TextView(this);
        tagLabel.setText("Etiket (istege bagli):");
        tagLabel.setTextColor(0xFF888888);
        tagLabel.setTextSize(12);
        tagLabel.setPadding(0,16,0,8);
        layout.addView(tagLabel);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout tagRow = new LinearLayout(this);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] selTag = {""};
        Button[] tagBtns = new Button[TAGS.size()];
        for (int i=0; i<TAGS.size(); i++) {
            String t = TAGS.get(i);
            Button tb = new Button(this);
            tb.setText(t);
            tb.setTextSize(10);
            tb.setTextColor(0xFFFFFFFF);
            tb.setBackgroundColor(0x446A1B9A);
            tb.setPadding(16,6,16,6);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(4,0,4,0);
            tb.setLayoutParams(tp);
            tagBtns[i]=tb;
            tagRow.addView(tb);
            final int idx=i;
            tb.setOnClickListener(cv -> {
                selTag[0]=t;
                for (int j=0;j<tagBtns.length;j++) tagBtns[j].setBackgroundColor(j==idx?0xFF6A1B9A:0x446A1B9A);
            });
        }
        hs.addView(tagRow);
        layout.addView(hs);
        b.setView(layout);
        b.setPositiveButton("Kaydet",(d,w) -> {
            ContentValues values = new ContentValues();
            values.put("pdf_uri",pdfUri);
            values.put("page",currentPage);
            values.put("color",color);
            values.put("note",input.getText().toString());
            values.put("tag",selTag[0]);
            db.insert("highlights",null,values);
            Toast.makeText(this,"Kaydedildi",Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("Iptal",null);
        b.show();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(PdfViewerActivity.this,"goblith.db",null,5); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try { db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception e) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)"); } catch (Exception e) {}
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        isFastScrolling=false;
        fastScrollHandler.removeCallbacksAndMessages(null);
        try {
            if (pdfRenderer!=null) pdfRenderer.close();
            if (fileDescriptor!=null) fileDescriptor.close();
        } catch (IOException e) { e.printStackTrace(); }
    }
}
