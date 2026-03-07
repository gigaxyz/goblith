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
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
        } catch (Exception ignored) {}
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(ex);
            } catch (Exception ignored) {}
            android.util.Log.e("GOBLITH_CRASH", "CRASH", ex);
            try {
                getSharedPreferences("crash", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", ex.toString() + "\n" + android.util.Log.getStackTraceString(ex))
                    .apply();
            } catch (Exception ignored) {}
            android.os.Process.killProcess(android.os.Process.myPid());
        });
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

        public static void createTables(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, tool_type INTEGER DEFAULT 0, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, path_data TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, created_at TEXT, source_info TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS pdf_ocr_cache (pdf_uri TEXT, page INTEGER, ocr_text TEXT, PRIMARY KEY(pdf_uri, page))");
            db.execSQL("CREATE TABLE IF NOT EXISTS search_history (id INTEGER PRIMARY KEY AUTOINCREMENT, query TEXT, searched_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS flashcards (id INTEGER PRIMARY KEY AUTOINCREMENT, front TEXT, back TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS word_analysis (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, word TEXT, count INTEGER, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS cross_refs (id INTEGER PRIMARY KEY AUTOINCREMENT, source_uri TEXT, target_uri TEXT, note TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            try { db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE archive ADD COLUMN source_info TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE archive ADD COLUMN book_name TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE page_highlights ADD COLUMN tool_type INTEGER DEFAULT 0"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE page_highlights ADD COLUMN path_data TEXT"); } catch (Exception ignored) {}
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createTables(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
            try { db.execSQL("PRAGMA journal_mode=WAL"); } catch (Exception ignored) {}
            try { db.execSQL("PRAGMA synchronous=NORMAL"); } catch (Exception ignored) {}
        }
    }
}
