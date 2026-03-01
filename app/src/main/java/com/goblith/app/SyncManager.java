package com.goblith.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import java.util.*;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private FirebaseFirestore db;
    private FirebaseUser user;
    private SQLiteDatabase localDb;
    private Context ctx;

    public SyncManager(Context ctx, SQLiteDatabase localDb) {
        this.ctx = ctx;
        this.localDb = localDb;
        this.db = FirebaseFirestore.getInstance();
        this.user = FirebaseAuth.getInstance().getCurrentUser();
    }

    public boolean isLoggedIn() { return user != null; }
    public String getUserId() { return user != null ? user.getUid() : null; }
    public String getUserName() { return user != null ? user.getDisplayName() : "Misafir"; }
    public String getUserEmail() { return user != null ? user.getEmail() : ""; }

    // ── YUKARI SYNC (Lokal → Firebase) ───────────────────────────────────────

    public void syncAll() {
        if (!isLoggedIn()) return;
        new Thread(() -> {
            syncLibrary();
            syncBookmarks();
            syncArchive();
            syncNotes();
        }).start();
    }

    public void syncLibrary() {
        if (!isLoggedIn()) return;
        Cursor c = localDb.rawQuery(
            "SELECT pdf_uri, custom_name, file_type, last_page, last_opened FROM library", null);
        while (c.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("pdf_uri", c.getString(0));
            data.put("custom_name", c.getString(1));
            data.put("file_type", c.getString(2));
            data.put("last_page", c.getInt(3));
            data.put("last_opened", c.getString(4));
            data.put("uid", user.getUid());
            data.put("updated_at", new Date());
            String docId = user.getUid() + "_" + Math.abs(c.getString(0).hashCode());
            db.collection("library").document(docId).set(data);
        }
        c.close();
    }

    public void syncBookmarks() {
        if (!isLoggedIn()) return;
        Cursor c = localDb.rawQuery(
            "SELECT pdf_uri, page, title, created_at FROM bookmarks", null);
        while (c.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("pdf_uri", c.getString(0));
            data.put("page", c.getInt(1));
            data.put("title", c.getString(2));
            data.put("created_at", c.getString(3));
            data.put("uid", user.getUid());
            String docId = user.getUid() + "_" + c.getString(0).hashCode() + "_" + c.getInt(1);
            db.collection("bookmarks").document(docId).set(data);
        }
        c.close();
    }

    public void syncArchive() {
        if (!isLoggedIn()) return;
        Cursor c = localDb.rawQuery(
            "SELECT id, pdf_uri, page, quote, topic, importance, source_info, created_at FROM archive", null);
        while (c.moveToNext()) {
            Map<String, Object> data = new HashMap<>();
            data.put("local_id", c.getInt(0));
            data.put("pdf_uri", c.getString(1));
            data.put("page", c.getInt(2));
            data.put("quote", c.getString(3));
            data.put("topic", c.getString(4));
            data.put("importance", c.getInt(5));
            data.put("source_info", c.getString(6));
            data.put("created_at", c.getString(7));
            data.put("uid", user.getUid());
            String docId = user.getUid() + "_archive_" + c.getInt(0);
            db.collection("archive").document(docId).set(data);
        }
        c.close();
    }

    public void syncNotes() {
        if (!isLoggedIn()) return;
        try {
            Cursor c = localDb.rawQuery(
                "SELECT id, pdf_uri, page, note, type, tag, created_at FROM notes", null);
            while (c.moveToNext()) {
                Map<String, Object> data = new HashMap<>();
                data.put("local_id", c.getInt(0));
                data.put("pdf_uri", c.getString(1));
                data.put("page", c.getInt(2));
                data.put("note", c.getString(3));
                data.put("type", c.getString(4));
                data.put("tag", c.getString(5));
                data.put("created_at", c.getString(6));
                data.put("uid", user.getUid());
                String docId = user.getUid() + "_note_" + c.getInt(0);
                db.collection("notes").document(docId).set(data);
            }
            c.close();
        } catch (Exception ignored) {}
    }

    // ── AŞAĞI SYNC (Firebase → Lokal) ────────────────────────────────────────

    public void pullLibrary(Runnable onDone) {
        if (!isLoggedIn()) { if (onDone != null) onDone.run(); return; }
        db.collection("library")
            .whereEqualTo("uid", user.getUid())
            .get()
            .addOnSuccessListener(docs -> {
                for (DocumentSnapshot doc : docs) {
                    ContentValues cv = new ContentValues();
                    cv.put("pdf_uri", doc.getString("pdf_uri"));
                    cv.put("custom_name", doc.getString("custom_name"));
                    cv.put("file_type", doc.getString("file_type"));
                    Long lp = doc.getLong("last_page");
                    cv.put("last_page", lp != null ? lp.intValue() : 0);
                    cv.put("last_opened", doc.getString("last_opened"));
                    localDb.insertWithOnConflict("library", null, cv,
                        SQLiteDatabase.CONFLICT_IGNORE);
                }
                if (onDone != null) onDone.run();
            });
    }

    public void pullArchive(Runnable onDone) {
        if (!isLoggedIn()) { if (onDone != null) onDone.run(); return; }
        db.collection("archive")
            .whereEqualTo("uid", user.getUid())
            .get()
            .addOnSuccessListener(docs -> {
                for (DocumentSnapshot doc : docs) {
                    try {
                        ContentValues cv = new ContentValues();
                        cv.put("pdf_uri", doc.getString("pdf_uri"));
                        Long page = doc.getLong("page");
                        cv.put("page", page != null ? page.intValue() : 0);
                        cv.put("quote", doc.getString("quote"));
                        cv.put("topic", doc.getString("topic"));
                        Long imp = doc.getLong("importance");
                        cv.put("importance", imp != null ? imp.intValue() : 1);
                        cv.put("source_info", doc.getString("source_info"));
                        cv.put("created_at", doc.getString("created_at"));
                        localDb.insertWithOnConflict("archive", null, cv,
                            SQLiteDatabase.CONFLICT_IGNORE);
                    } catch (Exception ignored) {}
                }
                if (onDone != null) onDone.run();
            });
    }

    // ── TOPLULUK ──────────────────────────────────────────────────────────────

    public void shareArchiveEntry(int localId, Runnable onSuccess) {
        if (!isLoggedIn()) return;
        Cursor c = localDb.rawQuery(
            "SELECT pdf_uri, page, quote, topic, importance, source_info FROM archive WHERE id=?",
            new String[]{String.valueOf(localId)});
        if (c.moveToFirst()) {
            Map<String, Object> data = new HashMap<>();
            data.put("uid", user.getUid());
            data.put("user_name", user.getDisplayName());
            data.put("pdf_uri", c.getString(0));
            data.put("page", c.getInt(1));
            data.put("quote", c.getString(2));
            data.put("topic", c.getString(3));
            data.put("importance", c.getInt(4));
            data.put("source_info", c.getString(5));
            data.put("shared_at", new Date());
            data.put("likes", 0);
            db.collection("community_archive").add(data)
                .addOnSuccessListener(ref -> { if (onSuccess != null) onSuccess.run(); });
        }
        c.close();
    }

    public void getCommunityArchive(String topic, OnArchiveListener listener) {
        Query q = db.collection("community_archive").orderBy("shared_at", Query.Direction.DESCENDING).limit(50);
        if (topic != null && !topic.isEmpty()) q = q.whereEqualTo("topic", topic);
        q.get().addOnSuccessListener(docs -> {
            List<Map<String, Object>> results = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                Map<String, Object> item = doc.getData();
                if (item != null) { item.put("doc_id", doc.getId()); results.add(item); }
            }
            listener.onResult(results);
        });
    }

    public interface OnArchiveListener {
        void onResult(List<Map<String, Object>> items);
    }
}
