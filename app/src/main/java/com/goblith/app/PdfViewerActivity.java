package com.goblith.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
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
import androidx.core.content.ContextCompat;
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
    private int currentPage = 0, totalPages = 1;
    private ImageView pageView;
    private HighlightOverlay highlightOverlay;
    private TextView pageInfo;
    private TextView txtContent;
    private SQLiteDatabase db;
    private String pdfUri, fileType;
    private int imgWidth = 1, imgHeight = 1;

    // Zoom + pan
    private Matrix matrix = new Matrix(), savedMatrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private float lastTouchX, lastTouchY;
    private static final int TOUCH_NONE=0, TOUCH_DRAG=1, TOUCH_ZOOM=2;
    private int touchMode = TOUCH_NONE;
    private static final float MIN_ZOOM=1f, MAX_ZOOM=5f;

    private boolean highlightMode=false, deleteMode=false;
    private String highlightColor="yellow";
    private LinearLayout highlightToolbar;
    private Button btnMark;

    private boolean nightMode=false, sepiaMode=false;

    private Handler fastScrollHandler = new Handler();
    private boolean isFastScrolling = false;

    private static final List<String> DEFAULT_TAGS =
        Arrays.asList("Felsefe","Ekonomi","Strateji","Bilim","Tarih","Psikoloji","Diger");

    // ─── Highlight Overlay ───────────────────────────────────────────────────
    class HighlightOverlay extends View {
        // Koordinatlar PDF sayfa uzayında normalize (0-1)
        private List<long[]>  hlIds   = new ArrayList<>();
        private List<float[]> hlRects = new ArrayList<>(); // {x1,y1,x2,y2,colorFloat}
        private Paint paint = new Paint();
        // Çizim sırasında geçici — sayfa uzayında normalize
        private float drawX1, drawY1, drawX2, drawY2;
        private boolean drawing = false;

        // Matrix referansı (PdfViewerActivity'den gelecek)
        Matrix viewMatrix = new Matrix();
        int imgW = 1, imgH = 1;

        HighlightOverlay(Context ctx) {
            super(ctx);
            setBackgroundColor(Color.TRANSPARENT);
        }

        void loadData(List<long[]> ids, List<float[]> rects) {
            hlIds   = new ArrayList<>(ids);
            hlRects = new ArrayList<>(rects);
            drawing = false;
            invalidate();
        }

        // touch koordinatı (ekran) → sayfa normalize
        private float[] screenToPage(float sx, float sy) {
            Matrix inv = new Matrix();
            viewMatrix.invert(inv);
            float[] pt = {sx, sy};
            inv.mapPoints(pt);
            return new float[]{pt[0] / imgW, pt[1] / imgH};
        }

        void startDraw(float sx, float sy) {
            float[] p = screenToPage(sx, sy);
            drawX1 = p[0]; drawY1 = p[1];
            drawX2 = p[0]; drawY2 = p[1];
            drawing = true; invalidate();
        }

        void updateDraw(float sx, float sy) {
            float[] p = screenToPage(sx, sy);
            drawX2 = p[0]; drawY2 = p[1];
            invalidate();
        }

        float[] finishDraw() {
            drawing = false; invalidate();
            float x1=Math.min(drawX1,drawX2), y1=Math.min(drawY1,drawY2);
            float x2=Math.max(drawX1,drawX2), y2=Math.max(drawY1,drawY2);
            // Minimum boyut: sayfa uzayında ~0.01
            if ((x2-x1)<0.01f || (y2-y1)<0.005f) return null;
            return new float[]{x1,y1,x2,y2};
        }

        long undoLast() {
            if (hlIds.isEmpty()) return -1;
            long id = hlIds.get(hlIds.size()-1)[0];
            hlIds.remove(hlIds.size()-1);
            hlRects.remove(hlRects.size()-1);
            invalidate(); return id;
        }

        // touch ekran koordinatıyla eşleşen highlight'ı bul
        long removeTouched(float sx, float sy) {
            float[] p = screenToPage(sx, sy);
            float nx=p[0], ny=p[1];
            for (int i=hlRects.size()-1; i>=0; i--) {
                float[] h = hlRects.get(i);
                if (nx>=h[0] && nx<=h[2] && ny>=h[1] && ny<=h[3]) {
                    long id = hlIds.get(i)[0];
                    hlIds.remove(i); hlRects.remove(i);
                    invalidate(); return id;
                }
            }
            return -1;
        }

        // Sayfa normalize → ekran koordinatı (matrix uygula)
        private RectF pageToScreen(float x1, float y1, float x2, float y2) {
            float[] pts = {x1*imgW, y1*imgH, x2*imgW, y2*imgH};
            viewMatrix.mapPoints(pts);
            return new RectF(pts[0], pts[1], pts[2], pts[3]);
        }

        private int fill(float f) {
            switch ((int)f) {
                case 1: return 0x50FF4560;
                case 2: return 0x504488FF;
                case 3: return 0x5044CC66;
                default: return 0x50FFE500;
            }
        }
        private int stroke(float f) {
            switch ((int)f) {
                case 1: return 0x99FF4560;
                case 2: return 0x994488FF;
                case 3: return 0x9944CC66;
                default: return 0x99DDB800;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Kaydedilmiş highlight'lar — matrix dönüşümü uygulanır
            for (float[] hl : hlRects) {
                RectF r = pageToScreen(hl[0], hl[1], hl[2], hl[3]);
                paint.reset();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fill(hl[4]));
                canvas.drawRect(r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f);
                paint.setColor(stroke(hl[4]));
                canvas.drawRect(r, paint);
            }
            // Çizim yapılıyor
            if (drawing) {
                RectF r = pageToScreen(
                    Math.min(drawX1,drawX2), Math.min(drawY1,drawY2),
                    Math.max(drawX1,drawX2), Math.max(drawY1,drawY2));
                paint.reset();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x44FFE500);
                canvas.drawRect(r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setColor(0xCCFFFFFF);
                canvas.drawRect(r, paint);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();
        pdfUri    = getIntent().getStringExtra("pdfUri");
        int startPage = getIntent().getIntExtra("startPage", 0);
        fileType  = getIntent().getStringExtra("fileType");
        if (fileType == null) fileType = "PDF";

        scaleDetector = new ScaleGestureDetector(this, new ScaleListener());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // ── ÜST BAR ─────────────────────────────────────────────────────────
        // Satır 1: ◀  sayfa bilgisi  ▶
        LinearLayout topBar1 = new LinearLayout(this);
        topBar1.setOrientation(LinearLayout.HORIZONTAL);
        topBar1.setBackgroundColor(0xFF0F3460);
        topBar1.setPadding(8, 6, 8, 4);
        topBar1.setGravity(Gravity.CENTER_VERTICAL);

        Button btnPrev = makeNavBtn("◀");
        pageInfo = new TextView(this);
        pageInfo.setTextColor(0xFFFFFFFF);
        pageInfo.setTextSize(14);
        pageInfo.setGravity(Gravity.CENTER);
        pageInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button btnNext = makeNavBtn("▶");

        topBar1.addView(btnPrev);
        topBar1.addView(pageInfo);
        topBar1.addView(btnNext);

        // Satır 2: Git | ☆ Yer imi | 🌙 Gece
        LinearLayout topBar2 = new LinearLayout(this);
        topBar2.setOrientation(LinearLayout.HORIZONTAL);
        topBar2.setBackgroundColor(0xFF0A2040);
        topBar2.setPadding(8, 4, 8, 6);

        Button btnGo       = makeSmallTopBtn("GİT",        0xFF1A3A6A);
        Button btnBookmark = makeSmallTopBtn("☆ YER İMİ",  0xFF1A3A6A);
        Button btnNight    = makeSmallTopBtn("🌙 GECE",    0xFF1A3A6A);

        topBar2.addView(withFlex(btnGo, 0));
        topBar2.addView(withFlex(btnBookmark, 8));
        topBar2.addView(withFlex(btnNight, 8));

        // ── HIGHLIGHT TOOLBAR ────────────────────────────────────────────────
        highlightToolbar = new LinearLayout(this);
        highlightToolbar.setOrientation(LinearLayout.VERTICAL);
        highlightToolbar.setBackgroundColor(0xFF0A1628);
        highlightToolbar.setVisibility(View.GONE);

        // Renk satırı
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(8, 8, 8, 4);

        String[] hlNames = {"Sarı","Kırm.","Mavi","Yeşil"};
        int[]    hlVals  = {0xFFFFE500, 0xFFFF4560, 0xFF4488FF, 0xFF44CC66};
        String[] hlKeys  = {"yellow","red","blue","green"};
        Button[] colorBtns = new Button[4];
        for (int i=0; i<4; i++) {
            Button cb = new Button(this);
            cb.setText(hlNames[i]);
            cb.setBackgroundColor(hlVals[i]);
            cb.setTextColor(i==0 ? 0xFF000000 : 0xFFFFFFFF);
            cb.setTextSize(11);
            cb.setTypeface(null, android.graphics.Typeface.BOLD);
            cb.setPadding(12, 8, 12, 8);
            colorBtns[i] = cb;
            colorRow.addView(withFlex(cb, i==0?0:6));
            final String key=hlKeys[i]; final int idx=i;
            cb.setOnClickListener(v -> {
                highlightColor=key; deleteMode=false;
                for (int j=0;j<4;j++) colorBtns[j].setAlpha(j==idx?1f:0.45f);
            });
        }
        colorBtns[0].setAlpha(1f);
        for (int i=1;i<4;i++) colorBtns[i].setAlpha(0.45f);
        highlightToolbar.addView(colorRow);

        // Geri al + Sil satırı
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(8, 4, 8, 8);

        Button btnUndo    = makeActionBtn("↩  GERİ AL",       0xFF37474F);
        Button btnDelMode = makeActionBtn("✕  İŞARETLEME SİL", 0xFF333333);

        actionRow.addView(withFlex(btnUndo, 0));
        actionRow.addView(withFlex(btnDelMode, 8));
        highlightToolbar.addView(actionRow);

        // ── İÇERİK ALANI ────────────────────────────────────────────────────
        FrameLayout contentArea = new FrameLayout(this);
        contentArea.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        contentArea.setBackgroundColor(0xFF1A1A2E);

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
            topBar1.setVisibility(View.GONE);
            topBar2.setVisibility(View.GONE);
        } else {
            pageView = new ImageView(this);
            pageView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            pageView.setScaleType(ImageView.ScaleType.MATRIX);
            pageView.setBackgroundColor(0xFF1A1A2E);
            contentArea.addView(pageView);

            highlightOverlay = new HighlightOverlay(this);
            highlightOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            contentArea.addView(highlightOverlay);
            setupTouch();
        }

        // ── ALT BAR ─────────────────────────────────────────────────────────
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF0F3460);
        bottomBar.setPadding(6, 6, 6, 6);

        btnMark = makeIconBtn("ISARETLE", R.drawable.ic_isaretleme, 0xFF6A1B9A);
        Button btnRed   = makeIconBtn("ITIRAZ",  R.drawable.ic_itiraz,   0xFFE94560);
        Button btnBlue  = makeIconBtn("ARGUMAN", R.drawable.ic_arguman,  0xFF1565C0);
        Button btnGreen = makeIconBtn("VERI",    R.drawable.ic_veri,     0xFF2E7D32);

        bottomBar.addView(btnMark);
        bottomBar.addView(btnRed);
        bottomBar.addView(btnBlue);
        bottomBar.addView(btnGreen);

        root.addView(topBar1);
        root.addView(topBar2);
        root.addView(highlightToolbar);
        root.addView(contentArea);
        root.addView(bottomBar);
        setContentView(root);

        if (fileType.equals("TXT")) openTextFile();
        else openPdfFile(startPage);

        // ── BUTON OLAYLARI ───────────────────────────────────────────────────
        btnPrev.setOnClickListener(v -> { if (currentPage>0) showPage(currentPage-1); });
        btnNext.setOnClickListener(v -> { if (currentPage<totalPages-1) showPage(currentPage+1); });

        Runnable fastPrev = new Runnable() { @Override public void run() { if (isFastScrolling&&currentPage>0){showPage(currentPage-1);fastScrollHandler.postDelayed(this,120);} } };
        Runnable fastNext = new Runnable() { @Override public void run() { if (isFastScrolling&&currentPage<totalPages-1){showPage(currentPage+1);fastScrollHandler.postDelayed(this,120);} } };
        btnPrev.setOnLongClickListener(v -> { isFastScrolling=true; fastScrollHandler.post(fastPrev); return true; });
        btnNext.setOnLongClickListener(v -> { isFastScrolling=true; fastScrollHandler.post(fastNext); return true; });
        btnPrev.setOnTouchListener((v,e) -> { if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) isFastScrolling=false; return false; });
        btnNext.setOnTouchListener((v,e) -> { if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) isFastScrolling=false; return false; });

        btnGo.setOnClickListener(v -> {
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
                    else Toast.makeText(this,"Geçersiz sayfa",Toast.LENGTH_SHORT).show();
                } catch (Exception e) { Toast.makeText(this,"Sayı gir",Toast.LENGTH_SHORT).show(); }
            });
            b.setNegativeButton("İptal",null); b.show();
        });

        btnBookmark.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Yer İmi — Sayfa "+(currentPage+1));
            EditText input = new EditText(this);
            input.setHint("Başlık (isteğe bağlı)");
            b.setView(input);
            b.setPositiveButton("Ekle", (d,w) -> {
                ContentValues cv = new ContentValues();
                cv.put("pdf_uri", pdfUri); cv.put("page", currentPage);
                String t = input.getText().toString().trim();
                cv.put("title", t.isEmpty() ? "Sayfa "+(currentPage+1) : t);
                db.insert("bookmarks",null,cv);
                btnBookmark.setText("★ YER İMİ");
                Toast.makeText(this,"Yer imi eklendi",Toast.LENGTH_SHORT).show();
            });
            b.setNegativeButton("Listeyi Gör", (d,w) -> showBookmarksList());
            b.show();
        });

        btnNight.setOnClickListener(v -> {
            if (!nightMode&&!sepiaMode) { sepiaMode=true; btnNight.setText("🌅 SEPİA"); }
            else if (sepiaMode)         { sepiaMode=false; nightMode=true; btnNight.setText("☀️ NORMAL"); }
            else                        { nightMode=false; btnNight.setText("🌙 GECE"); }
            if (!fileType.equals("TXT")) showPage(currentPage);
        });

        btnMark.setOnClickListener(v -> {
            highlightMode = !highlightMode; deleteMode = false;
            highlightToolbar.setVisibility(highlightMode ? View.VISIBLE : View.GONE);
            btnMark.setBackgroundColor(highlightMode ? 0xFFE94560 : 0xFF6A1B9A);
            btnMark.setText(highlightMode ? "✓ BİTTİ" : "ISARETLE");
        });

        btnUndo.setOnClickListener(v -> {
            if (highlightOverlay==null) return;
            long id = highlightOverlay.undoLast();
            if (id>0) { db.delete("page_highlights","id=?",new String[]{String.valueOf(id)}); Toast.makeText(this,"Geri alındı",Toast.LENGTH_SHORT).show(); }
            else Toast.makeText(this,"Geri alınacak işaret yok",Toast.LENGTH_SHORT).show();
        });

        btnDelMode.setOnClickListener(v -> {
            deleteMode = !deleteMode;
            btnDelMode.setBackgroundColor(deleteMode ? 0xFFE94560 : 0xFF333333);
            btnDelMode.setText(deleteMode ? "✕ DOKUNARAK SİL" : "✕  İŞARETLEME SİL");
            if (deleteMode) Toast.makeText(this,"Silmek için işarete dokun",Toast.LENGTH_SHORT).show();
        });

        btnRed.setOnClickListener(v   -> saveHighlight("red",  "ITIRAZ"));
        btnBlue.setOnClickListener(v  -> saveHighlight("blue", "ARGUMAN"));
        btnGreen.setOnClickListener(v -> saveHighlight("green","VERI"));
    }

    // ── Buton yardımcıları ───────────────────────────────────────────────────
    private Button makeNavBtn(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(0xFF1A3A6A);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(20);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setPadding(24, 8, 24, 8);
        return btn;
    }

    private Button makeSmallTopBtn(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(11);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setPadding(8, 8, 8, 8);
        return btn;
    }

    private Button makeActionBtn(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(13);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setPadding(16, 12, 16, 12);
        return btn;
    }

    private Button makeIconBtn(String text, int iconRes, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(10);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(4, 10, 4, 10);
        try {
            Drawable icon = ContextCompat.getDrawable(this, iconRes);
            if (icon!=null) { icon.setBounds(0,0,40,40); btn.setCompoundDrawables(null,icon,null,null); btn.setCompoundDrawablePadding(4); }
        } catch (Exception ignored) {}
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return btn;
    }

    private Button withFlex(Button btn, int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(leftMargin, 0, 0, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    // ── Touch ────────────────────────────────────────────────────────────────
    private void setupTouch() {
        highlightOverlay.setOnTouchListener((v, event) -> {
            if (!highlightMode) return false;

            if (deleteMode) {
                if (event.getAction()==MotionEvent.ACTION_DOWN) {
                    long id = highlightOverlay.removeTouched(event.getX(), event.getY());
                    if (id>0) { db.delete("page_highlights","id=?",new String[]{String.valueOf(id)}); Toast.makeText(this,"Silindi",Toast.LENGTH_SHORT).show(); }
                }
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    highlightOverlay.startDraw(event.getX(), event.getY()); return true;
                case MotionEvent.ACTION_MOVE:
                    highlightOverlay.updateDraw(event.getX(), event.getY()); return true;
                case MotionEvent.ACTION_UP:
                    float[] rect = highlightOverlay.finishDraw();
                    if (rect!=null) {
                        ContentValues cv = new ContentValues();
                        cv.put("pdf_uri", pdfUri); cv.put("page", currentPage);
                        cv.put("x1",rect[0]); cv.put("y1",rect[1]);
                        cv.put("x2",rect[2]); cv.put("y2",rect[3]);
                        cv.put("color", highlightColor);
                        db.insert("page_highlights",null,cv);
                        loadPageHighlights(currentPage);
                        Toast.makeText(this,"İşaretlendi",Toast.LENGTH_SHORT).show();
                    }
                    return true;
            }
            return false;
        });

        pageView.setOnTouchListener((v, event) -> {
            if (highlightMode) return false;
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix); lastTouchX=event.getX(); lastTouchY=event.getY(); touchMode=TOUCH_DRAG; break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    touchMode=TOUCH_ZOOM; break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode==TOUCH_DRAG && !scaleDetector.isInProgress()) {
                        matrix.set(savedMatrix);
                        matrix.postTranslate(event.getX()-lastTouchX, event.getY()-lastTouchY);
                        clampMatrix();
                        pageView.setImageMatrix(matrix);
                        // Overlay'i de güncelle
                        syncOverlayMatrix();
                    }
                    break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP:
                    touchMode=TOUCH_NONE; break;
            }
            return true;
        });
    }

    private void syncOverlayMatrix() {
        if (highlightOverlay==null) return;
        highlightOverlay.viewMatrix = new Matrix(matrix);
        highlightOverlay.imgW = imgWidth;
        highlightOverlay.imgH = imgHeight;
        highlightOverlay.invalidate();
    }

    private void clampMatrix() {
        if (pageView.getDrawable()==null) return;
        float[] values=new float[9]; matrix.getValues(values);
        float scX=values[Matrix.MSCALE_X], tX=values[Matrix.MTRANS_X], tY=values[Matrix.MTRANS_Y];
        int vW=pageView.getWidth(), vH=pageView.getHeight();
        float sW=imgWidth*scX, sH=imgHeight*scX;
        float minTX,maxTX,minTY,maxTY;
        if (sW<=vW){minTX=maxTX=(vW-sW)/2f;} else {minTX=vW-sW;maxTX=0;}
        if (sH<=vH){minTY=maxTY=(vH-sH)/2f;} else {minTY=vH-sH;maxTY=0;}
        tX=Math.max(minTX,Math.min(tX,maxTX));
        tY=Math.max(minTY,Math.min(tY,maxTY));
        values[Matrix.MTRANS_X]=tX; values[Matrix.MTRANS_Y]=tY;
        matrix.setValues(values);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector d) {
            float[] v=new float[9]; matrix.getValues(v);
            float cur=v[Matrix.MSCALE_X];
            float ns=Math.max(MIN_ZOOM,Math.min(cur*d.getScaleFactor(),MAX_ZOOM));
            matrix.postScale(ns/cur,ns/cur,d.getFocusX(),d.getFocusY());
            clampMatrix();
            pageView.setImageMatrix(matrix);
            syncOverlayMatrix();
            return true;
        }
    }

    // ── PDF / TXT ────────────────────────────────────────────────────────────
    private void openPdfFile(int startPage) {
        try {
            fileDescriptor=getContentResolver().openFileDescriptor(Uri.parse(pdfUri),"r");
            if (fileDescriptor==null) { Toast.makeText(this,"Dosya açılamadı",Toast.LENGTH_LONG).show(); finish(); return; }
            pdfRenderer=new PdfRenderer(fileDescriptor);
            totalPages=pdfRenderer.getPageCount();
            showPage(Math.min(startPage,totalPages-1));
        } catch (Exception e) { Toast.makeText(this,"PDF hatası: "+e.getMessage(),Toast.LENGTH_LONG).show(); finish(); }
    }

    private void openTextFile() {
        try {
            InputStream is=getContentResolver().openInputStream(Uri.parse(pdfUri));
            BufferedReader r=new BufferedReader(new InputStreamReader(is));
            StringBuilder sb=new StringBuilder(); String line;
            while ((line=r.readLine())!=null) sb.append(line).append("\n");
            r.close(); txtContent.setText(sb.toString());
        } catch (Exception e) { Toast.makeText(this,"Dosya okunamadı",Toast.LENGTH_LONG).show(); }
    }

    private void showPage(int index) {
        if (pdfRenderer==null) return;
        currentPage=index;
        matrix.reset();
        pageView.setImageMatrix(matrix);
        if (highlightOverlay!=null) {
            highlightOverlay.loadData(new ArrayList<>(),new ArrayList<>());
            highlightOverlay.viewMatrix = new Matrix(matrix);
        }
        try {
            PdfRenderer.Page page=pdfRenderer.openPage(index);
            int w=getResources().getDisplayMetrics().widthPixels;
            int h=(int)((float)page.getHeight()/page.getWidth()*w);
            imgWidth=w; imgHeight=h;
            if (highlightOverlay!=null) { highlightOverlay.imgW=w; highlightOverlay.imgH=h; }
            Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            new Canvas(bmp).drawColor(Color.WHITE);
            page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            pageView.setImageBitmap(bmp);
            applyColorMode();
            pageInfo.setText((index+1)+" / "+totalPages);
            loadPageHighlights(index);
            ContentValues cv=new ContentValues();
            cv.put("pdf_uri",pdfUri); cv.put("last_page",index);
            cv.put("last_opened",new java.util.Date().toString());
            db.insertWithOnConflict("library",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) { Toast.makeText(this,"Sayfa yüklenemedi",Toast.LENGTH_SHORT).show(); }
    }

    private void applyColorMode() {
        if (nightMode) {
            ColorMatrix cm=new ColorMatrix(new float[]{-1,0,0,0,255,0,-1,0,0,255,0,0,-1,0,255,0,0,0,1,0});
            pageView.setColorFilter(new ColorMatrixColorFilter(cm));
        } else if (sepiaMode) {
            ColorMatrix cm=new ColorMatrix(new float[]{0.393f,0.769f,0.189f,0,0,0.349f,0.686f,0.168f,0,0,0.272f,0.534f,0.131f,0,0,0,0,0,1,0});
            pageView.setColorFilter(new ColorMatrixColorFilter(cm));
        } else {
            pageView.clearColorFilter();
        }
    }

    private void loadPageHighlights(int page) {
        if (highlightOverlay==null) return;
        List<long[]>  ids   = new ArrayList<>();
        List<float[]> rects = new ArrayList<>();
        Cursor c=db.rawQuery(
            "SELECT id,x1,y1,x2,y2,color FROM page_highlights WHERE pdf_uri=? AND page=? ORDER BY id ASC",
            new String[]{pdfUri,String.valueOf(page)});
        while (c.moveToNext()) {
            ids.add(new long[]{c.getLong(0)});
            float cf; switch(c.getString(5)!=null?c.getString(5):"yellow") { case "red":cf=1;break; case "blue":cf=2;break; case "green":cf=3;break; default:cf=0; }
            rects.add(new float[]{c.getFloat(1),c.getFloat(2),c.getFloat(3),c.getFloat(4),cf});
        }
        c.close();
        highlightOverlay.loadData(ids,rects);
    }

    private void showBookmarksList() {
        Cursor c=db.rawQuery("SELECT id,page,title FROM bookmarks WHERE pdf_uri=? ORDER BY page ASC",new String[]{pdfUri});
        if (!c.moveToFirst()) { Toast.makeText(this,"Bu kitapta yer imi yok",Toast.LENGTH_SHORT).show(); c.close(); return; }
        List<String> labels=new ArrayList<>(); List<Integer> pages=new ArrayList<>(); List<Integer> ids=new ArrayList<>();
        do { ids.add(c.getInt(0)); pages.add(c.getInt(1)); labels.add("Sayfa "+(c.getInt(1)+1)+" — "+c.getString(2)); } while(c.moveToNext());
        c.close();
        new AlertDialog.Builder(this).setTitle("Yer İmleri")
            .setItems(labels.toArray(new String[0]),(d,w)->showPage(pages.get(w)))
            .setNegativeButton("Kapat",null).show();
    }

    // ── Not kaydet (özel etiket destekli) ───────────────────────────────────
    private void saveHighlight(String color, String label) {
        AlertDialog.Builder b=new AlertDialog.Builder(this);
        b.setTitle(label+" — Sayfa "+(currentPage+1));
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32,16,32,8);

        EditText noteInput=new EditText(this);
        noteInput.setHint("Notunu yaz..."); noteInput.setMinLines(2);
        layout.addView(noteInput);

        TextView tl=new TextView(this);
        tl.setText("Etiket seç veya yeni oluştur:");
        tl.setTextColor(0xFF888888); tl.setTextSize(12); tl.setPadding(0,16,0,8);
        layout.addView(tl);

        final String[] selTag = {""};

        // Stok etiketler
        HorizontalScrollView hs=new HorizontalScrollView(this);
        LinearLayout tagRow=new LinearLayout(this);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);

        // DB'den mevcut özel etiketleri çek
        List<String> allTags = new ArrayList<>(DEFAULT_TAGS);
        Cursor tc=db.rawQuery("SELECT DISTINCT tag FROM highlights WHERE tag IS NOT NULL AND tag!='' AND tag NOT IN ("+
            "'Felsefe','Ekonomi','Strateji','Bilim','Tarih','Psikoloji','Diger') ORDER BY tag",null);
        while(tc.moveToNext()) allTags.add(tc.getString(0));
        tc.close();

        Button[] tagBtns=new Button[allTags.size()];
        for (int i=0;i<allTags.size();i++) {
            String t=allTags.get(i);
            Button tb=new Button(this);
            tb.setText(t); tb.setTextSize(10); tb.setTextColor(0xFFFFFFFF);
            tb.setBackgroundColor(0x446A1B9A); tb.setPadding(16,6,16,6);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(4,0,4,0); tb.setLayoutParams(tp);
            tagBtns[i]=tb; tagRow.addView(tb);
            final int idx=i;
            tb.setOnClickListener(cv -> { selTag[0]=t; for(int j=0;j<tagBtns.length;j++) tagBtns[j].setBackgroundColor(j==idx?0xFF6A1B9A:0x446A1B9A); });
        }
        hs.addView(tagRow); layout.addView(hs);

        // Özel etiket oluştur
        LinearLayout customRow=new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setPadding(0,10,0,0);
        EditText customTag=new EditText(this);
        customTag.setHint("Yeni etiket...");
        customTag.setTextSize(13);
        customTag.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button btnAddTag=new Button(this);
        btnAddTag.setText("+ Ekle");
        btnAddTag.setBackgroundColor(0xFF6A1B9A);
        btnAddTag.setTextColor(0xFFFFFFFF);
        btnAddTag.setTextSize(11);
        btnAddTag.setPadding(14,6,14,6);
        customRow.addView(customTag); customRow.addView(btnAddTag);
        layout.addView(customRow);

        btnAddTag.setOnClickListener(v -> {
            String nt=customTag.getText().toString().trim();
            if (!nt.isEmpty()) { selTag[0]=nt; Toast.makeText(this,"Etiket: "+nt,Toast.LENGTH_SHORT).show(); customTag.setText(""); }
        });

        b.setView(layout);
        b.setPositiveButton("Kaydet",(d,w)->{
            String finalTag=selTag[0].isEmpty()&&!customTag.getText().toString().trim().isEmpty()
                ?customTag.getText().toString().trim():selTag[0];
            ContentValues cv=new ContentValues();
            cv.put("pdf_uri",pdfUri); cv.put("page",currentPage);
            cv.put("color",color); cv.put("note",noteInput.getText().toString());
            cv.put("tag",finalTag);
            db.insert("highlights",null,cv);
            Toast.makeText(this,"Kaydedildi",Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("İptal",null); b.show();
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(PdfViewerActivity.this,"goblith.db",null,6); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try{db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT");}catch(Exception e){}
            try{db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT");}catch(Exception e){}
            try{db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");}catch(Exception e){}
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy(); isFastScrolling=false; fastScrollHandler.removeCallbacksAndMessages(null);
        try { if(pdfRenderer!=null)pdfRenderer.close(); if(fileDescriptor!=null)fileDescriptor.close(); } catch(IOException e){e.printStackTrace();}
    }
}
