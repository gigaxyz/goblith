package com.goblith.app;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class GoblithApp extends Application {
    private static GoblithApp instance;
    private static SQLiteDatabase db;
    private static final Object lock = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // DB'yi uygulama başlangıcında bir kez aç
        try {
            DBManager helper = new DBManager(this);
            db = helper.getWritableDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static GoblithApp get() { return instance; }

    public static SQLiteDatabase getDb() {
        synchronized (lock) {
            if (db == null || !db.isOpen()) {
                try {
                    DBManager helper = new DBManager(instance);
                    db = helper.getWritableDatabase();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return db;
        }
    }

    public static class DBManager extends SQLiteOpenHelper {
        private static final int VERSION = 9;

        public DBManager(android.content.Context ctx) {
            super(ctx, "goblith.db", null, VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, source_info TEXT, created_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, note TEXT, type TEXT, tag TEXT, created_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS pdf_ocr_cache (pdf_uri TEXT, page INTEGER, ocr_text TEXT, blocks TEXT, PRIMARY KEY(pdf_uri, page))");
            db.execSQL("CREATE TABLE IF NOT EXISTS search_history (pdf_uri TEXT, query TEXT, found_page INTEGER, ts DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Tabloları silmeden güncelle
            onCreate(db);
            try { db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE archive ADD COLUMN source_info TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE archive ADD COLUMN book_name TEXT"); } catch (Exception ignored) {}
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            super.onOpen(db);
            db.execSQL("PRAGMA journal_mode=WAL");
            db.execSQL("PRAGMA synchronous=NORMAL");
        }
    }
}
