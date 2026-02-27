package com.goblith.app;

import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.AsyncTask;
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

    static final int TOOL_HIGHLIGHT = 0;
    static final int TOOL_UNDERLINE = 1;
    static final int TOOL_STRIKE    = 2;
    static final int TOOL_FREEHAND  = 3;
    static final int TOOL_RECT      = 4;

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private int currentPage=0, totalPages=1;
    private ImageView pageView;
    private DrawingOverlay drawingOverlay;
    private TextView pageInfo;
    private TextView txtContent;
    private SQLiteDatabase db;
    private String pdfUri, fileType;
    private int imgWidth=1, imgHeight=1;

    private Matrix matrix=new Matrix(), savedMatrix=new Matrix();
    private ScaleGestureDetector scaleDetector;
    private float lastTouchX, lastTouchY;
    private static final int TOUCH_NONE=0,TOUCH_DRAG=1,TOUCH_ZOOM=2;
    private int touchMode=TOUCH_NONE;
    private static final float MIN_ZOOM=1f,MAX_ZOOM=5f;

    private boolean drawMode=false, deleteMode=false;
    private int currentTool=TOOL_HIGHLIGHT;
    private int currentColor=0xFFFFE500;
    private String currentColorKey="yellow";
    private LinearLayout drawToolbar;
    private Button btnDrawToggle;
    private Button btnDelMode;
    private boolean nightMode=false, sepiaMode=false;

    private Handler fastScrollHandler=new Handler();
    private boolean isFastScrolling=false;

    private static final List<String> DEFAULT_TAGS=Arrays.asList(
        "Felsefe","Ekonomi","Strateji","Bilim","Tarih","Psikoloji","Diger");

    // ─── Overlay ─────────────────────────────────────────────────────────────
    class DrawingOverlay extends View {
        List<Long>     itemIds  = new ArrayList<>();
        List<DrawItem> items    = new ArrayList<>();
        private Paint  paint    = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Aktif çizim (temp)
        boolean  drawing = false;
        float    ax1,ay1,ax2,ay2;          // sayfa normalize
        List<float[]> freePts = new ArrayList<>();

        Matrix viewMatrix = new Matrix();
        int    imgW=1, imgH=1;

        DrawingOverlay(Context ctx) { super(ctx); setBackgroundColor(Color.TRANSPARENT); }

        void load(List<Long> ids, List<DrawItem> its) {
            itemIds=new ArrayList<>(ids); items=new ArrayList<>(its);
            drawing=false; invalidate();
        }

        // ekran → sayfa normalize
        private float[] s2p(float sx, float sy) {
            Matrix inv=new Matrix(); viewMatrix.invert(inv);
            float[] p={sx,sy}; inv.mapPoints(p);
            return new float[]{p[0]/imgW, p[1]/imgH};
        }

        // sayfa normalize → ekran
        private float[] p2s(float px, float py) {
            float[] p={px*imgW, py*imgH}; viewMatrix.mapPoints(p); return p;
        }

        void startDraw(float sx, float sy) {
            float[] p=s2p(sx,sy);
            ax1=ax2=p[0]; ay1=ay2=p[1];
            freePts=new ArrayList<>(); freePts.add(new float[]{p[0],p[1]});
            drawing=true; invalidate();
        }

        void moveDraw(float sx, float sy) {
            float[] p=s2p(sx,sy); ax2=p[0]; ay2=p[1];
            if (currentTool==TOOL_FREEHAND) freePts.add(new float[]{p[0],p[1]});
            invalidate();
        }

        void finishDraw() {
            drawing=false; invalidate();
            float x1=Math.min(ax1,ax2), y1=Math.min(ay1,ay2);
            float x2=Math.max(ax1,ax2), y2=Math.max(ay1,ay2);
            if (currentTool!=TOOL_FREEHAND && (x2-x1)<0.005f) return;
            if (currentTool==TOOL_FREEHAND && freePts.size()<3) return;

            try {
                ContentValues cv=new ContentValues();
                cv.put("pdf_uri",pdfUri); cv.put("page",currentPage);
                cv.put("tool_type",currentTool); cv.put("color",currentColorKey);
                cv.put("x1",x1); cv.put("y1",y1); cv.put("x2",x2); cv.put("y2",y2);
                if (currentTool==TOOL_FREEHAND) {
                    StringBuilder sb=new StringBuilder();
                    for (float[] pt:freePts) sb.append(pt[0]).append(",").append(pt[1]).append(";");
                    cv.put("path_data",sb.toString());
                }
                db.insert("page_highlights",null,cv);
                loadPageDrawings(currentPage);
            } catch (Exception e) {
                Toast.makeText(PdfViewerActivity.this,"Kaydedilemedi: "+e.getMessage(),Toast.LENGTH_SHORT).show();
            }
        }

        long undoLast() {
            if (itemIds.isEmpty()) return -1;
            long id=itemIds.get(itemIds.size()-1);
            itemIds.remove(itemIds.size()-1); items.remove(items.size()-1);
            invalidate(); return id;
        }

        long removeTouched(float sx, float sy) {
            float[] p=s2p(sx,sy); float nx=p[0],ny=p[1];
            for (int i=items.size()-1;i>=0;i--) {
                DrawItem it=items.get(i); boolean hit;
                if (it.type==TOOL_FREEHAND) {
                    hit=false;
                    for (float[] pt:it.path) if (Math.abs(pt[0]-nx)<0.03f&&Math.abs(pt[1]-ny)<0.03f){hit=true;break;}
                } else {
                    hit=(nx>=it.x1&&nx<=it.x2&&ny>=it.y1&&ny<=it.y2);
                }
                if (hit) { long id=itemIds.get(i); itemIds.remove(i); items.remove(i); invalidate(); return id; }
            }
            return -1;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (DrawItem it:items) renderItem(canvas,it);
            if (drawing) renderActive(canvas);
        }

        private void renderItem(Canvas canvas, DrawItem it) {
            paint.reset(); paint.setAntiAlias(true);
            float[] a=p2s(it.x1,it.y1), b=p2s(it.x2,it.y2);
            float midY=(a[1]+b[1])/2f;

            switch(it.type) {
                case TOOL_HIGHLIGHT:
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(it.color&0x00FFFFFF|0x55000000);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
                case TOOL_UNDERLINE:
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(it.color); paint.setStrokeWidth(4f);
                    canvas.drawLine(a[0],b[1],b[0],b[1],paint);
                    break;
                case TOOL_STRIKE:
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(it.color); paint.setStrokeWidth(4f);
                    canvas.drawLine(a[0],midY,b[0],midY,paint);
                    break;
                case TOOL_FREEHAND:
                    if (it.path.size()<2) break;
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(it.color);
                    paint.setStrokeWidth(6f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
                    Path fp=new Path();
                    float[] f0=p2s(it.path.get(0)[0],it.path.get(0)[1]); fp.moveTo(f0[0],f0[1]);
                    for (int i=1;i<it.path.size();i++) { float[] pt=p2s(it.path.get(i)[0],it.path.get(i)[1]); fp.lineTo(pt[0],pt[1]); }
                    canvas.drawPath(fp,paint);
                    break;
                case TOOL_RECT:
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(it.color); paint.setStrokeWidth(4f);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
            }
        }

        private void renderActive(Canvas canvas) {
            paint.reset(); paint.setAntiAlias(true);
            float[] a=p2s(Math.min(ax1,ax2),Math.min(ay1,ay2));
            float[] b=p2s(Math.max(ax1,ax2),Math.max(ay1,ay2));
            float midY=(a[1]+b[1])/2f;

            switch(currentTool) {
                case TOOL_HIGHLIGHT:
                    paint.setStyle(Paint.Style.FILL); paint.setColor(currentColor&0x00FFFFFF|0x44000000);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(0xAAFFFFFF); paint.setStrokeWidth(2);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
                case TOOL_UNDERLINE:
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(currentColor); paint.setStrokeWidth(4f);
                    canvas.drawLine(a[0],b[1],b[0],b[1],paint);
                    paint.setAlpha(50); paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
                case TOOL_STRIKE:
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(currentColor); paint.setStrokeWidth(4f);
                    canvas.drawLine(a[0],midY,b[0],midY,paint);
                    paint.setAlpha(40); paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
                case TOOL_FREEHAND:
                    if (freePts.size()<2) break;
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(currentColor);
                    paint.setStrokeWidth(6f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
                    Path fp=new Path();
                    float[] f0=p2s(freePts.get(0)[0],freePts.get(0)[1]); fp.moveTo(f0[0],f0[1]);
                    for (int i=1;i<freePts.size();i++) { float[] pt=p2s(freePts.get(i)[0],freePts.get(i)[1]); fp.lineTo(pt[0],pt[1]); }
                    canvas.drawPath(fp,paint);
                    break;
                case TOOL_RECT:
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(currentColor); paint.setStrokeWidth(4f);
                    paint.setPathEffect(new DashPathEffect(new float[]{12,6},0));
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            db = new DBHelper().getWritableDatabase();
        } catch (Exception e) {
            Toast.makeText(this,"DB hatası: "+e.getMessage(),Toast.LENGTH_LONG).show();
            finish(); return;
        }
        pdfUri   = getIntent().getStringExtra("pdfUri");
        int startPage = getIntent().getIntExtra("startPage",0);
        fileType = getIntent().getStringExtra("fileType");
        if (fileType==null) fileType="PDF";
        scaleDetector=new ScaleGestureDetector(this,new ScaleListener());

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // ── Üst bar 1: Navigasyon ─────────────────────────────────────────────
        LinearLayout topBar1=new LinearLayout(this);
        topBar1.setOrientation(LinearLayout.HORIZONTAL);
        topBar1.setBackgroundColor(0xFF0F3460);
        topBar1.setPadding(8,6,8,4);
        topBar1.setGravity(Gravity.CENTER_VERTICAL);

        Button btnPrev=makeNavBtn("◀");
        pageInfo=new TextView(this);
        pageInfo.setTextColor(0xFFFFFFFF); pageInfo.setTextSize(14); pageInfo.setGravity(Gravity.CENTER);
        pageInfo.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button btnNext=makeNavBtn("▶");
        topBar1.addView(btnPrev); topBar1.addView(pageInfo); topBar1.addView(btnNext);

        // ── Üst bar 2: Yardımcı araçlar ──────────────────────────────────────
        LinearLayout topBar2=new LinearLayout(this);
        topBar2.setOrientation(LinearLayout.HORIZONTAL);
        topBar2.setBackgroundColor(0xFF0A2040);
        topBar2.setPadding(8,4,8,6);

        Button btnGo       =makeSmallBtn("GİT",          0xFF1A3A6A);
        Button btnBookmark =makeSmallBtn("☆ YERİMİ",     0xFF1A3A6A);
        Button btnArchive  =makeSmallBtn("📁 ARŞİV",     0xFF1B5E20);
        Button btnNightBtn =makeSmallBtn("🌙 GECE",      0xFF1A3A6A);
        topBar2.addView(flex(btnGo,0));
        topBar2.addView(flex(btnBookmark,6));
        topBar2.addView(flex(btnArchive,6));
        topBar2.addView(flex(btnNightBtn,6));
        Button btnPdfSearch = makeSmallBtn("🔍 ARA", 0xFF4C1D95);
        topBar2.addView(flex(btnPdfSearch,6));

        btnPdfSearch.setOnClickListener(v -> showPdfSearchDialog());

        // ── Çizim toolbar ─────────────────────────────────────────────────────
        drawToolbar=new LinearLayout(this);
        drawToolbar.setOrientation(LinearLayout.VERTICAL);
        drawToolbar.setBackgroundColor(0xFF060F1E);
        drawToolbar.setVisibility(View.GONE);

        // Araç seçimi
        HorizontalScrollView toolScroll=new HorizontalScrollView(this);
        LinearLayout toolRow=new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setPadding(8,8,8,4);

        String[] toolNames={"🖌 Fosforlu","A̲ Altçizgi","S̶ Üstçizik","✏ Kalem","□ Çerçeve"};
        int[]    toolIds  ={TOOL_HIGHLIGHT,TOOL_UNDERLINE,TOOL_STRIKE,TOOL_FREEHAND,TOOL_RECT};
        Button[] toolBtns =new Button[5];
        for (int i=0;i<5;i++) {
            Button b=new Button(this); b.setText(toolNames[i]);
            b.setBackgroundColor(i==0?0xFFE94560:0xFF1A3A6A);
            b.setTextColor(0xFFFFFFFF); b.setTextSize(12);
            b.setTypeface(null,android.graphics.Typeface.BOLD); b.setPadding(14,10,14,10);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(i==0?0:6,0,0,0); b.setLayoutParams(lp);
            toolBtns[i]=b; toolRow.addView(b);
            final int idx=i;
            b.setOnClickListener(v -> {
                currentTool=toolIds[idx];
                for (Button tb:toolBtns) tb.setBackgroundColor(0xFF1A3A6A);
                toolBtns[idx].setBackgroundColor(0xFFE94560);
            });
        }
        toolScroll.addView(toolRow);
        drawToolbar.addView(toolScroll);

        // Renk seçimi
        HorizontalScrollView colorScroll=new HorizontalScrollView(this);
        LinearLayout colorRow=new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(8,4,8,4);

        String[] cNames={"Sarı","Kırmızı","Mavi","Yeşil","Mor","Turuncu"};
        int[]    cVals ={0xFFFFE500,0xFFFF4560,0xFF4488FF,0xFF44CC66,0xFFAA44FF,0xFFFF8800};
        String[] cKeys ={"yellow","red","blue","green","purple","orange"};
        Button[] cBtns =new Button[6];
        for (int i=0;i<6;i++) {
            Button b=new Button(this); b.setText(cNames[i]);
            b.setBackgroundColor(cVals[i]);
            b.setTextColor(i==0?0xFF000000:0xFFFFFFFF);
            b.setTextSize(11); b.setTypeface(null,android.graphics.Typeface.BOLD); b.setPadding(14,8,14,8);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(i==0?0:6,0,0,0); b.setLayoutParams(lp);
            cBtns[i]=b; colorRow.addView(b); b.setAlpha(i==0?1f:0.45f);
            final int idx=i;
            b.setOnClickListener(v -> {
                currentColor=cVals[idx]; currentColorKey=cKeys[idx];
                for (Button cb:cBtns) cb.setAlpha(0.45f);
                cBtns[idx].setAlpha(1f);
            });
        }
        colorScroll.addView(colorRow);
        drawToolbar.addView(colorScroll);

        // Geri al + Sil
        LinearLayout actionRow=new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(8,6,8,8);
        Button btnUndo=makeActionBtn("↩  GERİ AL",0xFF37474F);
        btnDelMode    =makeActionBtn("✕  ÇİZİM SİL",0xFF333333);
        actionRow.addView(flex(btnUndo,0));
        actionRow.addView(flex(btnDelMode,8));
        drawToolbar.addView(actionRow);

        // ── İçerik ────────────────────────────────────────────────────────────
        FrameLayout contentArea=new FrameLayout(this);
        contentArea.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        contentArea.setBackgroundColor(0xFF1A1A2E);

        if (fileType.equals("TXT")) {
            ScrollView sv=new ScrollView(this);
            txtContent=new TextView(this);
            txtContent.setTextColor(0xFF111111); txtContent.setBackgroundColor(Color.WHITE);
            txtContent.setTextSize(15); txtContent.setPadding(24,24,24,24); txtContent.setLineSpacing(4,1.3f);
            sv.addView(txtContent);
            sv.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
            contentArea.addView(sv);
            topBar1.setVisibility(View.GONE); topBar2.setVisibility(View.GONE);
        } else {
            pageView=new ImageView(this);
            pageView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
            pageView.setScaleType(ImageView.ScaleType.MATRIX);
            pageView.setBackgroundColor(0xFF1A1A2E);
            contentArea.addView(pageView);

            drawingOverlay=new DrawingOverlay(this);
            drawingOverlay.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
            contentArea.addView(drawingOverlay);
            setupTouch();
        }

        // ── Alt bar ───────────────────────────────────────────────────────────
        LinearLayout bottomBar=new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF0F3460);
        bottomBar.setPadding(6,6,6,6);

        btnDrawToggle=makeIconBtn("ÇİZİM",  R.drawable.ic_isaretleme,0xFF6A1B9A);
        Button btnRed  =makeIconBtn("ITIRAZ",  R.drawable.ic_itiraz,  0xFFE94560);
        Button btnBlue =makeIconBtn("ARGUMAN", R.drawable.ic_arguman, 0xFF1565C0);
        Button btnGreen=makeIconBtn("VERI",    R.drawable.ic_veri,    0xFF2E7D32);
        bottomBar.addView(btnDrawToggle);
        bottomBar.addView(btnRed);
        bottomBar.addView(btnBlue);
        bottomBar.addView(btnGreen);

        // Gizle/göster butonu — topBar1'in sağına ekle
        Button btnToggleUI = makeNavBtn("⋯");
        btnToggleUI.setTextSize(16);
        btnToggleUI.setPadding(16,8,16,8);
        topBar1.addView(btnToggleUI);

        // Başlangıçta gizli
        topBar2.setVisibility(android.view.View.GONE);
        bottomBar.setVisibility(android.view.View.GONE);
        drawToolbar.setVisibility(android.view.View.GONE);

        root.addView(topBar1);
        root.addView(topBar2);
        root.addView(drawToolbar);
        root.addView(contentArea);
        root.addView(bottomBar);

        final boolean[] uiVisible = {false};
        btnToggleUI.setOnClickListener(v -> {
            uiVisible[0] = !uiVisible[0];
            int vis = uiVisible[0] ? android.view.View.VISIBLE : android.view.View.GONE;
            topBar2.setVisibility(vis);
            bottomBar.setVisibility(vis);
            if (!uiVisible[0]) drawToolbar.setVisibility(android.view.View.GONE);
            btnToggleUI.setText(uiVisible[0] ? "✕" : "⋯");
        });
        setContentView(root);

        if (fileType.equals("TXT")) openTextFile();
        else openPdfFile(startPage);

        // ── Olaylar ──────────────────────────────────────────────────────────
        btnPrev.setOnClickListener(v->{if(currentPage>0)showPage(currentPage-1);});
        btnNext.setOnClickListener(v->{if(currentPage<totalPages-1)showPage(currentPage+1);});

        Runnable fPrev=new Runnable(){@Override public void run(){if(isFastScrolling&&currentPage>0){showPage(currentPage-1);fastScrollHandler.postDelayed(this,120);}}};
        Runnable fNext=new Runnable(){@Override public void run(){if(isFastScrolling&&currentPage<totalPages-1){showPage(currentPage+1);fastScrollHandler.postDelayed(this,120);}}};
        btnPrev.setOnLongClickListener(v->{isFastScrolling=true;fastScrollHandler.post(fPrev);return true;});
        btnNext.setOnLongClickListener(v->{isFastScrolling=true;fastScrollHandler.post(fNext);return true;});
        btnPrev.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)isFastScrolling=false;return false;});
        btnNext.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)isFastScrolling=false;return false;});

        btnGo.setOnClickListener(v->{
            AlertDialog.Builder b=new AlertDialog.Builder(this); b.setTitle("Sayfaya Git");
            EditText in=new EditText(this); in.setInputType(InputType.TYPE_CLASS_NUMBER); in.setHint("1 - "+totalPages);
            b.setView(in);
            b.setPositiveButton("Git",(d,w)->{
                try{int p=Integer.parseInt(in.getText().toString())-1;if(p>=0&&p<totalPages)showPage(p);else Toast.makeText(this,"Geçersiz",Toast.LENGTH_SHORT).show();}
                catch(Exception e2){Toast.makeText(this,"Sayı gir",Toast.LENGTH_SHORT).show();}
            });
            b.setNegativeButton("İptal",null); b.show();
        });

        btnBookmark.setOnClickListener(v->{
            AlertDialog.Builder b=new AlertDialog.Builder(this);
            b.setTitle("Yer İmi — Sayfa "+(currentPage+1));
            EditText in=new EditText(this); in.setHint("Başlık (isteğe bağlı)");
            b.setView(in);
            b.setPositiveButton("Ekle",(d,w)->{
                ContentValues cv=new ContentValues();
                cv.put("pdf_uri",pdfUri); cv.put("page",currentPage);
                String t=in.getText().toString().trim();
                cv.put("title",t.isEmpty()?"Sayfa "+(currentPage+1):t);
                db.insert("bookmarks",null,cv);
                Toast.makeText(this,"Yer imi eklendi",Toast.LENGTH_SHORT).show();
            });
            b.setNegativeButton("Listeyi Gör",(d,w)->showBookmarksList());
            b.show();
        });

        btnArchive.setOnClickListener(v->showArchiveDialog());

        btnNightBtn.setOnClickListener(v->{
            if(!nightMode&&!sepiaMode){sepiaMode=true;btnNightBtn.setText("🌅 SEPİA");}
            else if(sepiaMode){sepiaMode=false;nightMode=true;btnNightBtn.setText("☀ NORMAL");}
            else{nightMode=false;btnNightBtn.setText("🌙 GECE");}
            if(!fileType.equals("TXT")) showPage(currentPage);
        });

        btnDrawToggle.setOnClickListener(v->{
            drawMode=!drawMode; deleteMode=false;
            drawToolbar.setVisibility(drawMode?View.VISIBLE:View.GONE);
            btnDrawToggle.setBackgroundColor(drawMode?0xFFE94560:0xFF6A1B9A);
            btnDrawToggle.setText(drawMode?"✓ BİTTİ":"ÇİZİM");
            if(btnDelMode!=null){btnDelMode.setBackgroundColor(0xFF333333);btnDelMode.setText("✕  ÇİZİM SİL");}
        });

        btnUndo.setOnClickListener(v->{
            if(drawingOverlay==null) return;
            long id=drawingOverlay.undoLast();
            if(id>0){db.delete("page_highlights","id=?",new String[]{String.valueOf(id)});Toast.makeText(this,"Geri alındı",Toast.LENGTH_SHORT).show();}
            else Toast.makeText(this,"Geri alınacak çizim yok",Toast.LENGTH_SHORT).show();
        });

        btnDelMode.setOnClickListener(v->{
            deleteMode=!deleteMode;
            btnDelMode.setBackgroundColor(deleteMode?0xFFE94560:0xFF333333);
            btnDelMode.setText(deleteMode?"✕ DOKUNARAK SİL":"✕  ÇİZİM SİL");
            if(deleteMode) Toast.makeText(this,"Silmek için çizime dokun",Toast.LENGTH_SHORT).show();
        });

        btnRed.setOnClickListener(v  ->saveNote("red",  "ITIRAZ"));
        btnBlue.setOnClickListener(v ->saveNote("blue", "ARGUMAN"));
        btnGreen.setOnClickListener(v->saveNote("green","VERI"));
    }

    // ── Arşiv dialogu ─────────────────────────────────────────────────────────
    private void showArchiveDialog() {
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, created_at TEXT)");
        } catch (Exception ignored) {}
        AlertDialog.Builder b=new AlertDialog.Builder(this);
        b.setTitle("📁 Arşive Ekle — Sayfa "+(currentPage+1));

        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32,16,32,16);

        // Kaynak bilgisi (otomatik dolu)
        TextView srcLabel=new TextView(this);
        srcLabel.setText("Kaynak:");
        srcLabel.setTextColor(0xFF888888); srcLabel.setTextSize(12); srcLabel.setPadding(0,0,0,4);
        layout.addView(srcLabel);

        // Kitap adını DB'den çek
        String bookName="Bu kitap";
        Cursor nc=db.rawQuery("SELECT custom_name FROM library WHERE pdf_uri=?",new String[]{pdfUri});
        if(nc.moveToFirst()&&nc.getString(0)!=null) bookName=nc.getString(0);
        nc.close();

        TextView srcInfo=new TextView(this);
        srcInfo.setText(bookName+" — Sayfa "+(currentPage+1));
        srcInfo.setTextColor(0xFF4488FF); srcInfo.setTextSize(14);
        srcInfo.setTypeface(null,android.graphics.Typeface.BOLD);
        srcInfo.setPadding(0,0,0,16);
        layout.addView(srcInfo);

        // Alıntı metni
        TextView alLabel=new TextView(this);
        alLabel.setText("Alıntı / Not:");
        alLabel.setTextColor(0xFF888888); alLabel.setTextSize(12); alLabel.setPadding(0,0,0,4);
        layout.addView(alLabel);

        EditText alıntıInput=new EditText(this);
        alıntıInput.setHint("Kitaptaki alıntıyı veya önemli pasajı yaz...");
        alıntıInput.setMinLines(3); alıntıInput.setMaxLines(6);
        alıntıInput.setGravity(Gravity.TOP);
        layout.addView(alıntıInput);

        // Konu etiketi
        TextView konuLabel=new TextView(this);
        konuLabel.setText("Konu / Etiket:");
        konuLabel.setTextColor(0xFF888888); konuLabel.setTextSize(12); konuLabel.setPadding(0,16,0,4);
        layout.addView(konuLabel);

        EditText konuInput=new EditText(this);
        konuInput.setHint("Örn: Osmanlı, Siyaset Felsefesi...");
        layout.addView(konuInput);

        // Önem derecesi
        TextView onLabel=new TextView(this);
        onLabel.setText("Önem:");
        onLabel.setTextColor(0xFF888888); onLabel.setTextSize(12); onLabel.setPadding(0,16,0,8);
        layout.addView(onLabel);

        LinearLayout onRow=new LinearLayout(this);
        onRow.setOrientation(LinearLayout.HORIZONTAL);
        String[]  onNames={"⭐ Düşük","⭐⭐ Orta","⭐⭐⭐ Yüksek"};
        int[]     onVals ={1,2,3};
        int[]     onColors={0xFF37474F,0xFF1565C0,0xFFE94560};
        Button[]  onBtns =new Button[3];
        final int[] selOn={2}; // varsayılan orta

        for(int i=0;i<3;i++){
            Button ob=new Button(this); ob.setText(onNames[i]);
            ob.setBackgroundColor(i==1?onColors[1]:0xFF222222);
            ob.setTextColor(0xFFFFFFFF); ob.setTextSize(10); ob.setPadding(10,6,10,6);
            LinearLayout.LayoutParams olp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
            olp.setMargins(i==0?0:6,0,0,0); ob.setLayoutParams(olp);
            onBtns[i]=ob; onRow.addView(ob);
            final int idx=i;
            ob.setOnClickListener(v->{
                selOn[0]=onVals[idx];
                for(int j=0;j<3;j++) onBtns[j].setBackgroundColor(0xFF222222);
                onBtns[idx].setBackgroundColor(onColors[idx]);
            });
        }
        layout.addView(onRow);

        ScrollView sv=new ScrollView(this);
        sv.addView(layout);
        b.setView(sv);

        final String finalBookName=bookName;
        b.setPositiveButton("Arşive Kaydet",(d,w)->{
            String alinti=alıntıInput.getText().toString().trim();
            if(alinti.isEmpty()){Toast.makeText(this,"Alıntı boş olamaz",Toast.LENGTH_SHORT).show();return;}
            ContentValues cv=new ContentValues();
            cv.put("pdf_uri",pdfUri);
            cv.put("book_name",finalBookName);
            cv.put("page",currentPage);
            cv.put("quote",alinti);
            cv.put("topic",konuInput.getText().toString().trim());
            cv.put("importance",selOn[0]);
            cv.put("created_at",new java.util.Date().toString());
            db.insert("archive",null,cv);
            Toast.makeText(this,"Arşive eklendi! 📁",Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("Arşivi Gör",(d,w)->{
            Intent i=new Intent(this,ArchiveActivity.class);
            startActivity(i);
        });
        b.setNeutralButton("İptal",null);
        b.show();
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    private void setupTouch() {
        drawingOverlay.setOnTouchListener((v,event)->{
            if(!drawMode) return false;
            if(deleteMode){
                if(event.getAction()==MotionEvent.ACTION_DOWN){
                    long id=drawingOverlay.removeTouched(event.getX(),event.getY());
                    if(id>0){db.delete("page_highlights","id=?",new String[]{String.valueOf(id)});Toast.makeText(this,"Silindi",Toast.LENGTH_SHORT).show();}
                }
                return true;
            }
            switch(event.getAction()){
                case MotionEvent.ACTION_DOWN:  drawingOverlay.startDraw(event.getX(),event.getY()); return true;
                case MotionEvent.ACTION_MOVE:  drawingOverlay.moveDraw(event.getX(),event.getY());  return true;
                case MotionEvent.ACTION_UP:    drawingOverlay.finishDraw(); return true;
            }
            return false;
        });

        pageView.setOnTouchListener((v,event)->{
            if(drawMode) return false;
            scaleDetector.onTouchEvent(event);
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN: savedMatrix.set(matrix);lastTouchX=event.getX();lastTouchY=event.getY();touchMode=TOUCH_DRAG;break;
                case MotionEvent.ACTION_POINTER_DOWN: touchMode=TOUCH_ZOOM;break;
                case MotionEvent.ACTION_MOVE:
                    if(touchMode==TOUCH_DRAG&&!scaleDetector.isInProgress()){
                        matrix.set(savedMatrix);
                        matrix.postTranslate(event.getX()-lastTouchX,event.getY()-lastTouchY);
                        clampMatrix();pageView.setImageMatrix(matrix);syncOverlay();
                    }
                    break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: touchMode=TOUCH_NONE;break;
            }
            return true;
        });
    }

    private void syncOverlay(){
        if(drawingOverlay==null) return;
        drawingOverlay.viewMatrix=new Matrix(matrix);
        drawingOverlay.imgW=imgWidth; drawingOverlay.imgH=imgHeight;
        drawingOverlay.invalidate();
    }

    private void clampMatrix(){
        float[] vals=new float[9]; matrix.getValues(vals);
        float sc=vals[Matrix.MSCALE_X],tx=vals[Matrix.MTRANS_X],ty=vals[Matrix.MTRANS_Y];
        int vW=pageView.getWidth(),vH=pageView.getHeight();
        float sW=imgWidth*sc,sH=imgHeight*sc;
        float minX,maxX,minY,maxY;
        if(sW<=vW){minX=maxX=(vW-sW)/2f;}else{minX=vW-sW;maxX=0;}
        if(sH<=vH){minY=maxY=(vH-sH)/2f;}else{minY=vH-sH;maxY=0;}
        vals[Matrix.MTRANS_X]=Math.max(minX,Math.min(tx,maxX));
        vals[Matrix.MTRANS_Y]=Math.max(minY,Math.min(ty,maxY));
        matrix.setValues(vals);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener{
        @Override public boolean onScale(ScaleGestureDetector d){
            float[] v=new float[9]; matrix.getValues(v);
            float cur=v[Matrix.MSCALE_X];
            float ns=Math.max(MIN_ZOOM,Math.min(cur*d.getScaleFactor(),MAX_ZOOM));
            matrix.postScale(ns/cur,ns/cur,d.getFocusX(),d.getFocusY());
            clampMatrix();pageView.setImageMatrix(matrix);syncOverlay();return true;
        }
    }

    // ── PDF/TXT ───────────────────────────────────────────────────────────────
    private void openPdfFile(int startPage){
        try{
            fileDescriptor=getContentResolver().openFileDescriptor(Uri.parse(pdfUri),"r");
            if(fileDescriptor==null){Toast.makeText(this,"Dosya açılamadı",Toast.LENGTH_LONG).show();finish();return;}
            pdfRenderer=new PdfRenderer(fileDescriptor);totalPages=pdfRenderer.getPageCount();
            showPage(Math.min(startPage,totalPages-1));
        }catch(Exception e){Toast.makeText(this,"PDF hatası: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();}
    }

    private void openTextFile(){
        try{
            InputStream is=getContentResolver().openInputStream(Uri.parse(pdfUri));
            BufferedReader r=new BufferedReader(new InputStreamReader(is));
            StringBuilder sb=new StringBuilder();String l;
            while((l=r.readLine())!=null)sb.append(l).append("\n");
            r.close();txtContent.setText(sb.toString());
        }catch(Exception e){Toast.makeText(this,"Dosya okunamadı",Toast.LENGTH_LONG).show();}
    }

    private void showPage(int index){
        if(pdfRenderer==null) return;
        currentPage=index; matrix.reset(); pageView.setImageMatrix(matrix);
        if(drawingOverlay!=null){
            drawingOverlay.load(new ArrayList<>(),new ArrayList<>());
            drawingOverlay.viewMatrix=new Matrix(matrix);
        }
        try{
            PdfRenderer.Page page=pdfRenderer.openPage(index);
            int w=getResources().getDisplayMetrics().widthPixels;
            int h=(int)((float)page.getHeight()/page.getWidth()*w);
            imgWidth=w; imgHeight=h;
            if(drawingOverlay!=null){drawingOverlay.imgW=w;drawingOverlay.imgH=h;}
            Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            new Canvas(bmp).drawColor(Color.WHITE);
            page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            pageView.setImageBitmap(bmp); applyColor();
            pageInfo.setText((index+1)+" / "+totalPages);
            loadPageDrawings(index);
            ContentValues cv=new ContentValues();
            cv.put("pdf_uri",pdfUri);cv.put("last_page",index);cv.put("last_opened",new java.util.Date().toString());
            db.insertWithOnConflict("library",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
        }catch(Exception e){Toast.makeText(this,"Sayfa yüklenemedi: "+e.getMessage(),Toast.LENGTH_SHORT).show();}
    }

    private void applyColor(){
        if(nightMode){ColorMatrix cm=new ColorMatrix(new float[]{-1,0,0,0,255,0,-1,0,0,255,0,0,-1,0,255,0,0,0,1,0});pageView.setColorFilter(new ColorMatrixColorFilter(cm));}
        else if(sepiaMode){ColorMatrix cm=new ColorMatrix(new float[]{0.393f,0.769f,0.189f,0,0,0.349f,0.686f,0.168f,0,0,0.272f,0.534f,0.131f,0,0,0,0,0,1,0});pageView.setColorFilter(new ColorMatrixColorFilter(cm));}
        else pageView.clearColorFilter();
    }

    void loadPageDrawings(int page){
        // Eksik kolonları ekle
        try{db.execSQL("ALTER TABLE page_highlights ADD COLUMN tool_type INTEGER DEFAULT 0");}catch(Exception ignored){}
        try{db.execSQL("ALTER TABLE page_highlights ADD COLUMN path_data TEXT");}catch(Exception ignored){}
        if(drawingOverlay==null) return;
        List<Long>     ids=new ArrayList<>();
        List<DrawItem> its=new ArrayList<>();
        try{
            Cursor c=db.rawQuery(
                "SELECT id,tool_type,x1,y1,x2,y2,color,path_data FROM page_highlights WHERE pdf_uri=? AND page=? ORDER BY id ASC",
                new String[]{pdfUri,String.valueOf(page)});
            while(c.moveToNext()){
                DrawItem it=new DrawItem();
                it.type=c.getInt(1);
                it.x1=c.getFloat(2);it.y1=c.getFloat(3);it.x2=c.getFloat(4);it.y2=c.getFloat(5);
                it.color=colorInt(c.getString(6));
                if(it.type==TOOL_FREEHAND){
                    String pd=c.getString(7);
                    if(pd!=null&&!pd.isEmpty()){
                        for(String seg:pd.split(";")){
                            String[]xy=seg.split(",");
                            if(xy.length==2){try{it.path.add(new float[]{Float.parseFloat(xy[0]),Float.parseFloat(xy[1])});}catch(Exception ignored){}}
                        }
                    }
                }
                ids.add(c.getLong(0)); its.add(it);
            }
            c.close();
        }catch(Exception e){Toast.makeText(this,"Çizimler yüklenemedi",Toast.LENGTH_SHORT).show();}
        drawingOverlay.load(ids,its);
    }

    private int colorInt(String c){
        switch(c!=null?c:"yellow"){
            case "red":    return 0xFFFF4560;
            case "blue":   return 0xFF4488FF;
            case "green":  return 0xFF44CC66;
            case "purple": return 0xFFAA44FF;
            case "orange": return 0xFFFF8800;
            default:       return 0xFFFFE500;
        }
    }

    private void showBookmarksList(){
        Cursor c=db.rawQuery("SELECT page,title FROM bookmarks WHERE pdf_uri=? ORDER BY page ASC",new String[]{pdfUri});
        if(!c.moveToFirst()){Toast.makeText(this,"Yer imi yok",Toast.LENGTH_SHORT).show();c.close();return;}
        List<String>  labels=new ArrayList<>();List<Integer> pages=new ArrayList<>();
        do{pages.add(c.getInt(0));labels.add("Sayfa "+(c.getInt(0)+1)+" — "+c.getString(1));}while(c.moveToNext());
        c.close();
        new AlertDialog.Builder(this).setTitle("Yer İmleri")
            .setItems(labels.toArray(new String[0]),(d,w)->showPage(pages.get(w)))
            .setNegativeButton("Kapat",null).show();
    }

    private void saveNote(String color,String label){
        AlertDialog.Builder b=new AlertDialog.Builder(this);
        b.setTitle(label+" — Sayfa "+(currentPage+1));
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(32,16,32,8);
        EditText noteIn=new EditText(this);noteIn.setHint("Notunu yaz...");noteIn.setMinLines(2);layout.addView(noteIn);
        TextView tl=new TextView(this);tl.setText("Etiket seç veya yeni oluştur:");tl.setTextColor(0xFF888888);tl.setTextSize(12);tl.setPadding(0,16,0,8);layout.addView(tl);
        final String[]selTag={""};
        List<String> allTags=new ArrayList<>(DEFAULT_TAGS);
        Cursor tc=db.rawQuery("SELECT DISTINCT tag FROM highlights WHERE tag IS NOT NULL AND tag!='' AND tag NOT IN ('Felsefe','Ekonomi','Strateji','Bilim','Tarih','Psikoloji','Diger') ORDER BY tag",null);
        while(tc.moveToNext())allTags.add(tc.getString(0));tc.close();
        HorizontalScrollView hs=new HorizontalScrollView(this);
        LinearLayout tagRow=new LinearLayout(this);tagRow.setOrientation(LinearLayout.HORIZONTAL);
        Button[]tagBtns=new Button[allTags.size()];
        for(int i=0;i<allTags.size();i++){
            String t=allTags.get(i);Button tb=new Button(this);tb.setText(t);tb.setTextSize(10);tb.setTextColor(0xFFFFFFFF);tb.setBackgroundColor(0x446A1B9A);tb.setPadding(16,6,16,6);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);tp.setMargins(4,0,4,0);tb.setLayoutParams(tp);
            tagBtns[i]=tb;tagRow.addView(tb);final int idx=i;
            tb.setOnClickListener(cv->{selTag[0]=t;for(int j=0;j<tagBtns.length;j++)tagBtns[j].setBackgroundColor(j==idx?0xFF6A1B9A:0x446A1B9A);});
        }
        hs.addView(tagRow);layout.addView(hs);
        LinearLayout cr=new LinearLayout(this);cr.setOrientation(LinearLayout.HORIZONTAL);cr.setPadding(0,10,0,0);
        EditText customTag=new EditText(this);customTag.setHint("Yeni etiket...");customTag.setTextSize(13);customTag.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button btnAddTag=new Button(this);btnAddTag.setText("+ Ekle");btnAddTag.setBackgroundColor(0xFF6A1B9A);btnAddTag.setTextColor(0xFFFFFFFF);btnAddTag.setTextSize(11);btnAddTag.setPadding(14,6,14,6);
        cr.addView(customTag);cr.addView(btnAddTag);layout.addView(cr);
        btnAddTag.setOnClickListener(v->{String nt=customTag.getText().toString().trim();if(!nt.isEmpty()){selTag[0]=nt;Toast.makeText(this,"Etiket: "+nt,Toast.LENGTH_SHORT).show();customTag.setText("");}});
        b.setView(layout);
        b.setPositiveButton("Kaydet",(d,w)->{
            String ft=selTag[0].isEmpty()&&!customTag.getText().toString().trim().isEmpty()?customTag.getText().toString().trim():selTag[0];
            ContentValues cv=new ContentValues();cv.put("pdf_uri",pdfUri);cv.put("page",currentPage);cv.put("color",color);cv.put("note",noteIn.getText().toString());cv.put("tag",ft);
            db.insert("highlights",null,cv);Toast.makeText(this,"Kaydedildi",Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("İptal",null);b.show();
    }

    // ── PDF Metin Arama ───────────────────────────────────────────────────────
    private SearchOverlay searchOverlay;

    private void showPdfSearchDialog() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("PDF'de Metin Ara");
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Aramak istediğin cümle veya kelime...");
        input.setPadding(32, 24, 32, 24);
        b.setView(input);
        b.setPositiveButton("Ara", (d, w) -> {
            String query = input.getText().toString().trim();
            if (query.isEmpty()) return;
            doFuzzyPdfSearch(query);
        });
        b.setNegativeButton("İptal", null);
        b.show();
    }

    private void doFuzzyPdfSearch(String query) {
        Toast.makeText(this, "Aranıyor...", Toast.LENGTH_SHORT).show();
        new AsyncTask<String, Void, Integer>() {
            @Override
            protected Integer doInBackground(String... params) {
                try {
                    String q = normalizeText(params[0]);
                    String[] qWords = q.split("\\s+");
                    java.io.InputStream is = getContentResolver()
                        .openInputStream(android.net.Uri.parse(pdfUri));
                    if (is == null) return -1;
                    java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                    byte[] tmp = new byte[4096]; int n;
                    while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
                    is.close();
                    byte[] pdfBytes = buf.toByteArray();
                    android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(
                        getContentResolver().openFileDescriptor(android.net.Uri.parse(pdfUri), "r"));
                    int totalPages = renderer.getPageCount();
                    renderer.close();
                    String[] pageTexts = extractPageTexts(pdfBytes, totalPages);
                    int bestPage = -1; double bestScore = 0;
                    for (int p = 0; p < pageTexts.length; p++) {
                        double score = fuzzyScore(qWords, normalizeText(pageTexts[p]));
                        if (score > bestScore) { bestScore = score; bestPage = p; }
                    }
                    if (bestScore < 0.1) return -2;
                    return bestPage;
                } catch (Exception e) { return -1; }
            }
            private String[] extractPageTexts(byte[] pdfBytes, int totalPages) {
                String[] texts = new String[totalPages];
                for (int i = 0; i < totalPages; i++) texts[i] = "";
                try {
                    String raw = new String(pdfBytes, "ISO-8859-1");
                    StringBuilder allText = new StringBuilder();
                    int pos = 0;
                    while (pos < raw.length()) {
                        int s1 = raw.indexOf("stream\n", pos);
                        int s2 = raw.indexOf("stream\r\n", pos);
                        int streamStart = (s1 < 0) ? s2 : (s2 < 0) ? s1 : Math.min(s1, s2);
                        if (streamStart < 0) break;
                        int dataStart = raw.indexOf("\n", streamStart) + 1;
                        int streamEnd = raw.indexOf("endstream", dataStart);
                        if (streamEnd < 0) break;
                        int objStart = raw.lastIndexOf("obj", streamStart);
                        String objHeader = objStart >= 0 ? raw.substring(objStart, streamStart) : "";
                        boolean isFlate = objHeader.contains("FlateDecode") || objHeader.contains("/Fl ");
                        String streamText = "";
                        if (isFlate) {
                            try {
                                byte[] compressed = raw.substring(dataStart, streamEnd).getBytes("ISO-8859-1");
                                java.util.zip.Inflater inf = new java.util.zip.Inflater();
                                inf.setInput(compressed);
                                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                                byte[] outBuf = new byte[4096];
                                while (!inf.finished()) {
                                    int len = inf.inflate(outBuf);
                                    if (len == 0) break;
                                    out.write(outBuf, 0, len);
                                }
                                inf.end();
                                streamText = out.toString("UTF-8");
                            } catch (Exception e) {
                                try {
                                    byte[] compressed = raw.substring(dataStart, streamEnd).getBytes("ISO-8859-1");
                                    java.util.zip.Inflater inf = new java.util.zip.Inflater();
                                    inf.setInput(compressed);
                                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                                    byte[] outBuf = new byte[4096];
                                    while (!inf.finished()) {
                                        int len = inf.inflate(outBuf);
                                        if (len == 0) break;
                                        out.write(outBuf, 0, len);
                                    }
                                    inf.end();
                                    streamText = out.toString("ISO-8859-1");
                                } catch (Exception ignored) {}
                            }
                        } else {
                            streamText = raw.substring(dataStart, streamEnd);
                        }
                        allText.append(extractTextFromStream(streamText)).append(" ");
                        pos = streamEnd + 9;
                    }
                    String full = allText.toString().trim();
                    if (full.isEmpty()) full = extractRawText(raw);
                    int chunkSize = Math.max(1, full.length() / Math.max(totalPages, 1));
                    for (int p = 0; p < totalPages; p++) {
                        int start = p * chunkSize;
                        int end = Math.min(start + chunkSize, full.length());
                        if (start < full.length()) texts[p] = full.substring(start, end);
                    }
                } catch (Exception ignored) {}
                return texts;
            }
            private String extractTextFromStream(String stream) {
                if (stream == null || stream.isEmpty()) return "";
                StringBuilder sb = new StringBuilder();
                java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("\\(([^)]*)\\)\\s*Tj").matcher(stream);
                while (m1.find()) sb.append(m1.group(1)).append(" ");
                java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("\\[([^\\]]*)\\]\\s*TJ").matcher(stream);
                while (m2.find()) {
                    java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("\\(([^)]*)\\)").matcher(m2.group(1));
                    while (m3.find()) sb.append(m3.group(1)).append(" ");
                }
                return sb.toString();
            }
            private String extractRawText(String raw) {
                StringBuilder sb = new StringBuilder();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([\\x20-\\x7E]{3,})\\)").matcher(raw);
                while (m.find()) sb.append(m.group(1)).append(" ");
                return sb.toString();
            }
            @Override
            protected void onPostExecute(Integer result) {
                if (result == -2) {
                    Toast.makeText(PdfViewerActivity.this, "Eslesme bulunamadi", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result < 0) {
                    Toast.makeText(PdfViewerActivity.this, "Hata olustu", Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(PdfViewerActivity.this,
                    "Sayfa " + (result + 1) + " civarinda bulundu", Toast.LENGTH_LONG).show();
                showPage(result);
                final String fQuery = query;
                new Handler().postDelayed(() -> showSearchOverlay(fQuery), 600);
            }
        }.execute(query);
    }


    private void showSearchOverlay(String query) {
        if (searchOverlay != null) {
            ((android.view.ViewGroup) searchOverlay.getParent()).removeView(searchOverlay);
        }
        searchOverlay = new SearchOverlay(this, query);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // contentArea içindeki frameLayout'a ekle
        try {
            android.widget.FrameLayout frame = (android.widget.FrameLayout)
                ((android.widget.FrameLayout) pageView.getParent());
            frame.addView(searchOverlay, lp);
        } catch (Exception e) {
            Toast.makeText(this, "İşaret eklenemedi", Toast.LENGTH_SHORT).show();
        }
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.toLowerCase()
            .replaceAll("[.,!?;:()\\[\\]{}]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private double fuzzyScore(String[] queryWords, String pageText) {
        if (pageText == null || pageText.isEmpty()) return 0;
        int matched = 0;
        for (String word : queryWords) {
            if (word.length() < 2) continue;
            // Tam eşleşme
            if (pageText.contains(word)) { matched += 2; continue; }
            // Kısmi eşleşme (en az 3 harf olan kelimelerin ilk 3 harfi)
            if (word.length() >= 3 && pageText.contains(word.substring(0, 3))) matched++;
        }
        return queryWords.length > 0 ? (double) matched / (queryWords.length * 2) : 0;
    }

    // Yanıp sönen arama işareti overlay
    class SearchOverlay extends View {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean visible = true;
        private Handler handler = new Handler();
        private String query;
        private Runnable blink = new Runnable() {
            @Override public void run() {
                visible = !visible;
                invalidate();
                handler.postDelayed(this, 600);
            }
        };

        SearchOverlay(android.content.Context ctx, String q) {
            super(ctx);
            this.query = q;
            setBackgroundColor(android.graphics.Color.TRANSPARENT);
            handler.post(blink);
            setOnClickListener(v -> {
                handler.removeCallbacks(blink);
                android.view.ViewGroup parent = (android.view.ViewGroup) getParent();
                if (parent != null) parent.removeView(this);
                searchOverlay = null;
                Toast.makeText(ctx, "İşaret kaldırıldı", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!visible) return;
            int w = getWidth(), h = getHeight();
            // Ortada yanıp sönen kutu
            paint.setColor(0x884488FF);
            paint.setStyle(Paint.Style.FILL);
            float boxH = h * 0.08f;
            float top  = h * 0.35f;
            canvas.drawRect(w * 0.05f, top, w * 0.95f, top + boxH, paint);
            // Kenarlık
            paint.setColor(0xFF4488FF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4);
            canvas.drawRect(w * 0.05f, top, w * 0.95f, top + boxH, paint);
            // Metin
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(32);
            paint.setTextAlign(Paint.Align.CENTER);
            String label = "Aranan: \"" + (query.length() > 20 ? query.substring(0, 20) + "..." : query) + "\"";
            canvas.drawText(label, w / 2f, top + boxH * 0.65f, paint);
            // Alt bilgi
            paint.setTextSize(22);
            paint.setColor(0xFFAABBFF);
            canvas.drawText("Kapatmak için dokun", w / 2f, top + boxH + 36, paint);
        }
    }


    // ── Buton yardımcıları ────────────────────────────────────────────────────
    private Button makeNavBtn(String t){Button b=new Button(this);b.setText(t);b.setBackgroundColor(0xFF1A3A6A);b.setTextColor(0xFFFFFFFF);b.setTextSize(20);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setPadding(28,8,28,8);return b;}
    private Button makeSmallBtn(String t,int c){Button b=new Button(this);b.setText(t);b.setBackgroundColor(c);b.setTextColor(0xFFFFFFFF);b.setTextSize(11);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setPadding(8,8,8,8);return b;}
    private Button makeActionBtn(String t,int c){Button b=new Button(this);b.setText(t);b.setBackgroundColor(c);b.setTextColor(0xFFFFFFFF);b.setTextSize(13);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setPadding(16,12,16,12);return b;}
    private Button makeIconBtn(String t,int iconRes,int c){
        Button b=new Button(this);b.setText(t);b.setBackgroundColor(c);b.setTextColor(0xFFFFFFFF);b.setTextSize(10);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setPadding(4,10,4,10);
        try{Drawable icon=ContextCompat.getDrawable(this,iconRes);if(icon!=null){icon.setBounds(0,0,40,40);b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(4);}}catch(Exception ignored){}
        b.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));return b;
    }
    private Button flex(Button b,int lm){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);lp.setMargins(lm,0,0,0);b.setLayoutParams(lp);return b;}

    // ── Intent import ─────────────────────────────────────────────────────────
class DBHelper extends SQLiteOpenHelper{
        DBHelper(){super(PdfViewerActivity.this,"goblith.db",null,8);}
        @Override public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, tool_type INTEGER DEFAULT 0, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, path_data TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, created_at TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db,int o,int n){
            try{db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT");}catch(Exception e){}
            try{db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT");}catch(Exception e){}
            try{db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, tool_type INTEGER DEFAULT 0, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, path_data TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");}catch(Exception e){}
            try{db.execSQL("ALTER TABLE page_highlights ADD COLUMN tool_type INTEGER DEFAULT 0");}catch(Exception e){}
            try{db.execSQL("ALTER TABLE page_highlights ADD COLUMN path_data TEXT");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, created_at TEXT)");}catch(Exception e){}
        }
    }

    @Override protected void onDestroy(){
        super.onDestroy();isFastScrolling=false;fastScrollHandler.removeCallbacksAndMessages(null);
        try{if(pdfRenderer!=null)pdfRenderer.close();if(fileDescriptor!=null)fileDescriptor.close();}catch(IOException e){e.printStackTrace();}
    }
}
