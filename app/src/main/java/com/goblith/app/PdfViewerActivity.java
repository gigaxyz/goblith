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
    private LinearLayout highlightColorBar;
    private Button btnMark;

    private Handler fastScrollHandler = new Handler();
    private boolean isFastScrolling = false;

    private static final List<String> TAGS = Arrays.asList(
        "Felsefe","Ekonomi","Strateji","Bilim","Tarih","Psikoloji","Diger"
    );

    // ——— HIGHLIGHT OVERLAY ———
    class HighlightOverlay extends View {
        private List<long[]> highlightIds = new ArrayList<>();
        private List<float[]> highlights = new ArrayList<>();
        private Paint paint = new Paint();
        private float startX, startY, endX, endY;
        private boolean drawing = false;

        HighlightOverlay(Context ctx) {
            super(ctx);
            setBackgroundColor(Color.TRANSPARENT);
        }

        void loadHighlights(List<long[]> ids, List<float[]> rects) {
            highlightIds = new ArrayList<>(ids);
            highlights = new ArrayList<>(rects);
            drawing = false;
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
            invalidate();
            if (Math.abs(x2-x1)<15 || Math.abs(y2-y1)<8) return null;
            return new float[]{x1/getWidth(), y1/getHeight(), x2/getWidth(), y2/getHeight()};
        }

        void addHighlight(long dbId, float x1, float y1, float x2, float y2, String color) {
            highlightIds.add(new long[]{dbId});
            highlights.add(new float[]{x1, y1, x2, y2, colorToFloat(color)});
            invalidate();
        }

        // Geri al — son eklenen
        long undoLast() {
            if (highlights.isEmpty()) return -1;
            highlights.remove(highlights.size()-1);
            long id = highlightIds.get(highlightIds.size()-1)[0];
            highlightIds.remove(highlightIds.size()-1);
            invalidate();
            return id;
        }

        // Tıklanan yerdeki highlight'ı sil
        long removeTouched(float tx, float ty) {
            float nx=tx/getWidth(), ny=ty/getHeight();
            for (int i=highlights.size()-1; i>=0; i--) {
                float[] h=highlights.get(i);
                if (nx>=h[0] && nx<=h[2] && ny>=h[1] && ny<=h[3]) {
                    highlights.remove(i);
                    long id=highlightIds.get(i)[0];
                    highlightIds.remove(i);
                    invalidate();
                    return id;
                }
            }
            return -1;
        }

        private float colorToFloat(String c) {
            switch(c) {
                case "red":   return 1;
                case "blue":  return 2;
                case "green": return 3;
                default:      return 0;
            }
        }

        private int getColor(float f) {
            switch((int)f) {
                case 1: return 0x55FF4560;
                case 2: return 0x554488FF;
                case 3: return 0x5544CC66;
                default: return 0x55FFE500;
            }
        }

        private int getStrokeColor(float f) {
            switch((int)f) {
                case 1: return 0xAAFF4560;
                case 2: return 0xAA4488FF;
                case 3: return 0xAA44CC66;
                default: return 0xAADDAA00;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w=getWidth(), h=getHeight();
            for (float[] hl : highlights) {
                paint.reset();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(getColor(hl[4]));
                canvas.drawRect(hl[0]*w, hl[1]*h, hl[2]*w, hl[3]*h, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f);
                paint.setColor(getStrokeColor(hl[4]));
                canvas.drawRect(hl[0]*w, hl[1]*h, hl[2]*w, hl[3]*h, paint);
            }
            if (drawing) {
                float x1=Math.min(startX,endX), y1=Math.min(startY,endY);
                float x2=Math.max(startX,endX), y2=Math.max(startY,endY);
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

        // Renk butonları
        String[] hlNames = {"Sari","Kirmizi","Mavi","Yesil"};
        int[] hlVals = {0xFFFFE500, 0xFFFF4560, 0xFF4488FF, 0xFF44CC66};
        String[] hlKeys = {"yellow","red","blue","green"};
        Button[] colorBtns = new Button[hlNames.length];

        for (int i=0; i<hlNames.length; i++) {
            Button cb = new Button(this);
            cb.setText(hlNames[i]);
            cb.setBackgroundColor(hlVals[i]);
            cb.setTextColor(0xFF000000);
            cb.setTextSize(10);
            cb.setTypeface(null, android.graphics.Typeface.BOLD);
            cb.setPadding(14,6,14,6);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(4,0,0,0);
            cb.setLayoutParams(cp);
            colorBtns[i] = cb;
            final String key = hlKeys[i];
            final int idx = i;
            cb.setOnClickListener(v -> {
                highlightColor = key;
                deleteMode = false;
                for (Button b2 : colorBtns) b2.setAlpha(0.5f);
                colorBtns[idx].setAlpha(1.0f);
            });
            highlightColorBar.addView(cb);
        }
        colorBtns[0].setAlpha(1.0f);
        for (int i=1;i<colorBtns.length;i++) colorBtns[i].setAlpha(0.5f);

        // Geri al butonu
        Button btnUndo = new Button(this);
        btnUndo.setText("GERI AL");
        btnUndo.setBackgroundColor(0xFF555555);
        btnUndo.setTextColor(0xFFFFFFFF);
        btnUndo.setTextSize(10);
        btnUndo.setTypeface(null, android.graphics.Typeface.BOLD);
        btnUndo.setPadding(14,6,14,6);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        up.setMargins(8,0,0,0);
        btnUndo.setLayoutParams(up);
        highlightColorBar.addView(btnUndo);

        // Sil modu butonu
        Button btnDelMode = new Button(this);
        btnDelMode.setText("SIL");
        btnDelMode.setBackgroundColor(0xFF333333);
        btnDelMode.setTextColor(0xFFFFFFFF);
        btnDelMode.setTextSize(10);
        btnDelMode.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDelMode.setPadding(14,6,14,6);
        LinearLayout.LayoutParams dmp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dmp.setMargins(4,0,0,0);
        btnDelMode.setLayoutParams(dmp);
        highlightColorBar.addView(btnDelMode);

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

        // Alt bar
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF0F3460);
        bottomBar.setPadding(8,6,8,6);

        btnMark = new Button(this);
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
            highlightColorBar.setVisibility(highlightMode ? View.VISIBLE : View.GONE);
            btnMark.setBackgroundColor(highlightMode ? 0xFFE94560 : 0xFF6A1B9A);
            btnMark.setText(highlightMode ? "BITTI" : "ISARETLEME");
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
            btnDelMode.setText(deleteMode ? "SIL (DOK)" : "SIL");
            Toast.makeText(this, deleteMode ? "Silmek icin isarete dokun" : "Silme modu kapandi", Toast.LENGTH_SHORT).show();
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
                        ContentValues values = new ContentValues();
                        values.put("pdf_uri",pdfUri);
                        values.put("page",currentPage);
                        values.put("x1",rect[0]);
                        values.put("y1",rect[1]);
                        values.put("x2",rect[2]);
                        values.put("y2",rect[3]);
                        values.put("color",highlightColor);
                        long newId = db.insert("page_highlights",null,values);
                        highlightOverlay.addHighlight(newId,rect[0],rect[1],rect[2],rect[3],highlightColor);
                        // Yeni eklendi, undo için son öğeyi güncelle (addHighlight zaten ekliyor ama finishDraw da ekliyor — sadece DB'den yükle)
                        loadPageHighlights(currentPage);
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
                    lastTouchX=event.getX();
                    lastTouchY=event.getY();
                    touchMode=TOUCH_DRAG;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    touchMode=TOUCH_ZOOM;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (touchMode==TOUCH_DRAG && !scaleDetector.isInProgress()) {
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
        float[] values = new float[9];
        matrix.getValues(values);
        float scaleX=values[Matrix.MSCALE_X];
        float transX=values[Matrix.MTRANS_X];
        float transY=values[Matrix.MTRANS_Y];
        int viewW=pageView.getWidth();
        int viewH=pageView.getHeight();
        float scaledW=imgWidth*scaleX;
        float scaledH=imgHeight*scaleX;

        float minTX, maxTX, minTY, maxTY;
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
        @Override public boolean onScale(ScaleGestureDetector detector) {
            float[] values = new float[9];
            matrix.getValues(values);
            float cur=values[Matrix.MSCALE_X];
            float newS=Math.max(MIN_ZOOM,Math.min(cur*detector.getScaleFactor(),MAX_ZOOM));
            float real=newS/cur;
            matrix.postScale(real,real,detector.getFocusX(),detector.getFocusY());
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
            BufferedReader reader=new BufferedReader(new InputStreamReader(is));
            StringBuilder sb=new StringBuilder();
            String line;
            while ((line=reader.readLine())!=null) sb.append(line).append("\n");
            reader.close();
            txtContent.setText(sb.toString());
        } catch (Exception e) { Toast.makeText(this,"Dosya okunamadi",Toast.LENGTH_LONG).show(); }
    }

    private void showPage(int index) {
        if (pdfRenderer==null) return;
        currentPage=index;
        matrix.reset();
        pageView.setImageMatrix(matrix);

        // Overlay temizle — yeni sayfa yüklenirken eski sayfanın highlight'ları görünmesin
        if (highlightOverlay!=null) highlightOverlay.loadHighlights(new ArrayList<>(), new ArrayList<>());

        try {
            PdfRenderer.Page page=pdfRenderer.openPage(index);
            int w=getResources().getDisplayMetrics().widthPixels;
            int h=(int)((float)page.getHeight()/page.getWidth()*w);
            imgWidth=w; imgHeight=h;
            Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            pageView.setImageBitmap(bitmap);
            pageInfo.setText((index+1)+" / "+totalPages);

            loadPageHighlights(index);

            ContentValues values=new ContentValues();
            values.put("pdf_uri",pdfUri);
            values.put("last_page",index);
            values.put("last_opened",new java.util.Date().toString());
            db.insertWithOnConflict("library",null,values,SQLiteDatabase.CONFLICT_REPLACE);
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
            long id=c.getLong(0);
            float x1=c.getFloat(1),y1=c.getFloat(2),x2=c.getFloat(3),y2=c.getFloat(4);
            String color=c.getString(5);
            float cf;
            switch(color!=null?color:"yellow") {
                case "red": cf=1; break;
                case "blue": cf=2; break;
                case "green": cf=3; break;
                default: cf=0;
            }
            ids.add(new long[]{id});
            rects.add(new float[]{x1,y1,x2,y2,cf});
        }
        c.close();
        highlightOverlay.loadHighlights(ids,rects);
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
        TextView tagLabel=new TextView(this);
        tagLabel.setText("Etiket (istege bagli):");
        tagLabel.setTextColor(0xFF888888);
        tagLabel.setTextSize(12);
        tagLabel.setPadding(0,16,0,8);
        layout.addView(tagLabel);
        HorizontalScrollView hs=new HorizontalScrollView(this);
        LinearLayout tagRow=new LinearLayout(this);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] selTag={""};
        Button[] tagBtns=new Button[TAGS.size()];
        for (int i=0;i<TAGS.size();i++) {
            String t=TAGS.get(i);
            Button tb=new Button(this);
            tb.setText(t);
            tb.setTextSize(10);
            tb.setTextColor(0xFFFFFFFF);
            tb.setBackgroundColor(0x446A1B9A);
            tb.setPadding(16,6,16,6);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
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
            ContentValues values=new ContentValues();
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
