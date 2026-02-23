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
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
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

    // Araç sabitleri
    static final int TOOL_HIGHLIGHT     = 0;
    static final int TOOL_UNDERLINE     = 1;
    static final int TOOL_STRIKETHROUGH = 2;
    static final int TOOL_FREEHAND      = 3;
    static final int TOOL_RECTANGLE     = 4;

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
    private static final float MIN_ZOOM=1f, MAX_ZOOM=5f;

    private boolean drawMode=false, deleteMode=false;
    private int currentTool=TOOL_HIGHLIGHT;
    private String drawColor="yellow";
    private LinearLayout drawToolbar;
    private Button btnDrawToggle;
    private boolean nightMode=false, sepiaMode=false;

    private Handler fastScrollHandler=new Handler();
    private boolean isFastScrolling=false;

    private static final List<String> DEFAULT_TAGS=Arrays.asList(
        "Felsefe","Ekonomi","Strateji","Bilim","Tarih","Psikoloji","Diger");

    // ─── Çizim katmanı ───────────────────────────────────────────────────────
    class DrawingOverlay extends View {

        // Kaydedilmiş çizimler
        private List<long[]>   ids   = new ArrayList<>();
        private List<DrawItem> items = new ArrayList<>();

        // Aktif çizim
        private boolean drawing=false;
        private float px1,py1,px2,py2;          // sayfa normalize (highlight/underline/strike/rect)
        private List<float[]> freePath=new ArrayList<>(); // serbest yol noktaları

        private Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Matrix viewMatrix=new Matrix();
        int imgW=1, imgH=1;

        static final int TYPE_HIGHLIGHT=0,TYPE_UNDERLINE=1,TYPE_STRIKE=2,TYPE_FREE=3,TYPE_RECT=4;

        class DrawItem {
            int type; float x1,y1,x2,y2; int color; float strokeWidth;
            List<float[]> path; // serbest çizim için
        }

        DrawingOverlay(Context ctx) { super(ctx); setBackgroundColor(Color.TRANSPARENT); }

        void loadData(List<long[]> newIds, List<DrawItem> newItems) {
            ids=new ArrayList<>(newIds); items=new ArrayList<>(newItems);
            drawing=false; invalidate();
        }

        // Ekran → sayfa normalize
        float[] s2p(float sx, float sy) {
            Matrix inv=new Matrix(); viewMatrix.invert(inv);
            float[] pt={sx,sy}; inv.mapPoints(pt);
            return new float[]{pt[0]/imgW, pt[1]/imgH};
        }

        // Sayfa normalize → ekran
        float[] p2s(float px, float py) {
            float[] pt={px*imgW, py*imgH};
            viewMatrix.mapPoints(pt);
            return pt;
        }

        void startDraw(float sx, float sy) {
            float[] p=s2p(sx,sy);
            px1=p[0]; py1=p[1]; px2=p[0]; py2=p[1];
            freePath=new ArrayList<>();
            freePath.add(new float[]{p[0],p[1]});
            drawing=true; invalidate();
        }

        void moveDraw(float sx, float sy) {
            float[] p=s2p(sx,sy);
            px2=p[0]; py2=p[1];
            if (currentTool==TOOL_FREEHAND) freePath.add(new float[]{p[0],p[1]});
            invalidate();
        }

        // Çizimi bitir, DB'ye kaydet
        void finishDraw() {
            drawing=false; invalidate();
            float x1=Math.min(px1,px2),y1=Math.min(py1,py2);
            float x2=Math.max(px1,px2),y2=Math.max(py1,py2);
            if (currentTool!=TOOL_FREEHAND && (x2-x1)<0.005f) return;
            if (currentTool==TOOL_FREEHAND && freePath.size()<3) return;

            ContentValues cv=new ContentValues();
            cv.put("pdf_uri",pdfUri); cv.put("page",currentPage);
            cv.put("tool_type",currentTool); cv.put("color",drawColor);
            cv.put("x1",x1); cv.put("y1",y1); cv.put("x2",x2); cv.put("y2",y2);
            if (currentTool==TOOL_FREEHAND) {
                StringBuilder sb=new StringBuilder();
                for (float[] pt:freePath) sb.append(pt[0]).append(",").append(pt[1]).append(";");
                cv.put("path_data",sb.toString());
            }
            long newId=db.insert("page_highlights",null,cv);
            PdfViewerActivity.this.loadPageDrawings(currentPage);
        }

        long undoLast() {
            if (ids.isEmpty()) return -1;
            long id=ids.get(ids.size()-1)[0];
            ids.remove(ids.size()-1); items.remove(items.size()-1);
            invalidate(); return id;
        }

        long removeTouched(float sx, float sy) {
            float[] p=s2p(sx,sy); float nx=p[0],ny=p[1];
            for (int i=items.size()-1;i>=0;i--) {
                DrawItem it=items.get(i);
                boolean hit=false;
                if (it.type==TYPE_FREE) {
                    for (float[] pt:it.path) if (Math.abs(pt[0]-nx)<0.03f&&Math.abs(pt[1]-ny)<0.03f){hit=true;break;}
                } else {
                    hit=(nx>=it.x1&&nx<=it.x2&&ny>=it.y1&&ny<=it.y2);
                }
                if (hit) { long id=ids.get(i)[0]; ids.remove(i); items.remove(i); invalidate(); return id; }
            }
            return -1;
        }

        private int parseColor(String c) {
            switch(c!=null?c:"yellow") {
                case "red":    return 0xFFFF4560;
                case "blue":   return 0xFF4488FF;
                case "green":  return 0xFF44CC66;
                case "purple": return 0xFFAA44FF;
                case "orange": return 0xFFFF8800;
                default:       return 0xFFFFE500;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (DrawItem it:items) drawItem(canvas,it,false);
            if (drawing) drawActive(canvas);
        }

        private void drawItem(Canvas canvas, DrawItem it, boolean preview) {
            int col=parseColor(drawColor);
            // Saved item — kendi rengini kullan
            if (!preview) col=it.color;
            paint.reset(); paint.setAntiAlias(true);

            switch(it.type) {
                case TYPE_HIGHLIGHT: {
                    float[] a=p2s(it.x1,it.y1), b=p2s(it.x2,it.y2);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(col&0x00FFFFFF|0x50000000);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
                }
                case TYPE_UNDERLINE: {
                    float[] a=p2s(it.x1,it.y2), b=p2s(it.x2,it.y2);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(col);
                    paint.setStrokeWidth(4f);
                    canvas.drawLine(a[0],a[1],b[0],b[1],paint);
                    break;
                }
                case TYPE_STRIKE: {
                    float midY=(it.y1+it.y2)/2f;
                    float[] a=p2s(it.x1,midY), b=p2s(it.x2,midY);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(col);
                    paint.setStrokeWidth(4f);
                    canvas.drawLine(a[0],a[1],b[0],b[1],paint);
                    break;
                }
                case TYPE_FREE: {
                    if (it.path==null||it.path.size()<2) break;
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(col);
                    paint.setStrokeWidth(6f);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeJoin(Paint.Join.ROUND);
                    Path path=new Path();
                    float[] first=p2s(it.path.get(0)[0],it.path.get(0)[1]);
                    path.moveTo(first[0],first[1]);
                    for (int i=1;i<it.path.size();i++) {
                        float[] pt=p2s(it.path.get(i)[0],it.path.get(i)[1]);
                        path.lineTo(pt[0],pt[1]);
                    }
                    canvas.drawPath(path,paint);
                    break;
                }
                case TYPE_RECT: {
                    float[] a=p2s(it.x1,it.y1), b=p2s(it.x2,it.y2);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(col);
                    paint.setStrokeWidth(4f);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
                }
            }
        }

        private void drawActive(Canvas canvas) {
            int col=parseColor(drawColor);
            paint.reset(); paint.setAntiAlias(true);
            float[] a=p2s(Math.min(px1,px2),Math.min(py1,py2));
            float[] b=p2s(Math.max(px1,px2),Math.max(py1,py2));
            float midScreenY=(a[1]+b[1])/2f;

            switch(currentTool) {
                case TOOL_HIGHLIGHT:
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(col&0x00FFFFFF|0x44000000);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(0xCCFFFFFF); paint.setStrokeWidth(2);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
                case TOOL_UNDERLINE:
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(col); paint.setStrokeWidth(4f);
                    canvas.drawLine(a[0],b[1],b[0],b[1],paint);
                    paint.setAlpha(60); paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
                case TOOL_STRIKETHROUGH:
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(col); paint.setStrokeWidth(4f);
                    canvas.drawLine(a[0],midScreenY,b[0],midScreenY,paint);
                    paint.setAlpha(40); paint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
                case TOOL_FREEHAND:
                    if (freePath.size()<2) break;
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(col);
                    paint.setStrokeWidth(6f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
                    Path fp=new Path();
                    float[] fp0=p2s(freePath.get(0)[0],freePath.get(0)[1]);
                    fp.moveTo(fp0[0],fp0[1]);
                    for (int i=1;i<freePath.size();i++) { float[] pt=p2s(freePath.get(i)[0],freePath.get(i)[1]); fp.lineTo(pt[0],pt[1]); }
                    canvas.drawPath(fp,paint);
                    break;
                case TOOL_RECTANGLE:
                    paint.setStyle(Paint.Style.STROKE); paint.setColor(col); paint.setStrokeWidth(4f);
                    paint.setPathEffect(new DashPathEffect(new float[]{12,6},0));
                    canvas.drawRect(a[0],a[1],b[0],b[1],paint);
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db=new DBHelper().getWritableDatabase();
        pdfUri=getIntent().getStringExtra("pdfUri");
        int startPage=getIntent().getIntExtra("startPage",0);
        fileType=getIntent().getStringExtra("fileType");
        if(fileType==null) fileType="PDF";
        scaleDetector=new ScaleGestureDetector(this,new ScaleListener());

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // ── ÜST BAR 1: Navigasyon ────────────────────────────────────────────
        LinearLayout topBar1=new LinearLayout(this);
        topBar1.setOrientation(LinearLayout.HORIZONTAL);
        topBar1.setBackgroundColor(0xFF0F3460);
        topBar1.setPadding(8,6,8,4);
        topBar1.setGravity(Gravity.CENTER_VERTICAL);

        Button btnPrev=makeNavBtn("◀");
        pageInfo=new TextView(this);
        pageInfo.setTextColor(0xFFFFFFFF); pageInfo.setTextSize(14);
        pageInfo.setGravity(Gravity.CENTER);
        pageInfo.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button btnNext=makeNavBtn("▶");
        topBar1.addView(btnPrev); topBar1.addView(pageInfo); topBar1.addView(btnNext);

        // ── ÜST BAR 2: Araçlar ───────────────────────────────────────────────
        LinearLayout topBar2=new LinearLayout(this);
        topBar2.setOrientation(LinearLayout.HORIZONTAL);
        topBar2.setBackgroundColor(0xFF0A2040);
        topBar2.setPadding(8,4,8,6);

        Button btnGo       =makeSmallTopBtn("GİT",       0xFF1A3A6A);
        Button btnBookmark =makeSmallTopBtn("☆ YERİMİ",  0xFF1A3A6A);
        Button btnNightBtn =makeSmallTopBtn("🌙 GECE",   0xFF1A3A6A);
        topBar2.addView(flex(btnGo,0));
        topBar2.addView(flex(btnBookmark,6));
        topBar2.addView(flex(btnNightBtn,6));

        // ── ÇİZİM TOOLBAR ────────────────────────────────────────────────────
        drawToolbar=new LinearLayout(this);
        drawToolbar.setOrientation(LinearLayout.VERTICAL);
        drawToolbar.setBackgroundColor(0xFF060F1E);
        drawToolbar.setVisibility(View.GONE);

        // Araç seçim satırı
        LinearLayout toolRow=new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setPadding(8,8,8,4);

        Button btnHL   =makeToolBtn("🖌 Fosforlu", TOOL_HIGHLIGHT,     true);
        Button btnUL   =makeToolBtn("A̲ Altı Çizgi",TOOL_UNDERLINE,    false);
        Button btnST   =makeToolBtn("S̶ Üstü Çizik",TOOL_STRIKETHROUGH,false);
        Button btnFH   =makeToolBtn("✏ Kalem",    TOOL_FREEHAND,      false);
        Button btnRC   =makeToolBtn("□ Çerçeve",  TOOL_RECTANGLE,     false);

        Button[] toolBtns={btnHL,btnUL,btnST,btnFH,btnRC};
        int[]    toolIds ={TOOL_HIGHLIGHT,TOOL_UNDERLINE,TOOL_STRIKETHROUGH,TOOL_FREEHAND,TOOL_RECTANGLE};

        // Yatay kaydırmalı araç satırı
        HorizontalScrollView toolScroll=new HorizontalScrollView(this);
        LinearLayout toolInner=new LinearLayout(this);
        toolInner.setOrientation(LinearLayout.HORIZONTAL);
        toolInner.setPadding(4,0,4,0);
        for(int i=0;i<toolBtns.length;i++){
            final int idx=i;
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMargins(i==0?0:6,0,0,0);
            toolBtns[i].setLayoutParams(tp);
            toolInner.addView(toolBtns[i]);
            toolBtns[i].setOnClickListener(v->{
                currentTool=toolIds[idx];
                for(Button b:toolBtns) b.setBackgroundColor(0xFF1A3A6A);
                toolBtns[idx].setBackgroundColor(0xFFE94560);
            });
        }
        toolScroll.addView(toolInner);
        drawToolbar.addView(toolScroll);

        // Renk satırı
        LinearLayout colorRow=new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(8,6,8,4);

        String[] cNames={"Sarı","Kırmızı","Mavi","Yeşil","Mor","Turuncu"};
        int[]    cVals ={0xFFFFE500,0xFFFF4560,0xFF4488FF,0xFF44CC66,0xFFAA44FF,0xFFFF8800};
        String[] cKeys ={"yellow","red","blue","green","purple","orange"};
        Button[] cBtns =new Button[cNames.length];

        HorizontalScrollView colorScroll=new HorizontalScrollView(this);
        LinearLayout colorInner=new LinearLayout(this);
        colorInner.setOrientation(LinearLayout.HORIZONTAL);
        colorInner.setPadding(4,0,4,0);
        for(int i=0;i<cNames.length;i++){
            Button cb=new Button(this);
            cb.setText(cNames[i]);
            cb.setBackgroundColor(cVals[i]);
            cb.setTextColor(i==0?0xFF000000:0xFFFFFFFF);
            cb.setTextSize(11);
            cb.setTypeface(null,android.graphics.Typeface.BOLD);
            cb.setPadding(14,8,14,8);
            cBtns[i]=cb;
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(i==0?0:6,0,0,0); cb.setLayoutParams(cp);
            colorInner.addView(cb);
            final String key=cKeys[i]; final int idx=i;
            cb.setOnClickListener(v->{
                drawColor=key;
                for(Button b:cBtns) b.setAlpha(0.4f);
                cBtns[idx].setAlpha(1f);
            });
        }
        cBtns[0].setAlpha(1f); for(int i=1;i<cBtns.length;i++) cBtns[i].setAlpha(0.4f);
        colorScroll.addView(colorInner);
        drawToolbar.addView(colorScroll);

        // Geri al + Sil satırı
        LinearLayout actionRow=new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(8,6,8,8);

        Button btnUndo   =makeActionBtn("↩  GERİ AL",      0xFF37474F);
        Button btnDelMode=makeActionBtn("✕  ÇİZİM SİL",    0xFF333333);
        actionRow.addView(flex(btnUndo,0));
        actionRow.addView(flex(btnDelMode,8));
        drawToolbar.addView(actionRow);

        // ── İÇERİK ───────────────────────────────────────────────────────────
        FrameLayout contentArea=new FrameLayout(this);
        contentArea.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        contentArea.setBackgroundColor(0xFF1A1A2E);

        if(fileType.equals("TXT")){
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

        // ── ALT BAR ──────────────────────────────────────────────────────────
        LinearLayout bottomBar=new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(0xFF0F3460);
        bottomBar.setPadding(6,6,6,6);

        btnDrawToggle=makeIconBtn("ÇİZİM",   R.drawable.ic_isaretleme, 0xFF6A1B9A);
        Button btnRed  =makeIconBtn("ITIRAZ", R.drawable.ic_itiraz,    0xFFE94560);
        Button btnBlue =makeIconBtn("ARGUMAN",R.drawable.ic_arguman,   0xFF1565C0);
        Button btnGreen=makeIconBtn("VERI",   R.drawable.ic_veri,      0xFF2E7D32);

        bottomBar.addView(btnDrawToggle);
        bottomBar.addView(btnRed);
        bottomBar.addView(btnBlue);
        bottomBar.addView(btnGreen);

        root.addView(topBar1);
        root.addView(topBar2);
        root.addView(drawToolbar);
        root.addView(contentArea);
        root.addView(bottomBar);
        setContentView(root);

        if(fileType.equals("TXT")) openTextFile();
        else openPdfFile(startPage);

        // ── OLAYLAR ──────────────────────────────────────────────────────────
        btnPrev.setOnClickListener(v->{if(currentPage>0) showPage(currentPage-1);});
        btnNext.setOnClickListener(v->{if(currentPage<totalPages-1) showPage(currentPage+1);});

        Runnable fPrev=new Runnable(){@Override public void run(){if(isFastScrolling&&currentPage>0){showPage(currentPage-1);fastScrollHandler.postDelayed(this,120);}}};
        Runnable fNext=new Runnable(){@Override public void run(){if(isFastScrolling&&currentPage<totalPages-1){showPage(currentPage+1);fastScrollHandler.postDelayed(this,120);}}};
        btnPrev.setOnLongClickListener(v->{isFastScrolling=true;fastScrollHandler.post(fPrev);return true;});
        btnNext.setOnLongClickListener(v->{isFastScrolling=true;fastScrollHandler.post(fNext);return true;});
        btnPrev.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)isFastScrolling=false;return false;});
        btnNext.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)isFastScrolling=false;return false;});

        btnGo.setOnClickListener(v->{
            AlertDialog.Builder b=new AlertDialog.Builder(this);
            b.setTitle("Sayfaya Git");
            EditText in=new EditText(this); in.setInputType(InputType.TYPE_CLASS_NUMBER); in.setHint("1 - "+totalPages);
            b.setView(in);
            b.setPositiveButton("Git",(d,w)->{
                try{int p=Integer.parseInt(in.getText().toString())-1;if(p>=0&&p<totalPages)showPage(p);else Toast.makeText(this,"Geçersiz",Toast.LENGTH_SHORT).show();}
                catch(Exception e){Toast.makeText(this,"Sayı gir",Toast.LENGTH_SHORT).show();}
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

    // ── Buton factory'leri ────────────────────────────────────────────────────
    private Button makeNavBtn(String t){
        Button b=new Button(this); b.setText(t); b.setBackgroundColor(0xFF1A3A6A);
        b.setTextColor(0xFFFFFFFF); b.setTextSize(20);
        b.setTypeface(null,android.graphics.Typeface.BOLD); b.setPadding(28,8,28,8); return b;
    }
    private Button makeSmallTopBtn(String t,int c){
        Button b=new Button(this); b.setText(t); b.setBackgroundColor(c);
        b.setTextColor(0xFFFFFFFF); b.setTextSize(11);
        b.setTypeface(null,android.graphics.Typeface.BOLD); b.setPadding(8,8,8,8); return b;
    }
    private Button makeToolBtn(String t,int toolId,boolean active){
        Button b=new Button(this); b.setText(t);
        b.setBackgroundColor(active?0xFFE94560:0xFF1A3A6A);
        b.setTextColor(0xFFFFFFFF); b.setTextSize(12);
        b.setTypeface(null,android.graphics.Typeface.BOLD); b.setPadding(14,10,14,10); return b;
    }
    private Button makeActionBtn(String t,int c){
        Button b=new Button(this); b.setText(t); b.setBackgroundColor(c);
        b.setTextColor(0xFFFFFFFF); b.setTextSize(13);
        b.setTypeface(null,android.graphics.Typeface.BOLD); b.setPadding(16,12,16,12); return b;
    }
    private Button makeIconBtn(String t,int iconRes,int c){
        Button b=new Button(this); b.setText(t); b.setBackgroundColor(c);
        b.setTextColor(0xFFFFFFFF); b.setTextSize(10);
        b.setTypeface(null,android.graphics.Typeface.BOLD);
        b.setGravity(Gravity.CENTER); b.setPadding(4,10,4,10);
        try{Drawable icon=ContextCompat.getDrawable(this,iconRes);if(icon!=null){icon.setBounds(0,0,40,40);b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(4);}}catch(Exception ignored){}
        b.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); return b;
    }
    private Button flex(Button b,int lm){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        lp.setMargins(lm,0,0,0); b.setLayoutParams(lp); return b;
    }

    // ── Touch ─────────────────────────────────────────────────────────────────
    private void setupTouch(){
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
                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix); lastTouchX=event.getX(); lastTouchY=event.getY(); touchMode=TOUCH_DRAG; break;
                case MotionEvent.ACTION_POINTER_DOWN: touchMode=TOUCH_ZOOM; break;
                case MotionEvent.ACTION_MOVE:
                    if(touchMode==TOUCH_DRAG&&!scaleDetector.isInProgress()){
                        matrix.set(savedMatrix);
                        matrix.postTranslate(event.getX()-lastTouchX,event.getY()-lastTouchY);
                        clampMatrix(); pageView.setImageMatrix(matrix); syncOverlay();
                    }
                    break;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_POINTER_UP: touchMode=TOUCH_NONE; break;
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
            clampMatrix(); pageView.setImageMatrix(matrix); syncOverlay(); return true;
        }
    }

    // ── PDF/TXT ───────────────────────────────────────────────────────────────
    private void openPdfFile(int startPage){
        try{
            fileDescriptor=getContentResolver().openFileDescriptor(Uri.parse(pdfUri),"r");
            if(fileDescriptor==null){Toast.makeText(this,"Dosya açılamadı",Toast.LENGTH_LONG).show();finish();return;}
            pdfRenderer=new PdfRenderer(fileDescriptor); totalPages=pdfRenderer.getPageCount();
            showPage(Math.min(startPage,totalPages-1));
        }catch(Exception e){Toast.makeText(this,"PDF hatası: "+e.getMessage(),Toast.LENGTH_LONG).show();finish();}
    }
    private void openTextFile(){
        try{InputStream is=getContentResolver().openInputStream(Uri.parse(pdfUri));BufferedReader r=new BufferedReader(new InputStreamReader(is));StringBuilder sb=new StringBuilder();String l;while((l=r.readLine())!=null)sb.append(l).append("\n");r.close();txtContent.setText(sb.toString());}
        catch(Exception e){Toast.makeText(this,"Dosya okunamadı",Toast.LENGTH_LONG).show();}
    }

    private void showPage(int index){
        if(pdfRenderer==null) return;
        currentPage=index; matrix.reset(); pageView.setImageMatrix(matrix);
        if(drawingOverlay!=null){drawingOverlay.loadData(new ArrayList<>(),new ArrayList<>());drawingOverlay.viewMatrix=new Matrix(matrix);}
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
        }catch(Exception e){Toast.makeText(this,"Sayfa yüklenemedi",Toast.LENGTH_SHORT).show();}
    }

    private void applyColor(){
        if(nightMode){ColorMatrix cm=new ColorMatrix(new float[]{-1,0,0,0,255,0,-1,0,0,255,0,0,-1,0,255,0,0,0,1,0});pageView.setColorFilter(new ColorMatrixColorFilter(cm));}
        else if(sepiaMode){ColorMatrix cm=new ColorMatrix(new float[]{0.393f,0.769f,0.189f,0,0,0.349f,0.686f,0.168f,0,0,0.272f,0.534f,0.131f,0,0,0,0,0,1,0});pageView.setColorFilter(new ColorMatrixColorFilter(cm));}
        else pageView.clearColorFilter();
    }

    // DB'den mevcut sayfanın çizimlerini yükle
    void loadPageDrawings(int page){
        if(drawingOverlay==null) return;
        List<long[]> ids=new ArrayList<>();
        List<DrawingOverlay.DrawItem> its=new ArrayList<>();
        Cursor c=db.rawQuery(
            "SELECT id,tool_type,x1,y1,x2,y2,color,path_data FROM page_highlights WHERE pdf_uri=? AND page=? ORDER BY id ASC",
            new String[]{pdfUri,String.valueOf(page)});
        while(c.moveToNext()){
            long id=c.getLong(0);
            int tool=c.getInt(1);
            DrawingOverlay.DrawItem it=drawingOverlay.new DrawItem();
            it.type=tool; it.x1=c.getFloat(2);it.y1=c.getFloat(3);it.x2=c.getFloat(4);it.y2=c.getFloat(5);
            String colorStr=c.getString(6);
            it.color=parseColorInt(colorStr);
            if(tool==TOOL_FREEHAND){
                String pd=c.getString(7);
                it.path=new ArrayList<>();
                if(pd!=null&&!pd.isEmpty()){
                    for(String seg:pd.split(";")){
                        String[]xy=seg.split(",");
                        if(xy.length==2){try{it.path.add(new float[]{Float.parseFloat(xy[0]),Float.parseFloat(xy[1])});}catch(Exception ignored){}}
                    }
                }
            }
            ids.add(new long[]{id}); its.add(it);
        }
        c.close();
        drawingOverlay.loadData(ids,its);
    }

    private int parseColorInt(String c){
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
        Cursor c=db.rawQuery("SELECT id,page,title FROM bookmarks WHERE pdf_uri=? ORDER BY page ASC",new String[]{pdfUri});
        if(!c.moveToFirst()){Toast.makeText(this,"Bu kitapta yer imi yok",Toast.LENGTH_SHORT).show();c.close();return;}
        List<String> labels=new ArrayList<>();List<Integer> pages=new ArrayList<>();
        do{pages.add(c.getInt(1));labels.add("Sayfa "+(c.getInt(1)+1)+" — "+c.getString(2));}while(c.moveToNext());
        c.close();
        new AlertDialog.Builder(this).setTitle("Yer İmleri")
            .setItems(labels.toArray(new String[0]),(d,w)->showPage(pages.get(w)))
            .setNegativeButton("Kapat",null).show();
    }

    private void saveNote(String color,String label){
        AlertDialog.Builder b=new AlertDialog.Builder(this);
        b.setTitle(label+" — Sayfa "+(currentPage+1));
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(32,16,32,8);
        EditText noteIn=new EditText(this); noteIn.setHint("Notunu yaz..."); noteIn.setMinLines(2); layout.addView(noteIn);
        TextView tl=new TextView(this); tl.setText("Etiket seç veya yeni oluştur:"); tl.setTextColor(0xFF888888); tl.setTextSize(12); tl.setPadding(0,16,0,8); layout.addView(tl);
        final String[]selTag={""};
        List<String> allTags=new ArrayList<>(DEFAULT_TAGS);
        Cursor tc=db.rawQuery("SELECT DISTINCT tag FROM highlights WHERE tag IS NOT NULL AND tag!='' AND tag NOT IN ('Felsefe','Ekonomi','Strateji','Bilim','Tarih','Psikoloji','Diger') ORDER BY tag",null);
        while(tc.moveToNext()) allTags.add(tc.getString(0)); tc.close();
        HorizontalScrollView hs=new HorizontalScrollView(this);
        LinearLayout tagRow=new LinearLayout(this); tagRow.setOrientation(LinearLayout.HORIZONTAL);
        Button[]tagBtns=new Button[allTags.size()];
        for(int i=0;i<allTags.size();i++){
            String t=allTags.get(i); Button tb=new Button(this);
            tb.setText(t);tb.setTextSize(10);tb.setTextColor(0xFFFFFFFF);tb.setBackgroundColor(0x446A1B9A);tb.setPadding(16,6,16,6);
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
            ContentValues cv=new ContentValues();
            cv.put("pdf_uri",pdfUri);cv.put("page",currentPage);cv.put("color",color);cv.put("note",noteIn.getText().toString());cv.put("tag",ft);
            db.insert("highlights",null,cv);Toast.makeText(this,"Kaydedildi",Toast.LENGTH_SHORT).show();
        });
        b.setNegativeButton("İptal",null);b.show();
    }

    class DBHelper extends SQLiteOpenHelper{
        DBHelper(){super(PdfViewerActivity.this,"goblith.db",null,7);}
        @Override public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, tool_type INTEGER DEFAULT 0, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, path_data TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");
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
        }
    }

    @Override protected void onDestroy(){
        super.onDestroy();isFastScrolling=false;fastScrollHandler.removeCallbacksAndMessages(null);
        try{if(pdfRenderer!=null)pdfRenderer.close();if(fileDescriptor!=null)fileDescriptor.close();}catch(IOException e){e.printStackTrace();}
    }
}
