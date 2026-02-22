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
    private int currentPage = 0;
    private int totalPages = 1;
    private ImageView pageView;
    private HighlightOverlay highlightOverlay;
    private TextView pageInfo;
    private TextView txtContent;
    private SQLiteDatabase db;
    private String pdfUri;
    private String fileType;
    private int imgWidth=1, imgHeight=1;

    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private float lastTouchX, lastTouchY;
    private static final int TOUCH_NONE=0, TOUCH_DRAG=1, TOUCH_ZOOM=2;
    private int touchMode = TOUCH_NONE;
    private static final float MIN_ZOOM=1.0f, MAX_ZOOM=5.0f;

    private boolean highlightMode = false;
    private boolean deleteMode = false;
    private String highlightColor = "yellow";
    private LinearLayout highlightToolbar;
    private Button btnMark;

    private Handler fastScrollHandler = new Handler();
    private boolean isFastScrolling = false;

    private static final List<String> TAGS = Arrays.asList(
        "Felsefe","Ekonomi","Strateji","Bilim","Tarih","Psikoloji","Diger"
    );

    // ——— HIGHLIGHT OVERLAY ———
    class HighlightOverlay extends View {
        private List<long[]> hlIds = new ArrayList<>();
        private List<float[]> hlRects = new ArrayList<>();
        private Paint paint = new Paint();
        private float startX, startY, endX, endY;
        private boolean drawing = false;

        HighlightOverlay(Context ctx) {
            super(ctx);
            setBackgroundColor(Color.TRANSPARENT);
        }

        void loadData(List<long[]> ids, List<float[]> rects) {
            hlIds = new ArrayList<>(ids);
            hlRects = new ArrayList<>(rects);
            drawing = false;
            invalidate();
        }

        void startDraw(float x, float y) {
            startX=x; startY=y; endX=x; endY=y; drawing=true; invalidate();
        }

        void updateDraw(float x, float y) {
            endX=x; endY=y; invalidate();
        }

        // Sadece çizimi iptal eder, listeye eklemez
        float[] finishDraw() {
            drawing = false;
            float x1=Math.min(startX,endX), y1=Math.min(startY,endY);
            float x2=Math.max(startX,endX), y2=Math.max(startY,endY);
            invalidate();
            if (Math.abs(x2-x1)<15 || Math.abs(y2-y1)<8) return null;
            return new float[]{x1/getWidth(), y1/getHeight(), x2/getWidth(), y2/getHeight()};
        }

        long undoLast() {
            if (hlIds.isEmpty()) return -1;
            long id = hlIds.get(hlIds.size()-1)[0];
            hlIds.remove(hlIds.size()-1);
            hlRects.remove(hlRects.size()-1);
            invalidate();
            return id;
        }

        long removeTouched(float tx, float ty) {
            float nx=tx/getWidth(), ny=ty/getHeight();
            for (int i=hlRects.size()-1; i>=0; i--) {
                float[] h=hlRects.get(i);
                if (nx>=h[0]&&nx<=h[2]&&ny>=h[1]&&ny<=h[3]) {
                    long id=hlIds.get(i)[0];
                    hlIds.remove(i);
                    hlRects.remove(i);
                    invalidate();
                    return id;
                }
            }
            return -1;
        }

        private int getFillColor(float f) {
            switch((int)f) {
                case 1: return 0x50FF4560;
                case 2: return 0x504488FF;
                case 3: return 0x5044CC66;
                default: return 0x50FFE500;
            }
        }
        private int getStrokeColor(float f) {
            switch((int)f) {
                case 1: return 0x99FF4560;
                case 2: return 0x994488FF;
                case 3: return 0x9944CC66;
                default: return 0x99DDB800;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w=getWidth(), h=getHeight();
            for (float[] hl : hlRects) {
                paint.reset();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(getFillColor(hl[4]));
                canvas.drawRect(hl[0]*w,hl[1]*h,hl[2]*w,hl[3]*h,paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f);
                paint.setColor(getStrokeColor(hl[4]));
                canvas.drawRect(hl[0]*w,hl[1]*h,hl[2]*w,hl[3]*h,paint);
            }
            if (drawing) {
                float x1=Math.min(startX,endX),y1=Math.min(startY,endY);
                float x2=Math.max(startX,endX),y2=Math.max(startY,endY);
                paint.reset();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x44FFE500);
                canvas.drawRect(x1,y1,x2,y2,paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setColor(0xCCFFFFFF);
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

        // ——— ÜST BAR ———
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF0F3460);
        topBar.setPadding(8,8,8,8);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button btnPrev = makeTopBtn("  ◀  ", 0xFF1A3A6A);
        pageInfo = new TextView(this);
        pageInfo.setTextColor(0xFFFFFFFF);
        pageInfo.setTextSize(13);
        pageInfo.setGravity(Gravity.CENTER);
        pageInfo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button btnGo = makeTopBtn("Git", 0xFFE94560);
        Button btnNext = makeTopBtn("  ▶  ", 0xFF1A3A6A);

        topBar.addView(btnPrev);
        topBar.addView(pageInfo);
        topBar.addView(btnGo);
        topBar.addView(btnNext);

        // ——— HIGHLIGHT TOOLBAR (başta gizli) ———
        highlightToolbar = new LinearLayout(this);
        highlightToolbar.setOrientation(LinearLayout.VERTICAL);
        highlightToolbar.setBackgroundColor(0xFF0A1628);
        highlightToolbar.setVisibility(View.GONE);

        // Renk seçim satırı
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(8,8,8,4);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView colorLabel = new TextView(this);
        colorLabel.setText("Renk: ");
        colorLabel.setTextColor(0xFF888888);
        colorLabel.setTextSize(12);
        colorRow.addView(colorLabel);

        String[] hlNames={"Sarı","Kırmızı","Mavi","Yeşil"};
        int[] hlVals={0xFFFFE500,0xFFFF4560,0xFF4488FF,0xFF44CC66};
        String[] hlKeys={"yellow","red","blue","green"};
        Button[] colorBtns = new Button[4];

        for (int i=0; i<4; i++) {
            Button cb = new Button(this);
            cb.setText(hlNames[i]);
            cb.setBackgroundColor(hlVals[i]);
            cb.setTextColor(i==0?0xFF000000:0xFFFFFFFF);
            cb.setTextSize(12);
            cb.setTypeface(null, android.graphics.Typeface.BOLD);
            cb.setPadding(20,8,20,8);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            cp.setMargins(4,0,4,0);
            cb.setLayoutParams(cp);
            colorBtns[i]=cb;
            final String key=hlKeys[i];
            final int idx=i;
            cb.setOnClickListener(v -> {
                highlightColor=key;
                deleteMode=false;
                for (int j=0;j<4;j++) colorBtns[j].setAlpha(j==idx?1.0f:0.45f);
            });
            colorRow.addView(cb);
        }
        colorBtns[0].setAlpha(1.0f);
        for (int i=1;i<4;i++) colorBtns[i].setAlpha(0.45f);
        highlightToolbar.addView(colorRow);

        // Geri al + Sil satırı
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(8,4,8,8);

        Button btnUndo = new Button(this);
        btnUndo.setText("↩  GERI AL");
        btnUndo.setBackgroundColor(0xFF37474F);
        btnUndo.setTextColor(0xFFFFFFFF);
        btnUndo.setTextSize(13);
        btnUndo.setTypeface(null, android.graphics.Typeface.BOLD);
        btnUndo.setPadding(16,10,16,10);
        btnUndo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        actionRow.addView(btnUndo);

        Button btnDelMode = new Button(this);
        btnDelMode.setText("✕  ISARETLEME SIL");
        btnDelMode.setBackgroundColor(0xFF333333);
        btnDelMode.setTextColor(0xFFFFFFFF);
        btnDelMode.setTextSize(13);
        btnDelMode.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDelMode.setPadding(16,10,16,10);
        LinearLayout.LayoutParams dmp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        dmp.setMargins(8,0,0,0);
        btnDelMode.setLayoutParams(dmp);
        actionRow.addView(btnDelMode);

        highlightToolbar.addView(actionRow);

        // ——— İÇERİK ALANI ———
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
            txtContent.setLineSpacing(4,1.3f);
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

        // ——— ALT BAR ———
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF0F3460);
        bottomBar.setPadding(6,6,6,6);

        btnMark = makeIconBtn("ISARETLE", R.drawable.ic_isaretleme, 0xFF6A1B9A);
        Button btnRed = makeIconBtn("ITIRAZ", R.drawable.ic_itiraz, 0xFFE94560);
        Button btnBlue = makeIconBtn("ARGUMAN", R.drawable.ic_arguman, 0xFF1565C0);
        Button btnGreen = makeIconBtn("VERI", R.drawable.ic_veri, 0xFF2E7D32);

        bottomBar.addView(btnMark);
        bottomBar.addView(btnRed);
        bottomBar.addView(btnBlue);
        bottomBar.addView(btnGreen);

        root.addView(topBar);
        root.addView(highlightToolbar);
        root.addView(contentArea);
        root.addView(bottomBar);
        setContentView(root);

        if (fileType.equals("TXT")) openTextFile();
        else openPdfFile(startPage);

        // ——— BUTON OLAYLARI ———
        btnPrev.setOnClickListener(v -> { if (currentPage>0) showPage(currentPage-1); });
        btnNext.setOnClickListener(v -> { if (currentPage<totalPages-1) showPage(currentPage+1); });

        Runnable fastPrev = new Runnable() {
            @Override public void run() {
                if (isFastScrolling&&currentPage>0) { showPage(currentPage-1); fastScrollHandler.postDelayed(this,120); }
            }
        };
        Runnable fastNext = new Runnable() {
            @Override public void run() {
                if (isFastScrolling&&currentPage<totalPages-1) { showPage(currentPage+1); fastScrollHandler.postDelayed(this,120); }
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
            input.setHint("1 - "+totalPages);
            b.setView(input);
            b.setPositiveButton("Git",(d,w) -> {
                try {
                    int p=Integer.parseInt(input.getText().toString())-1;
                    if (p>=0&&p<totalPages) showPage(p);
                    else Toast.makeText(this,"Gecersiz sayfa",Toast.LENGTH_SHORT).show();
                } catch (Exception e) { Toast.makeText(this,"Sayi gir",Toast.LENGTH_SHORT).show(); }
            });
            b.setNegativeButton("Iptal",null);
            b.show();
        });

        btnMark.setOnClickListener(v -> {
            highlightMode = !highlightMode;
            deleteMode = false;
            highlightToolbar.setVisibility(highlightMode ? View.VISIBLE : View.GONE);
            btnMark.setBackgroundColor(highlightMode ? 0xFFE94560 : 0xFF6A1B9A);
            btnMark.setText(highlightMode ? "✓ BITTI" : "ISARETLE");
        });

        btnUndo.setOnClickListener(v -> {
            if (highlightOverlay==null) return;
            long id = highlightOverlay.undoLast();
            if (id>0) {
                db.delete("page_highlights","id=?",new String[]{String.valueOf(id)});
                Toast.makeText(this,"Geri alindi",Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,"Geri alinacak isaretleme yok",Toast.LENGTH_SHORT).show();
            }
        });

        btnDelMode.setOnClickListener(v -> {
            deleteMode = !deleteMode;
            btnDelMode.setBackgroundColor(deleteMode ? 0xFFE94560 : 0xFF333333);
            btnDelMode.setText(deleteMode ? "✕ DOKUNARAK SIL" : "✕  ISARETLEME SIL");
            if (deleteMode) Toast.makeText(this,"Silmek icin isarete dokun",Toast.LENGTH_SHORT).show();
        });

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

    private Button makeIconBtn(String text, int iconRes, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(10);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        try {
            Drawable icon = ContextCompat.getDrawable(this, iconRes);
            if (icon != null) {
                icon.setBounds(0, 0, 40, 40);
                btn.setCompoundDrawables(null, icon, null, null);
                btn.setCompoundDrawablePadding(4);
            }
        } catch (Exception e) {}
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(3,0,3,0);
        btn.setLayoutParams(lp);
        btn.setPadding(4,8,4,8);
        return btn;
    }

    private void setupTouch() {
        highlightOverlay.setOnTouchListener((v, event) -> {
            if (!highlightMode) return false;

            if (deleteMode) {
                if (event.getAction()==MotionEvent.ACTION_DOWN) {
                    long id = highlightOverlay.removeTouched(event.getX(), event.getY());
                    if (id>0) {
                        db.delete("page_highlights","id=?",new String[]{String.valueOf(id)});
                        Toast.makeText(this,"Silindi",Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    highlightOverlay.startDraw(event.getX(), event.getY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    highlightOverlay.updateDraw(event.getX(), event.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                    float[] rect = highlightOverlay.finishDraw();
                    if (rect!=null) {
                        // DB'ye kaydet
                        ContentValues cv = new ContentValues();
                        cv.put("pdf_uri", pdfUri);
                        cv.put("page", currentPage);
                        cv.put("x1", rect[0]);
                        cv.put("y1", rect[1]);
                        cv.put("x2", rect[2]);
                        cv.put("y2", rect[3]);
                        cv.put("color", highlightColor);
                        db.insert("page_highlights", null, cv);
                        // DB'den yeniden yükle (tutarlılık için)
                        loadPageHighlights(currentPage);
                        Toast.makeText(this,"Isaretlendi",Toast.LENGTH_SHORT).show();
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
                    savedMatrix.set(matrix);
                    lastTouchX=event.getX(); lastTouchY=event.getY();
                    touchMode=TOUCH_DRAG;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    touchMode=TOUCH_ZOOM;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode==TOUCH_DRAG&&!scaleDetector.isInProgress()) {
                        float dx=event.getX()-lastTouchX;
                        float dy=event.getY()-lastTouchY;
                        matrix.set(savedMatrix);
                        matrix.postTranslate(dx,dy);
                        clampMatrix();
                        pageView.setImageMatrix(matrix);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    touchMode=TOUCH_NONE;
                    break;
            }
            return true;
        });
    }

    private void clampMatrix() {
        if (pageView.getDrawable()==null) return;
        float[] values=new float[9];
        matrix.getValues(values);
        float scaleX=values[Matrix.MSCALE_X];
        float transX=values[Matrix.MTRANS_X];
        float transY=values[Matrix.MTRANS_Y];
        int viewW=pageView.getWidth(), viewH=pageView.getHeight();
        float scaledW=imgWidth*scaleX, scaledH=imgHeight*scaleX;
        float minTX,maxTX,minTY,maxTY;
        if (scaledW<=viewW) { minTX=maxTX=(viewW-scaledW)/2f; }
        else { minTX=viewW-scaledW; maxTX=0; }
        if (scaledH<=viewH) { minTY=maxTY=(viewH-scaledH)/2f; }
        else { minTY=viewH-scaledH; maxTY=0; }
        transX=Math.max(minTX,Math.min(transX,maxTX));
        transY=Math.max(minTY,Math.min(transY,maxTY));
        values[Matrix.MTRANS_X]=transX;
        values[Matrix.MTRANS_Y]=transY;
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
            return true;
        }
    }

    private void openPdfFile(int startPage) {
        try {
            fileDescriptor=getContentResolver().openFileDescriptor(Uri.parse(pdfUri),"r");
            if (fileDescriptor==null) { Toast.makeText(this,"Dosya acilamadi",Toast.LENGTH_LONG).show(); finish(); return; }
            pdfRenderer=new PdfRenderer(fileDescriptor);
            totalPages=pdfRenderer.getPageCount();
            showPage(Math.min(startPage,totalPages-1));
        } catch (Exception e) { Toast.makeText(this,"PDF hatasi: "+e.getMessage(),Toast.LENGTH_LONG).show(); finish(); }
    }

    private void openTextFile() {
        try {
            InputStream is=getContentResolver().openInputStream(Uri.parse(pdfUri));
            BufferedReader r=new BufferedReader(new InputStreamReader(is));
            StringBuilder sb=new StringBuilder(); String line;
            while ((line=r.readLine())!=null) sb.append(line).append("\n");
            r.close(); txtContent.setText(sb.toString());
        } catch (Exception e) { Toast.makeText(this,"Dosya okunamadi",Toast.LENGTH_LONG).show(); }
    }

    private void showPage(int index) {
        if (pdfRenderer==null) return;
        currentPage=index;
        matrix.reset();
        pageView.setImageMatrix(matrix);
        if (highlightOverlay!=null)
            highlightOverlay.loadData(new ArrayList<>(), new ArrayList<>());
        try {
            PdfRenderer.Page page=pdfRenderer.openPage(index);
            int w=getResources().getDisplayMetrics().widthPixels;
            int h=(int)((float)page.getHeight()/page.getWidth()*w);
            imgWidth=w; imgHeight=h;
            Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(bmp);
            c.drawColor(Color.WHITE);
            page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            pageView.setImageBitmap(bmp);
            pageInfo.setText((index+1)+" / "+totalPages);
            loadPageHighlights(index);
            ContentValues cv=new ContentValues();
            cv.put("pdf_uri",pdfUri); cv.put("last_page",index);
            cv.put("last_opened",new java.util.Date().toString());
            db.insertWithOnConflict("library",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) { Toast.makeText(this,"Sayfa yuklenemedi",Toast.LENGTH_SHORT).show(); }
    }

    private void loadPageHighlights(int page) {
        if (highlightOverlay==null) return;
        List<long[]> ids=new ArrayList<>();
        List<float[]> rects=new ArrayList<>();
        Cursor c=db.rawQuery(
            "SELECT id,x1,y1,x2,y2,color FROM page_highlights WHERE pdf_uri=? AND page=? ORDER BY id ASC",
            new String[]{pdfUri,String.valueOf(page)});
        while (c.moveToNext()) {
            ids.add(new long[]{c.getLong(0)});
            float cf;
            switch(c.getString(5)!=null?c.getString(5):"yellow") {
                case "red": cf=1; break;
                case "blue": cf=2; break;
                case "green": cf=3; break;
                default: cf=0;
            }
            rects.add(new float[]{c.getFloat(1),c.getFloat(2),c.getFloat(3),c.getFloat(4),cf});
        }
        c.close();
        highlightOverlay.loadData(ids,rects);
    }

    private void saveHighlight(String color, String label) {
        AlertDialog.Builder b=new AlertDialog.Builder(this);
        b.setTitle(label+" — Sayfa "+(currentPage+1));
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32,16,32,8);
        EditText input=new EditText(this);
        input.setHint("Notunu yaz...");
        input.setMinLines(2);
        layout.addView(input);
        TextView tl=new TextView(this);
        tl.setText("Etiket (istege bagli):");
        tl.setTextColor(0xFF888888);
        tl.setTextSize(12);
        tl.setPadding(0,16,0,8);
        layout.addView(tl);
        HorizontalScrollView hs=new HorizontalScrollView(this);
        LinearLayout tagRow=new LinearLayout(this);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] selTag={""};
        Button[] tagBtns=new Button[TAGS.size()];
        for (int i=0;i<TAGS.size();i++) {
            String t=TAGS.get(i);
            Button tb=new Button(this);
            tb.setText(t); tb.setTextSize(10); tb.setTextColor(0xFFFFFFFF);
            tb.setBackgroundColor(0x446A1B9A); tb.setPadding(16,6,16,6);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(4,0,4,0); tb.setLayoutParams(tp);
            tagBtns[i]=tb; tagRow.addView(tb);
            final int idx=i;
            tb.setOnClickListener(cv -> { selTag[0]=t; for (int j=0;j<tagBtns.length;j++) tagBtns[j].setBackgroundColor(j==idx?0xFF6A1B9A:0x446A1B9A); });
        }
        hs.addView(tagRow); layout.addView(hs);
        b.setView(layout);
        b.setPositiveButton("Kaydet",(d,w) -> {
            ContentValues cv=new ContentValues();
            cv.put("pdf_uri",pdfUri); cv.put("page",currentPage);
            cv.put("color",color); cv.put("note",input.getText().toString());
            cv.put("tag",selTag[0]);
            db.insert("highlights",null,cv);
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
