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
        // Crashlytics başlat
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
        } catch (Exception ignored) {}
        // Tüm crash'leri yakala
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(ex);
            } catch (Exception ignored) {}
            android.util.Log.e("GOBLITH_CRASH", "CRASH", ex);
            // Crash'i SharedPreferences'a kaydet
            try {
                getSharedPreferences("crash", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", ex.toString() + "\n" + android.util.Log.getStackTraceString(ex))
                    .apply();
            } catch (Exception ignored) {}
            android.os.Process.killProcess(android.os.Process.myPid());
        });
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

        public static void createTables(SQLiteDatabase db) {
            createTables(db);
            try { db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE archive ADD COLUMN source_info TEXT"); } catch (Exception ignored) {}
            try { db.execSQL("ALTER TABLE archive ADD COLUMN book_name TEXT"); } catch (Exception ignored) {}
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createTables(db);
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
