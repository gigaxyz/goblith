package com.goblith.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_FILE=1;
    private LinearLayout libraryContainer;
    private SQLiteDatabase db;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        db=new DBHelper().getWritableDatabase();

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        TextView title=new TextView(this);
        title.setText("Goblith"); title.setTextColor(0xFFE94560);
        title.setTextSize(36); title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setPadding(24,48,24,4); root.addView(title);

        TextView subtitle=new TextView(this);
        subtitle.setText("Kişisel Bilgi Sistemi"); subtitle.setTextColor(0xFF888888);
        subtitle.setTextSize(13); subtitle.setPadding(24,0,24,20); root.addView(subtitle);

        addRow(root, new int[]{R.drawable.ic_add,R.drawable.ic_notes}, new String[]{"DOSYA EKLE","ALINTI BANKASI"}, new int[]{0xFFE94560,0xFF0F3460});
        addRow(root, new int[]{R.drawable.ic_words,R.drawable.ic_stats}, new String[]{"KELIME ANALIZI","ISTATISTIKLER"}, new int[]{0xFF6A1B9A,0xFF1A5276});
        addRow(root, new int[]{R.drawable.ic_cross,R.drawable.ic_notes}, new String[]{"CAPRAZ BAGLANTI","FLASHCARD"}, new int[]{0xFF1B5E20,0xFF4A148C});
        addRow(root, new int[]{R.drawable.ic_add,R.drawable.ic_stats}, new String[]{"OKUMA LISTESI","YER IMLERI"}, new int[]{0xFF0D47A1,0xFF37474F});

        TextView libTitle=new TextView(this);
        libTitle.setText("KÜTÜPHANEMm"); libTitle.setTextColor(0xFF888888);
        libTitle.setTextSize(12); libTitle.setTypeface(null,android.graphics.Typeface.BOLD);
        libTitle.setPadding(24,4,24,10); root.addView(libTitle);

        ScrollView sv=new ScrollView(this);
        libraryContainer=new LinearLayout(this);
        libraryContainer.setOrientation(LinearLayout.VERTICAL);
        libraryContainer.setPadding(16,0,16,24);
        sv.addView(libraryContainer); root.addView(sv);

        setContentView(root);
        loadLibrary();

        // Buton referanslarını al
        Button btnAdd=(Button)((LinearLayout)root.getChildAt(3)).getChildAt(0);
        Button btnNotes=(Button)((LinearLayout)root.getChildAt(3)).getChildAt(1);
        Button btnWords=(Button)((LinearLayout)root.getChildAt(4)).getChildAt(0);
        Button btnStats=(Button)((LinearLayout)root.getChildAt(4)).getChildAt(1);
        Button btnCross=(Button)((LinearLayout)root.getChildAt(5)).getChildAt(0);
        Button btnFlash=(Button)((LinearLayout)root.getChildAt(5)).getChildAt(1);
        Button btnReadList=(Button)((LinearLayout)root.getChildAt(6)).getChildAt(0);
        Button btnBookmarks=(Button)((LinearLayout)root.getChildAt(6)).getChildAt(1);

        btnAdd.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","text/plain","application/epub+zip"});i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_FILE);});
        btnNotes.setOnClickListener(v->startActivity(new Intent(this,NotesActivity.class)));
        btnWords.setOnClickListener(v->startActivity(new Intent(this,WordAnalysisActivity.class)));
        btnStats.setOnClickListener(v->startActivity(new Intent(this,StatsActivity.class)));
        btnCross.setOnClickListener(v->startActivity(new Intent(this,CrossRefActivity.class)));
        btnFlash.setOnClickListener(v->startActivity(new Intent(this,FlashcardActivity.class)));
        btnReadList.setOnClickListener(v->startActivity(new Intent(this,ReadingListActivity.class)));
        btnBookmarks.setOnClickListener(v->showAllBookmarks());
    }

    private void addRow(LinearLayout root, int[]icons, String[]texts, int[]colors){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16,0,16,10);
        for(int i=0;i<texts.length;i++){
            Button btn=new Button(this); btn.setText(texts[i]);
            btn.setBackgroundColor(colors[i]); btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(11); btn.setTypeface(null,android.graphics.Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            try{Drawable icon=ContextCompat.getDrawable(this,icons[i]);if(icon!=null){icon.setBounds(0,0,40,40);btn.setCompoundDrawables(null,icon,null,null);btn.setCompoundDrawablePadding(4);}}catch(Exception e){}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
            if(i>0)lp.setMargins(10,0,0,0);
            btn.setLayoutParams(lp); btn.setPadding(8,12,8,12);
            row.addView(btn);
        }
        root.addView(row);
    }

    private void showAllBookmarks(){
        Cursor c=db.rawQuery("SELECT b.page, b.title, l.custom_name, b.pdf_uri FROM bookmarks b LEFT JOIN library l ON b.pdf_uri=l.pdf_uri ORDER BY b.created_at DESC",null);
        if(!c.moveToFirst()){android.widget.Toast.makeText(this,"Henüz yer imi yok",android.widget.Toast.LENGTH_SHORT).show();c.close();return;}
        java.util.List<String> labels=new java.util.ArrayList<>(); java.util.List<String> uris=new java.util.ArrayList<>(); java.util.List<Integer> pages=new java.util.ArrayList<>();
        do{String book=c.getString(2)!=null?c.getString(2):"?";labels.add(book+" — "+c.getString(1));uris.add(c.getString(3));pages.add(c.getInt(0));}while(c.moveToNext());c.close();
        new AlertDialog.Builder(this).setTitle("Tüm Yer İmleri")
            .setItems(labels.toArray(new String[0]),(d,w)->openFile(uris.get(w),pages.get(w),"PDF"))
            .setNegativeButton("Kapat",null).show();
    }

    private String getFileName(Uri uri){String r=null;try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(idx>=0)r=c.getString(idx);}}catch(Exception e){}if(r==null)r=uri.getLastPathSegment();if(r==null)r="Bilinmeyen";int dot=r.lastIndexOf('.');if(dot>0)r=r.substring(0,dot);return r;}
    private String getFileType(Uri uri){String t=getContentResolver().getType(uri);if(t==null)t="";if(t.contains("pdf"))return"PDF";if(t.contains("text"))return"TXT";String p=uri.toString().toLowerCase();if(p.endsWith(".pdf"))return"PDF";if(p.endsWith(".txt"))return"TXT";return"PDF";}

    private void loadLibrary(){
        libraryContainer.removeAllViews();
        Cursor cursor=db.rawQuery("SELECT pdf_uri,custom_name,file_type,last_page,last_opened FROM library ORDER BY last_opened DESC LIMIT 30",null);
        if(cursor.getCount()==0){TextView empty=new TextView(this);empty.setText("Kütüphaneniz boş.\nDOSYA EKLE ile başlayın.");empty.setTextColor(0xFF555555);empty.setTextSize(14);empty.setGravity(Gravity.CENTER);empty.setPadding(32,48,32,32);libraryContainer.addView(empty);cursor.close();return;}
        while(cursor.moveToNext()){
            String uri=cursor.getString(0),name=cursor.getString(1),type=cursor.getString(2);
            int page=cursor.getInt(3);String opened=cursor.getString(4);
            if(type==null)type="PDF";if(name==null||name.isEmpty())name="Bilinmeyen";

            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setBackgroundColor(0xFF16213E);card.setPadding(20,16,20,16);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);cp.setMargins(0,0,0,10);card.setLayoutParams(cp);

            LinearLayout topRow=new LinearLayout(this);topRow.setOrientation(LinearLayout.HORIZONTAL);topRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView typeTag=new TextView(this);typeTag.setText(" "+type+" ");typeTag.setTextColor(0xFFFFFFFF);typeTag.setTextSize(10);typeTag.setTypeface(null,android.graphics.Typeface.BOLD);typeTag.setBackgroundColor(type.equals("PDF")?0xFFE94560:0xFF0F3460);typeTag.setPadding(8,4,8,4);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);tp.setMargins(0,0,10,0);typeTag.setLayoutParams(tp);topRow.addView(typeTag);
            TextView nameView=new TextView(this);nameView.setText(name);nameView.setTextColor(0xFFFFFFFF);nameView.setTextSize(15);nameView.setTypeface(null,android.graphics.Typeface.BOLD);nameView.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));topRow.addView(nameView);
            Button btnRename=new Button(this);btnRename.setText("✎");btnRename.setBackgroundColor(0xFF0F3460);btnRename.setTextColor(0xFFFFFFFF);btnRename.setTextSize(14);btnRename.setPadding(16,4,16,4);topRow.addView(btnRename);
            Button btnDel=new Button(this);btnDel.setText("✕");btnDel.setBackgroundColor(0xFFE94560);btnDel.setTextColor(0xFFFFFFFF);btnDel.setTextSize(14);btnDel.setPadding(16,4,16,4);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);dp.setMargins(6,0,0,0);btnDel.setLayoutParams(dp);topRow.addView(btnDel);card.addView(topRow);
            TextView info=new TextView(this);info.setText("Sayfa "+(page+1)+"  |  "+(opened!=null&&opened.length()>=10?opened.substring(0,10):""));info.setTextColor(0xFF888888);info.setTextSize(12);info.setPadding(0,6,0,0);card.addView(info);
            libraryContainer.addView(card);

            final String fUri=uri,fType=type,fName=name;final int fPage=page;
            card.setOnClickListener(v->openFile(fUri,fPage,fType));
            btnRename.setOnClickListener(v->{AlertDialog.Builder b=new AlertDialog.Builder(this);b.setTitle("Adı Değiştir");EditText input=new EditText(this);input.setText(fName);input.setSelectAllOnFocus(true);b.setView(input);b.setPositiveButton("Kaydet",(d,w)->{ContentValues val=new ContentValues();val.put("custom_name",input.getText().toString().trim());db.update("library",val,"pdf_uri=?",new String[]{fUri});loadLibrary();});b.setNegativeButton("İptal",null);b.show();});
            btnDel.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Kaldır").setMessage("\""+fName+"\" listeden kaldırılsın mı?").setPositiveButton("Kaldır",(d,w)->{db.delete("library","pdf_uri=?",new String[]{fUri});loadLibrary();}).setNegativeButton("İptal",null).show());
        }
        cursor.close();
    }

    private void openFile(String uri,int page,String type){Intent i=new Intent(this,PdfViewerActivity.class);i.putExtra("pdfUri",uri);i.putExtra("startPage",page);i.putExtra("fileType",type);startActivity(i);}

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==PICK_FILE&&res==Activity.RESULT_OK&&data!=null){Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){}ContentValues val=new ContentValues();val.put("pdf_uri",uri.toString());val.put("custom_name",getFileName(uri));val.put("file_type",getFileType(uri));val.put("last_page",0);val.put("last_opened",new java.util.Date().toString());db.insertWithOnConflict("library",null,val,SQLiteDatabase.CONFLICT_REPLACE);openFile(uri.toString(),0,getFileType(uri));}
    }

    @Override protected void onResume(){super.onResume();loadLibrary();}

    class DBHelper extends SQLiteOpenHelper{
        DBHelper(){super(MainActivity.this,"goblith.db",null,6);}
        @Override public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db,int o,int n){
            try{db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT");}catch(Exception e){}
            try{db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT");}catch(Exception e){}
            try{db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");}catch(Exception e){}
            try{db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");}catch(Exception e){}
        }
    }
}
