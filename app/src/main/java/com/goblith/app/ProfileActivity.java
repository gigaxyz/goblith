package com.goblith.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

public class ProfileActivity extends Activity {
    private static final int PICK_IMAGE = 101;
    private FirebaseUser user;
    private FirebaseFirestore db;
    private android.database.sqlite.SQLiteDatabase localDb;
    private ImageView photoView;
    private TextView nameView, bioView;
    private LinearLayout statsContainer, badgeContainer;
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        user = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        localDb = GoblithApp.getDb();

        int bgColor = prefs.getInt("bg_color", 0xFF0F0E17);
        int accentColor = prefs.getInt("accent_color", 0xFF7C3AED);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bgColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setPadding(24, 0, 24, 64);

        // Üst bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(0, 48, 0, 24);

        TextView backBtn = new TextView(this);
        backBtn.setText("←");
        backBtn.setTextColor(0xFF9B8EC4);
        backBtn.setTextSize(24);
        backBtn.setPadding(0, 0, 16, 0);
        backBtn.setOnClickListener(v -> finish());
        topBar.addView(backBtn);

        TextView titleTv = new TextView(this);
        titleTv.setText("Profil");
        titleTv.setTextColor(0xFFE2D9F3);
        titleTv.setTextSize(20);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        topBar.addView(titleTv);

        TextView settingsBtn = new TextView(this);
        settingsBtn.setText("⚙");
        settingsBtn.setTextColor(0xFF9B8EC4);
        settingsBtn.setTextSize(22);
        settingsBtn.setOnClickListener(v -> showSettingsMenu());
        topBar.addView(settingsBtn);
        root.addView(topBar);

        // Profil kartı
        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setOrientation(LinearLayout.VERTICAL);
        profileCard.setGravity(Gravity.CENTER);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1A1831);
        cardBg.setCornerRadius(24);
        profileCard.setBackground(cardBg);
        profileCard.setPadding(32, 40, 32, 40);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, 20);
        profileCard.setLayoutParams(cardLp);

        // Fotoğraf frame
        FrameLayout photoFrame = new FrameLayout(this);
        int photoSize = dp(100);
        LinearLayout.LayoutParams pfLp = new LinearLayout.LayoutParams(photoSize + dp(8), photoSize + dp(8));
        pfLp.gravity = Gravity.CENTER_HORIZONTAL;
        pfLp.setMargins(0, 0, 0, 20);
        photoFrame.setLayoutParams(pfLp);

        View glowRing = new View(this);
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setStroke(dp(3), accentColor);
        glowRing.setBackground(glow);
        glowRing.setLayoutParams(new FrameLayout.LayoutParams(photoSize + dp(8), photoSize + dp(8)));
        photoFrame.addView(glowRing);

        photoView = new ImageView(this);
        GradientDrawable photoCircle = new GradientDrawable();
        photoCircle.setShape(GradientDrawable.OVAL);
        photoCircle.setColor(0xFF2D2B55);
        photoView.setBackground(photoCircle);
        photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams photoLp = new FrameLayout.LayoutParams(photoSize, photoSize);
        photoLp.gravity = Gravity.CENTER;
        photoView.setLayoutParams(photoLp);
        photoView.setOnClickListener(v -> pickPhoto());
        photoFrame.addView(photoView);

        TextView camBtn = new TextView(this);
        camBtn.setText("📷");
        camBtn.setTextSize(14);
        camBtn.setGravity(Gravity.CENTER);
        GradientDrawable camBg = new GradientDrawable();
        camBg.setShape(GradientDrawable.OVAL);
        camBg.setColor(accentColor);
        camBtn.setBackground(camBg);
        FrameLayout.LayoutParams camLp = new FrameLayout.LayoutParams(dp(28), dp(28));
        camLp.gravity = Gravity.BOTTOM | Gravity.END;
        camBtn.setLayoutParams(camLp);
        camBtn.setOnClickListener(v -> pickPhoto());
        photoFrame.addView(camBtn);
        profileCard.addView(photoFrame);

        // Baş harf avatarı göster
        showInitial(accentColor);

        // İsim
        nameView = new TextView(this);
        nameView.setText(getUserName());
        nameView.setTextColor(0xFFE2D9F3);
        nameView.setTextSize(22);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        nameView.setOnClickListener(v -> editName());
        profileCard.addView(nameView);

        TextView editHint = new TextView(this);
        editHint.setText("✎ düzenle");
        editHint.setTextColor(0xFF4A4560);
        editHint.setTextSize(11);
        editHint.setGravity(Gravity.CENTER);
        editHint.setPadding(0, 4, 0, 8);
        editHint.setOnClickListener(v -> editName());
        profileCard.addView(editHint);

        if (user != null && user.getEmail() != null) {
            TextView emailTv = new TextView(this);
            emailTv.setText(user.getEmail());
            emailTv.setTextColor(0xFF9B8EC4);
            emailTv.setTextSize(13);
            emailTv.setGravity(Gravity.CENTER);
            emailTv.setPadding(0, 4, 0, 12);
            profileCard.addView(emailTv);
        }

        bioView = new TextView(this);
        bioView.setText("✎ Bio ekle...");
        bioView.setTextColor(0xFF4A4560);
        bioView.setTextSize(13);
        bioView.setGravity(Gravity.CENTER);
        bioView.setPadding(16, 8, 16, 16);
        bioView.setOnClickListener(v -> editBio());
        profileCard.addView(bioView);

        TextView statusTv = new TextView(this);
        if (user != null && !user.isAnonymous()) {
            statusTv.setText("✓ Google ile giriş yapıldı");
            statusTv.setTextColor(0xFF4ADE80);
        } else {
            statusTv.setText("⚠ Misafir");
            statusTv.setTextColor(0xFFFBBF24);
        }
        statusTv.setTextSize(12);
        statusTv.setGravity(Gravity.CENTER);
        profileCard.addView(statusTv);
        root.addView(profileCard);

        // İstatistikler
        root.addView(makeSectionTitle("İSTATİSTİKLER"));
        statsContainer = new LinearLayout(this);
        statsContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.setMargins(0, 0, 0, 20);
        statsContainer.setLayoutParams(statsLp);
        root.addView(statsContainer);

        // Rozetler
        root.addView(makeSectionTitle("ROZETLER"));
        badgeContainer = new LinearLayout(this);
        badgeContainer.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.HorizontalScrollView hSv = new android.widget.HorizontalScrollView(this);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.setMargins(0, 0, 0, 20);
        hSv.setLayoutParams(hLp);
        hSv.addView(badgeContainer);
        root.addView(hSv);

        // Hesap
        root.addView(makeSectionTitle("HESAP"));
        if (user != null && !user.isAnonymous()) {
            root.addView(makeRow("🔄 Verileri Senkronize Et", () -> {
                Toast.makeText(this, "Senkronizasyon başlatıldı...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try {
                        SyncManager sync = new SyncManager(this, GoblithApp.getDb());
                        sync.syncAll();
                        runOnUiThread(() -> Toast.makeText(this, "✓ Senkronizasyon tamamlandı", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            }));
        } else {
            root.addView(makeRow("🔐 Google ile Giriş Yap", () -> {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }));
        }
        root.addView(makeRow("🚪 Çıkış Yap", () ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("Çıkış Yap")
                .setMessage("Hesabınızdan çıkmak istiyor musunuz?")
                .setPositiveButton("Çıkış", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .setNegativeButton("İptal", null).show()));

        root.addView(makeSectionTitle("VERİLER"));
        root.addView(makeRow("🗑 Yerel Verileri Temizle", () ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("Emin misiniz?")
                .setMessage("Tüm yerel veriler silinecek.")
                .setPositiveButton("Sil", (d, w) -> { deleteDatabase("goblith.db"); finishAffinity(); })
                .setNegativeButton("İptal", null).show()));

        if (user != null && !user.isAnonymous()) {
            root.addView(makeRow("❌ Hesabı Kalıcı Sil", () ->
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Hesabı Sil")
                    .setMessage("Hesabınız kalıcı olarak silinecek.")
                    .setPositiveButton("Evet, Sil", (d, w) -> deleteAccount())
                    .setNegativeButton("İptal", null).show()));
        }

        TextView ver = new TextView(this);
        ver.setText("Goblith v1.0");
        ver.setTextColor(0xFF2D2B55);
        ver.setTextSize(11);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, 32, 0, 0);
        root.addView(ver);

        sv.addView(root);
        setContentView(sv);

        // Ağır işlemleri background'da yap
        new Thread(() -> {
            int[] stats = getStats();
            Bitmap photo = loadPhotoSync();
            runOnUiThread(() -> {
                statsContainer.addView(makeStatCard("📚", String.valueOf(stats[0]), "Kitap", accentColor));
                statsContainer.addView(makeStatCard("📄", String.valueOf(stats[1]), "Sayfa", accentColor));
                statsContainer.addView(makeStatCard("💎", String.valueOf(stats[2]), "Alıntı", accentColor));
                showBadges(stats[0], stats[1], stats[2]);
                if (photo != null) photoView.setImageBitmap(toRoundBitmap(photo));
            });
        }).start();

        loadBio();
    }

    @Override
    public void finish() {
        super.finish();
    }

    private int[] getStats() {
        int archiveCount = 0, bookCount = 0, totalPages = 0;
        if (localDb != null) {
            try { android.database.Cursor c = localDb.rawQuery("SELECT COUNT(*) FROM archive", null); if (c.moveToFirst()) archiveCount = c.getInt(0); c.close(); } catch (Exception ignored) {}
            try { android.database.Cursor c = localDb.rawQuery("SELECT COUNT(*), COALESCE(SUM(last_page),0) FROM library", null); if (c.moveToFirst()) { bookCount = c.getInt(0); totalPages = c.getInt(1); } c.close(); } catch (Exception ignored) {}
        }
        return new int[]{bookCount, totalPages, archiveCount};
    }

    private Bitmap loadPhotoSync() {
        if (user == null || user.isAnonymous()) return null;
        // Google fotoğrafı
        if (user.getPhotoUrl() != null) {
            try {
                return BitmapFactory.decodeStream(new java.net.URL(user.getPhotoUrl().toString()).openStream());
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void showBadges(int bookCount, int totalPages, int archiveCount) {
        if (bookCount >= 1) addBadge("📖", "İlk Kitap");
        if (bookCount >= 5) addBadge("📚", "Kitapsever");
        if (totalPages >= 100) addBadge("📄", "100 Sayfa");
        if (totalPages >= 500) addBadge("🔥", "500 Sayfa");
        if (archiveCount >= 10) addBadge("💎", "10 Alıntı");
        if (archiveCount >= 50) addBadge("🏆", "50 Alıntı");
        if (badgeContainer.getChildCount() == 0) addBadge("🌱", "Yeni Başlayan");
    }

    private void showInitial(int accentColor) {
        String name = getUserName();
        String initial = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
        int size = dp(100);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(accentColor);
        c.drawCircle(size/2f, size/2f, size/2f, p);
        p.setColor(Color.WHITE);
        p.setTextSize(dp(40));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText(initial, size/2f, size/2f + dp(15), p);
        photoView.setImageBitmap(bmp);
    }

    private void pickPhoto() {
        if (user == null || user.isAnonymous()) {
            Toast.makeText(this, "Fotoğraf için giriş yapın", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("image/*");
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE && res == RESULT_OK && data != null) uploadPhoto(data.getData());
    }

    private void uploadPhoto(Uri uri) {
        Toast.makeText(this, "Yükleniyor...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, 256, 256, true);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                String b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT);
                Map<String, Object> d = new HashMap<>();
                d.put("photo_base64", b64);
                db.collection("users").document(user.getUid())
                    .set(d, SetOptions.merge())
                    .addOnSuccessListener(v -> runOnUiThread(() -> {
                        photoView.setImageBitmap(toRoundBitmap(scaled));
                        Toast.makeText(this, "Fotoğraf güncellendi ✓", Toast.LENGTH_SHORT).show();
                    }))
                    .addOnFailureListener(e -> runOnUiThread(() ->
                        Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show()));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadBio() {
        if (user == null || user.isAnonymous()) return;
        db.collection("users").document(user.getUid()).get()
            .addOnSuccessListener(doc -> {
                if (doc != null) {
                    String bio = doc.getString("bio");
                    if (bio != null && !bio.isEmpty()) {
                        bioView.setText(bio);
                        bioView.setTextColor(0xFF9B8EC4);
                    }
                    String b64 = doc.getString("photo_base64");
                    if (b64 != null && !b64.isEmpty()) {
                        try {
                            byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bmp != null) photoView.setImageBitmap(toRoundBitmap(bmp));
                        } catch (Exception ignored) {}
                    }
                }
            });
    }

    private void editName() {
        EditText et = new EditText(this);
        et.setText(getUserName());
        et.setPadding(32, 24, 32, 24);
        new android.app.AlertDialog.Builder(this)
            .setTitle("İsim Değiştir")
            .setView(et)
            .setPositiveButton("Kaydet", (d, w) -> {
                String n = et.getText().toString().trim();
                if (n.isEmpty()) return;
                if (user != null) {
                    user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(n).build())
                        .addOnSuccessListener(v -> { nameView.setText(n); Toast.makeText(this, "İsim güncellendi ✓", Toast.LENGTH_SHORT).show(); });
                }
            })
            .setNegativeButton("İptal", null).show();
    }

    private void editBio() {
        EditText et = new EditText(this);
        String cur = bioView.getText().toString();
        if (!cur.equals("✎ Bio ekle...")) et.setText(cur);
        et.setHint("Kendinizi tanıtın...");
        et.setPadding(32, 24, 32, 24);
        new android.app.AlertDialog.Builder(this)
            .setTitle("Bio Düzenle")
            .setView(et)
            .setPositiveButton("Kaydet", (d, w) -> {
                String bio = et.getText().toString().trim();
                bioView.setText(bio.isEmpty() ? "✎ Bio ekle..." : bio);
                bioView.setTextColor(bio.isEmpty() ? 0xFF4A4560 : 0xFF9B8EC4);
                if (user != null && !user.isAnonymous()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("bio", bio);
                    db.collection("users").document(user.getUid()).set(data, SetOptions.merge());
                }
            })
            .setNegativeButton("İptal", null).show();
    }

    private void showSettingsMenu() {
        boolean autoSync = prefs.getBoolean("auto_sync", true);
        String syncStatus = autoSync ? "🔄 Otomatik Sync: AÇIK" : "🔄 Otomatik Sync: KAPALI";
        new android.app.AlertDialog.Builder(this)
            .setTitle("Ayarlar")
            .setItems(new String[]{"🎨 Tema Seç", "🔒 Gizlilik", "🔔 Bildirimler", syncStatus},
                (d, w) -> {
                    if (w==0) showThemeDialog();
                    else if (w==1) showPrivacyDialog();
                    else if (w==2) showNotifDialog();
                    else showAutoSyncDialog();
                })
            .show();
    }

    private void showAutoSyncDialog() {
        boolean cur = prefs.getBoolean("auto_sync", true);
        String[] options = {"⏱ Her 5 dakikada", "⏱ Her 15 dakikada", "⏱ Her 30 dakikada", "⏱ Her saat", "🚫 Kapalı"};
        int[] intervals = {5, 15, 30, 60, -1};
        int curInterval = prefs.getInt("sync_interval", 15);
        int sel = 1;
        for (int i = 0; i < intervals.length; i++) if (intervals[i] == curInterval) sel = i;
        final int[] s = {sel};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Otomatik Senkronizasyon")
            .setSingleChoiceItems(options, sel, (d, w) -> s[0] = w)
            .setPositiveButton("Kaydet", (d, w) -> {
                boolean enabled = intervals[s[0]] != -1;
                prefs.edit()
                    .putBoolean("auto_sync", enabled)
                    .putInt("sync_interval", intervals[s[0]])
                    .apply();
                if (enabled) {
                    scheduleAutoSync(intervals[s[0]]);
                    Toast.makeText(this, "✓ Otomatik sync her " + intervals[s[0]] + " dakikada çalışacak", Toast.LENGTH_SHORT).show();
                } else {
                    cancelAutoSync();
                    Toast.makeText(this, "Otomatik sync kapatıldı", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("İptal", null).show();
    }

    private void scheduleAutoSync(int intervalMinutes) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            android.content.Intent i = new android.content.Intent("com.goblith.app.AUTO_SYNC");
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this, 0, i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            long interval = intervalMinutes * 60 * 1000L;
            am.setRepeating(android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval, interval, pi);
        } catch (Exception ignored) {}
    }

    private void cancelAutoSync() {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            android.content.Intent i = new android.content.Intent("com.goblith.app.AUTO_SYNC");
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this, 0, i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        } catch (Exception ignored) {}
    }

    private void showThemeDialog() {
        String[] themes = {"🟣 Karanlık Mor", "⚫ Tam Siyah", "🔵 Lacivert", "🟢 Koyu Yeşil"};
        String[] keys = {"dark_purple","pure_black","navy","dark_green"};
        int[] accents = {0xFF7C3AED, 0xFFFFFFFF, 0xFF3B82F6, 0xFF22C55E};
        int[] bgs = {0xFF0F0E17, 0xFF000000, 0xFF0A1628, 0xFF0A1A0F};
        String cur = prefs.getString("theme", "dark_purple");
        int sel = 0;
        for (int i=0;i<keys.length;i++) if (keys[i].equals(cur)) sel=i;
        final int[] s = {sel};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Tema Seç")
            .setSingleChoiceItems(themes, sel, (d,w) -> s[0]=w)
            .setPositiveButton("Uygula", (d,w) -> {
                prefs.edit().putString("theme",keys[s[0]]).putInt("accent_color",accents[s[0]]).putInt("bg_color",bgs[s[0]]).apply();
                Toast.makeText(this,"Tema değişti, uygulamayı yeniden açın",Toast.LENGTH_LONG).show();
                finish();
            })
            .setNegativeButton("İptal",null).show();
    }

    private void showPrivacyDialog() {
        String[] options = {"🌍 Herkese Açık","👥 Sadece Arkadaşlar","🔒 Gizli"};
        int cur = prefs.getInt("privacy",0);
        final int[] s = {cur};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Profil Gizliliği")
            .setSingleChoiceItems(options,cur,(d,w)->s[0]=w)
            .setPositiveButton("Kaydet",(d,w)->{
                prefs.edit().putInt("privacy",s[0]).apply();
                Toast.makeText(this,"Kaydedildi",Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("İptal",null).show();
    }

    private void showNotifDialog() {
        String[] options = {"📖 Günlük okuma hatırlatıcısı", "💬 Topluluk bildirimleri"};
        boolean[] checked = {prefs.getBoolean("notif_reading", true), prefs.getBoolean("notif_community", true)};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Bildirim Ayarları")
            .setMultiChoiceItems(options, checked, (d, w, c) -> checked[w] = c)
            .setPositiveButton("Kaydet", (d, w) -> {
                prefs.edit()
                    .putBoolean("notif_reading", checked[0])
                    .putBoolean("notif_community", checked[1])
                    .apply();
                if (checked[0]) scheduleReadingReminder();
                else cancelReadingReminder();
                Toast.makeText(this, "✓ Bildirim ayarları kaydedildi", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("İptal", null).show();
    }

    private void scheduleReadingReminder() {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            android.content.Intent i = new android.content.Intent("com.goblith.app.READING_REMINDER");
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this, 1, i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            // Her gün akşam 20:00'de hatırlat
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 20);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            if (cal.getTimeInMillis() < System.currentTimeMillis())
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            am.setRepeating(android.app.AlarmManager.RTC_WAKEUP,
                cal.getTimeInMillis(), android.app.AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) {}
    }

    private void cancelReadingReminder() {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            android.content.Intent i = new android.content.Intent("com.goblith.app.READING_REMINDER");
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this, 1, i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        } catch (Exception ignored) {}
    }

    private void deleteAccount() {
        if (user==null) return;
        db.collection("users").document(user.getUid()).delete();
        user.delete().addOnSuccessListener(v->{
            deleteDatabase("goblith.db");
            Intent i = new Intent(this,LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }).addOnFailureListener(e->Toast.makeText(this,"Hata: "+e.getMessage(),Toast.LENGTH_LONG).show());
    }

    private Bitmap toRoundBitmap(Bitmap bmp) {
        int size = Math.min(bmp.getWidth(),bmp.getHeight());
        Bitmap out = Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawCircle(size/2f,size/2f,size/2f,p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        c.drawBitmap(bmp,0,0,p);
        return out;
    }

    private void addBadge(String emoji, String label) {
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(16,16,16,16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2D2B55); bg.setCornerRadius(16);
        badge.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,12,0); badge.setLayoutParams(lp);
        TextView e = new TextView(this); e.setText(emoji); e.setTextSize(28); e.setGravity(Gravity.CENTER); badge.addView(e);
        TextView l = new TextView(this); l.setText(label); l.setTextColor(0xFF9B8EC4); l.setTextSize(10); l.setGravity(Gravity.CENTER); badge.addView(l);
        badgeContainer.addView(badge);
    }

    private View makeStatCard(String emoji, String value, String label, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(24,24,24,24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2D2B55); bg.setCornerRadius(20);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);
        lp.setMargins(0,0,8,0); card.setLayoutParams(lp);
        TextView ev = new TextView(this); ev.setText(emoji); ev.setTextSize(24); ev.setGravity(Gravity.CENTER); card.addView(ev);
        TextView vv = new TextView(this); vv.setText(value); vv.setTextColor(0xFFE2D9F3); vv.setTextSize(22); vv.setTypeface(null,Typeface.BOLD); vv.setGravity(Gravity.CENTER); card.addView(vv);
        TextView lv = new TextView(this); lv.setText(label); lv.setTextColor(0xFF9B8EC4); lv.setTextSize(11); lv.setGravity(Gravity.CENTER); card.addView(lv);
        return card;
    }

    private View makeRow(String text, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(24,20,24,20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A1831); bg.setCornerRadius(16);
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,10); row.setLayoutParams(lp);
        TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(0xFFE2D9F3); tv.setTextSize(14);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); row.addView(tv);
        TextView ar = new TextView(this); ar.setText("›"); ar.setTextColor(0xFF9B8EC4); ar.setTextSize(20); row.addView(ar);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private TextView makeSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(0xFF9B8EC4); tv.setTextSize(11);
        tv.setTypeface(null,Typeface.BOLD); tv.setPadding(0,24,0,12);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private String getUserName() {
        if (user!=null && user.getDisplayName()!=null && !user.getDisplayName().isEmpty()) return user.getDisplayName();
        return "Misafir";
    }

    private int dp(int dp) { return (int)(dp*getResources().getDisplayMetrics().density); }
}
