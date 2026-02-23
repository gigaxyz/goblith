package com.goblith.app;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Bundle;
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

public class ArchiveActivity extends AppCompatActivity {

    private SQLiteDatabase db;
    private LinearLayout listContainer;
    private String filterTopic = "";
    private int filterImportance = 0; // 0=hepsi

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Başlık
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setPadding(24, 32, 24, 8);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("📁 ARŞİV");
        title.setTextColor(0xFFE94560);
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleBar.addView(title);

        TextView countView = new TextView(this);
        countView.setTextColor(0xFF888888);
        countView.setTextSize(13);
        countView.setTag("count");
        titleBar.addView(countView);
        root.addView(titleBar);

        // Filtre satırı
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(16, 0, 16, 12);

        Button btnAll   = makeFilterBtn("Tümü",     true);
        Button btnHigh  = makeFilterBtn("⭐⭐⭐ Yüksek", false);
        Button btnMid   = makeFilterBtn("⭐⭐ Orta",     false);
        Button btnLow   = makeFilterBtn("⭐ Düşük",     false);
        Button btnSearch= makeFilterBtn("🔍 Ara",       false);

        Button[] fBtns={btnAll,btnHigh,btnMid,btnLow,btnSearch};

        filterRow.addView(wrapFlex(btnAll,0));
        filterRow.addView(wrapFlex(btnHigh,6));
        filterRow.addView(wrapFlex(btnMid,6));
        filterRow.addView(wrapFlex(btnLow,6));
        filterRow.addView(wrapFlex(btnSearch,6));
        root.addView(filterRow);

        ScrollView sv = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(16, 0, 16, 32);
        sv.addView(listContainer);
        root.addView(sv);

        setContentView(root);
        loadArchive();

        btnAll.setOnClickListener(v    -> { filterImportance=0; filterTopic=""; activateFilter(fBtns,0); loadArchive(); });
        btnHigh.setOnClickListener(v   -> { filterImportance=3; filterTopic=""; activateFilter(fBtns,1); loadArchive(); });
        btnMid.setOnClickListener(v    -> { filterImportance=2; filterTopic=""; activateFilter(fBtns,2); loadArchive(); });
        btnLow.setOnClickListener(v    -> { filterImportance=1; filterTopic=""; activateFilter(fBtns,3); loadArchive(); });
        btnSearch.setOnClickListener(v -> {
            AlertDialog.Builder b=new AlertDialog.Builder(this);
            b.setTitle("Konuya Göre Ara");
            EditText in=new EditText(this); in.setHint("Konu adı...");
            b.setView(in);
            b.setPositiveButton("Ara",(d,w)->{filterTopic=in.getText().toString().trim();filterImportance=0;activateFilter(fBtns,4);loadArchive();});
            b.setNegativeButton("İptal",null); b.show();
        });
    }

    private void activateFilter(Button[] btns, int idx) {
        for (Button b:btns) b.setBackgroundColor(0xFF16213E);
        btns[idx].setBackgroundColor(0xFFE94560);
    }

    private void loadArchive() {
        listContainer.removeAllViews();

        String sql="SELECT id,book_name,page,quote,topic,importance,created_at FROM archive";
        List<String> args=new java.util.ArrayList<>();
        List<String> conds=new java.util.ArrayList<>();

        if (filterImportance>0) { conds.add("importance=?"); args.add(String.valueOf(filterImportance)); }
        if (!filterTopic.isEmpty()) { conds.add("topic LIKE ?"); args.add("%"+filterTopic+"%"); }
        if (!conds.isEmpty()) sql+=" WHERE "+android.text.TextUtils.join(" AND ",conds);
        sql+=" ORDER BY importance DESC, created_at DESC";

        Cursor c=db.rawQuery(sql,args.toArray(new String[0]));

        // Sayaç
        TextView countView=(TextView)((LinearLayout)((LinearLayout)listContainer.getParent().getParent()).getChildAt(0)).findViewWithTag("count");
        if(countView!=null) countView.setText(c.getCount()+" kayıt");

        if (c.getCount()==0) {
            TextView empty=new TextView(this);
            empty.setText("Arşiv boş.\nPDF okurken 📁 ARŞİV butonunu kullan.");
            empty.setTextColor(0xFF555555); empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER); empty.setPadding(24,48,24,24);
            listContainer.addView(empty); c.close(); return;
        }

        while(c.moveToNext()) {
            long   id         = c.getLong(0);
            String bookName   = c.getString(1);
            int    page       = c.getInt(2);
            String quote      = c.getString(3);
            String topic      = c.getString(4);
            int    importance = c.getInt(5);
            String date       = c.getString(6);

            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF16213E);
            card.setPadding(20,16,20,16);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0,0,0,12); card.setLayoutParams(cp);

            // Önem yıldızları + kaynak satırı
            LinearLayout topRow=new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView stars=new TextView(this);
            String starStr=importance>=3?"⭐⭐⭐":importance==2?"⭐⭐":"⭐";
            stars.setText(starStr);
            stars.setTextSize(13);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.setMargins(0,0,10,0); stars.setLayoutParams(sp);
            topRow.addView(stars);

            TextView src=new TextView(this);
            src.setText((bookName!=null?bookName:"?")+", s."+(page+1));
            src.setTextColor(0xFF4488FF); src.setTextSize(13);
            src.setTypeface(null,Typeface.BOLD);
            src.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            topRow.addView(src);

            // Kitaba git butonu
            Button btnGo=new Button(this);
            btnGo.setText("→ Kitap");
            btnGo.setBackgroundColor(0xFF0F3460);
            btnGo.setTextColor(0xFFFFFFFF); btnGo.setTextSize(10); btnGo.setPadding(10,4,10,4);
            topRow.addView(btnGo);
            card.addView(topRow);

            // Alıntı
            TextView quoteView=new TextView(this);
            quoteView.setText("""+quote+""");
            quoteView.setTextColor(0xFFDDDDDD); quoteView.setTextSize(15);
            quoteView.setPadding(0,10,0,8); quoteView.setLineSpacing(4,1.3f);
            card.addView(quoteView);

            // Konu + tarih
            LinearLayout bottomRow=new LinearLayout(this);
            bottomRow.setOrientation(LinearLayout.HORIZONTAL);
            bottomRow.setGravity(Gravity.CENTER_VERTICAL);

            if(topic!=null&&!topic.isEmpty()){
                TextView topicTag=new TextView(this);
                topicTag.setText(" "+topic+" ");
                topicTag.setTextColor(0xFFFFFFFF); topicTag.setTextSize(10);
                topicTag.setTypeface(null,Typeface.BOLD);
                topicTag.setBackgroundColor(0xFF6A1B9A); topicTag.setPadding(10,4,10,4);
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
                tp.setMargins(0,0,8,0); topicTag.setLayoutParams(tp);
                bottomRow.addView(topicTag);
            }

            TextView dateView=new TextView(this);
            dateView.setText(date!=null&&date.length()>=10?date.substring(0,10):"");
            dateView.setTextColor(0xFF555566); dateView.setTextSize(11);
            dateView.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
            bottomRow.addView(dateView);

            Button btnDel=new Button(this);
            btnDel.setText("✕"); btnDel.setBackgroundColor(0xFF333333);
            btnDel.setTextColor(0xFF888888); btnDel.setTextSize(11); btnDel.setPadding(12,4,12,4);
            bottomRow.addView(btnDel);
            card.addView(bottomRow);
            listContainer.addView(card);

            // Kitaba git
            final long fId=id;
            Cursor uc=db.rawQuery("SELECT pdf_uri FROM archive WHERE id=?",new String[]{String.valueOf(id)});
            final String fUri=uc.moveToFirst()?uc.getString(0):null; uc.close();
            final int fPage=page;
            btnGo.setOnClickListener(v->{
                if(fUri==null){Toast.makeText(this,"Kaynak bulunamadı",Toast.LENGTH_SHORT).show();return;}
                Intent i=new Intent(this,PdfViewerActivity.class);
                i.putExtra("pdfUri",fUri); i.putExtra("startPage",fPage); i.putExtra("fileType","PDF");
                startActivity(i);
            });

            btnDel.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Sil").setMessage("Bu arşiv kaydı silinsin mi?")
                .setPositiveButton("Sil",(d,w)->{db.delete("archive","id=?",new String[]{String.valueOf(fId)});loadArchive();})
                .setNegativeButton("İptal",null).show());
        }
        c.close();
    }

    private Button makeFilterBtn(String t,boolean active){
        Button b=new Button(this); b.setText(t);
        b.setBackgroundColor(active?0xFFE94560:0xFF16213E);
        b.setTextColor(0xFFFFFFFF); b.setTextSize(10);
        b.setTypeface(null,Typeface.BOLD); b.setPadding(6,8,6,8); return b;
    }

    private Button wrapFlex(Button b,int lm){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        lp.setMargins(lm,0,0,0); b.setLayoutParams(lp); return b;
    }

    // Java'nın List'i için
    private <T> java.util.List<T> List(){return new java.util.ArrayList<>();}

    class DBHelper extends SQLiteOpenHelper{
        DBHelper(){super(ArchiveActivity.this,"goblith.db",null,7);}
        @Override public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, created_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db,int o,int n){
            try{db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, created_at TEXT)");}catch(Exception e){}
        }
    }
}
