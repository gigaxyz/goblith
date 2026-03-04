package com.goblith.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import java.util.*;

public class ProfileActivity extends Activity {
    private static final int PICK_IMAGE = 101;
    private FirebaseUser user;
    private FirebaseFirestore firestore;
    private android.database.sqlite.SQLiteDatabase db;
    private ImageView photoView;
    private TextView nameView, bioView;
    private LinearLayout badgeContainer, statsContainer;
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            getSharedPreferences("crash", MODE_PRIVATE).edit()
                .putString("profile_crash", ex.toString() + "\n" + android.util.Log.getStackTraceString(ex)).apply();
            finish();
        });

        android.content.SharedPreferences crashPrefs = getSharedPreferences("crash", MODE_PRIVATE);
        String crash = crashPrefs.getString("profile_crash", null);
        if (crash != null) {
            crashPrefs.edit().remove("profile_crash").apply();
            new android.app.AlertDialog.Builder(this)
                .setTitle("Hata")
                .setMessage(crash.length() > 600 ? crash.substring(0, 600) : crash)
                .setPositiveButton("Tamam", null).show();
        }

        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();

        try { db = openOrCreateDatabase("goblith.db", MODE_PRIVATE, null); }
        catch (Exception e) { db = null; }

        int bgColor = prefs.getInt("bg_color", 0xFF0F0E17);
        int accentColor = prefs.getInt("accent_color", 0xFF7C3AED);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bgColor);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setPadding(24, 0, 24, 48);

        // ── Üst bar ──────────────────────────────────────────────────────────
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
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleTv.setLayoutParams(titleLp);
        topBar.addView(titleTv);

        TextView settingsBtn = new TextView(this);
        settingsBtn.setText("⚙");
        settingsBtn.setTextColor(0xFF9B8EC4);
        settingsBtn.setTextSize(22);
        settingsBtn.setOnClickListener(v -> showSettingsMenu(accentColor));
        topBar.addView(settingsBtn);
        root.addView(topBar);

        // ── Profil kartı ─────────────────────────────────────────────────────
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

        // Profil fotoğrafı
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
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(photoSize + dp(8), photoSize + dp(8));
        glowRing.setBackground(glow);
        glowRing.setLayoutParams(glowLp);
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

        TextView cameraBtn = new TextView(this);
        cameraBtn.setText("📷");
        cameraBtn.setTextSize(14);
        cameraBtn.setGravity(Gravity.CENTER);
        GradientDrawable camBg = new GradientDrawable();
        camBg.setShape(GradientDrawable.OVAL);
        camBg.setColor(accentColor);
        cameraBtn.setBackground(camBg);
        int camSize = dp(28);
        FrameLayout.LayoutParams camLp = new FrameLayout.LayoutParams(camSize, camSize);
        camLp.gravity = Gravity.BOTTOM | Gravity.END;
        cameraBtn.setLayoutParams(camLp);
        cameraBtn.setOnClickListener(v -> pickPhoto());
        photoFrame.addView(cameraBtn);
        profileCard.addView(photoFrame);
        loadPhoto();

        // İsim
        nameView = new TextView(this);
        nameView.setText(getUserName());
        nameView.setTextColor(0xFFE2D9F3);
        nameView.setTextSize(22);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(0, 0, 0, 4);
        nameView.setOnClickListener(v -> editName());
        profileCard.addView(nameView);

        TextView editNameHint = new TextView(this);
        editNameHint.setText("✎ ismi düzenle");
        editNameHint.setTextColor(0xFF4A4560);
        editNameHint.setTextSize(11);
        editNameHint.setGravity(Gravity.CENTER);
        editNameHint.setPadding(0, 0, 0, 8);
        editNameHint.setOnClickListener(v -> editName());
        profileCard.addView(editNameHint);

        if (user != null && user.getEmail() != null) {
            TextView emailTv = new TextView(this);
            emailTv.setText(user.getEmail());
            emailTv.setTextColor(0xFF9B8EC4);
            emailTv.setTextSize(13);
            emailTv.setGravity(Gravity.CENTER);
            emailTv.setPadding(0, 0, 0, 12);
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
            statusTv.setText("⚠ Misafir — veriler senkronize edilmiyor");
            statusTv.setTextColor(0xFFFBBF24);
        }
        statusTv.setTextSize(12);
        statusTv.setGravity(Gravity.CENTER);
        profileCard.addView(statusTv);
        root.addView(profileCard);

        // ── İstatistikler ────────────────────────────────────────────────────
        root.addView(makeSectionTitle("İSTATİSTİKLER"));
        statsContainer = new LinearLayout(this);
        statsContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.setMargins(0, 0, 0, 20);
        statsContainer.setLayoutParams(statsLp);
        root.addView(statsContainer);
        new Thread(() -> {
            int[] stats = getStats();
            runOnUiThread(() -> {
                statsContainer.addView(makeStatCard("📚", String.valueOf(stats[0]), "Kitap", accentColor));
                statsContainer.addView(makeStatCard("📄", String.valueOf(stats[1]), "Sayfa", accentColor));
                statsContainer.addView(makeStatCard("💎", String.valueOf(stats[2]), "Alıntı", accentColor));
            });
        }).start();

        // ── Rozetler ─────────────────────────────────────────────────────────
        root.addView(makeSectionTitle("ROZETLER"));
        badgeContainer = new LinearLayout(this);
        badgeContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.setMargins(0, 0, 0, 20);
        badgeContainer.setLayoutParams(badgeLp);

        ScrollView badgeSv = new ScrollView(this);
        badgeSv.setLayoutParams(badgeLp);
        ((android.widget.HorizontalScrollView) new android.widget.HorizontalScrollView(this))
            .addView(badgeContainer);
        android.widget.HorizontalScrollView hSv = new android.widget.HorizontalScrollView(this);
        hSv.setLayoutParams(badgeLp);
        hSv.addView(badgeContainer);
        root.addView(hSv);
        new Thread(() -> {
            int[] stats = getStats();
            runOnUiThread(() -> showBadges(stats[0], stats[1], stats[2]));
        }).start();

        // ── Ayarlar ──────────────────────────────────────────────────────────
        root.addView(makeSectionTitle("HESAP"));
        if (user != null && !user.isAnonymous()) {
            root.addView(makeRow("🔄 Verileri Senkronize Et", accentColor, () ->
                Toast.makeText(this, "Senkronizasyon başlatıldı", Toast.LENGTH_SHORT).show()));
        } else {
            root.addView(makeRow("🔐 Google ile Giriş Yap", accentColor, () -> {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }));
        }
        root.addView(makeRow("🚪 Çıkış Yap", 0xFF2D2B55, () ->
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
        root.addView(makeRow("🗑 Yerel Verileri Temizle", 0xFF2D2B55, () ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("Emin misiniz?")
                .setMessage("Tüm yerel veriler silinecek.")
                .setPositiveButton("Sil", (d, w) -> { deleteDatabase("goblith.db"); finishAffinity(); })
                .setNegativeButton("İptal", null).show()));

        if (user != null && !user.isAnonymous()) {
            root.addView(makeRow("❌ Hesabı Kalıcı Sil", 0xFF7F1D1D, () ->
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Hesabı Sil")
                    .setMessage("Hesabınız ve tüm verileriniz kalıcı olarak silinecek.")
                    .setPositiveButton("Evet, Sil", (d, w) -> deleteAccount())
                    .setNegativeButton("İptal", null).show()));
        }

        TextView versionTv = new TextView(this);
        versionTv.setText("Goblith v1.0");
        versionTv.setTextColor(0xFF2D2B55);
        versionTv.setTextSize(11);
        versionTv.setGravity(Gravity.CENTER);
        versionTv.setPadding(0, 32, 0, 0);
        root.addView(versionTv);

        sv.addView(root);
        setContentView(sv);
        loadBio();
    }

    private void showSettingsMenu(int accentColor) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Ayarlar")
            .setItems(new String[]{"🎨 Tema Seç", "🔒 Gizlilik Ayarları", "🔔 Bildirimler"},
                (d, w) -> {
                    switch (w) {
                        case 0: showThemeDialog(); break;
                        case 1: showPrivacyDialog(); break;
                        case 2: showNotifDialog(); break;
                    }
                }).show();
    }

    private void showThemeDialog() {
        String[] themes = {"🟣 Karanlık Mor", "⚫ Tam Siyah", "🔵 Lacivert", "🟢 Koyu Yeşil"};
        String[] keys = {"dark_purple", "pure_black", "navy", "dark_green"};
        int[] accents = {0xFF7C3AED, 0xFFFFFFFF, 0xFF3B82F6, 0xFF22C55E};
        int[] bgs = {0xFF0F0E17, 0xFF000000, 0xFF0A1628, 0xFF0A1A0F};
        String current = prefs.getString("theme", "dark_purple");
        int sel = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) sel = i;
        final int[] selected = {sel};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Tema Seç")
            .setSingleChoiceItems(themes, sel, (d, w) -> selected[0] = w)
            .setPositiveButton("Uygula", (d, w) -> {
                prefs.edit()
                    .putString("theme", keys[selected[0]])
                    .putInt("accent_color", accents[selected[0]])
                    .putInt("bg_color", bgs[selected[0]])
                    .apply();
                Toast.makeText(this, "Tema değiştirildi, uygulamayı yeniden açın", Toast.LENGTH_LONG).show();
                finish();
            })
            .setNegativeButton("İptal", null).show();
    }

    private void showPrivacyDialog() {
        String[] options = {"🌍 Herkese Açık", "👥 Sadece Arkadaşlar", "🔒 Gizli"};
        int current = prefs.getInt("privacy", 0);
        final int[] sel = {current};
        new android.app.AlertDialog.Builder(this)
            .setTitle("Profil Gizliliği")
            .setSingleChoiceItems(options, current, (d, w) -> sel[0] = w)
            .setPositiveButton("Kaydet", (d, w) -> {
                prefs.edit().putInt("privacy", sel[0]).apply();
                if (user != null && !user.isAnonymous()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("privacy", sel[0]);
                    firestore.collection("users").document(user.getUid())
                        .set(data, SetOptions.merge());
                }
                Toast.makeText(this, "Gizlilik ayarı kaydedildi", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("İptal", null).show();
    }

    private void showNotifDialog() {
        boolean notifOn = prefs.getBoolean("notif_enabled", true);
        String[] options = {"📖 Okuma hatırlatıcısı", "💬 Topluluk bildirimleri", "🔔 Tümü"};
        boolean[] checked = {
            prefs.getBoolean("notif_reading", true),
            prefs.getBoolean("notif_community", true),
            notifOn
        };
        new android.app.AlertDialog.Builder(this)
            .setTitle("Bildirim Ayarları")
            .setMultiChoiceItems(options, checked, (d, w, isChecked) -> checked[w] = isChecked)
            .setPositiveButton("Kaydet", (d, w) -> {
                prefs.edit()
                    .putBoolean("notif_reading", checked[0])
                    .putBoolean("notif_community", checked[1])
                    .putBoolean("notif_enabled", checked[2])
                    .apply();
                Toast.makeText(this, "Bildirim ayarları kaydedildi", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("İptal", null).show();
    }

    private void deleteAccount() {
        if (user == null) return;
        firestore.collection("users").document(user.getUid()).delete();
        user.delete().addOnSuccessListener(v -> {
            deleteDatabase("goblith.db");
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }).addOnFailureListener(e ->
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void loadPhoto() {
        if (user == null) { showInitial(); return; }
        if (user.isAnonymous()) { showInitial(); return; }
        firestore.collection("users").document(user.getUid()).get()
            .addOnSuccessListener(doc -> {
                if (doc != null && doc.contains("photo_base64")) {
                    String b64 = doc.getString("photo_base64");
                    if (b64 != null && !b64.isEmpty()) {
                        try {
                            byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bmp != null) { photoView.setImageBitmap(toRoundBitmap(bmp)); return; }
                        } catch (Exception ignored) {}
                    }
                }
                if (user.getPhotoUrl() != null) loadBitmap(user.getPhotoUrl().toString());
                else showInitial();
            })
            .addOnFailureListener(e -> {
                if (user.getPhotoUrl() != null) loadBitmap(user.getPhotoUrl().toString());
                else showInitial();
            });
    }

    private void loadBitmap(String url) {
        new Thread(() -> {
            try {
                Bitmap bmp = BitmapFactory.decodeStream(new java.net.URL(url).openStream());
                if (bmp != null) runOnUiThread(() -> photoView.setImageBitmap(toRoundBitmap(bmp)));
                else runOnUiThread(this::showInitial);
            } catch (Exception e) { runOnUiThread(this::showInitial); }
        }).start();
    }

    private Bitmap toRoundBitmap(Bitmap bmp) {
        int size = Math.min(bmp.getWidth(), bmp.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(size/2f, size/2f, size/2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bmp, 0, 0, paint);
        return output;
    }

    private void showInitial() {
        String name = getUserName();
        String initial = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
        Bitmap bmp = Bitmap.createBitmap(dp(100), dp(100), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF7C3AED);
        c.drawCircle(dp(50), dp(50), dp(50), p);
        p.setColor(Color.WHITE);
        p.setTextSize(dp(40));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText(initial, dp(50), dp(50) + dp(15), p);
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
                Map<String, Object> data = new HashMap<>();
                data.put("photo_base64", b64);
                firestore.collection("users").document(user.getUid())
                    .set(data, SetOptions.merge())
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

    private void editName() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("İsim Değiştir");
        EditText et = new EditText(this);
        et.setText(getUserName());
        et.setPadding(32, 24, 32, 24);
        b.setView(et);
        b.setPositiveButton("Kaydet", (d, w) -> {
            String newName = et.getText().toString().trim();
            if (newName.isEmpty()) return;
            if (user != null) {
                UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                    .setDisplayName(newName).build();
                user.updateProfile(req).addOnSuccessListener(v -> {
                    nameView.setText(newName);
                    Toast.makeText(this, "İsim güncellendi ✓", Toast.LENGTH_SHORT).show();
                });
            }
        });
        b.setNegativeButton("İptal", null);
        b.show();
    }

    private void editBio() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("Bio Düzenle");
        EditText et = new EditText(this);
        et.setHint("Kendinizi tanıtın...");
        String currentBio = bioView.getText().toString();
        if (!currentBio.equals("✎ Bio ekle...")) et.setText(currentBio);
        et.setPadding(32, 24, 32, 24);
        b.setView(et);
        b.setPositiveButton("Kaydet", (d, w) -> {
            String bio = et.getText().toString().trim();
            bioView.setText(bio.isEmpty() ? "✎ Bio ekle..." : bio);
            bioView.setTextColor(bio.isEmpty() ? 0xFF4A4560 : 0xFF9B8EC4);
            if (user != null && !user.isAnonymous()) {
                Map<String, Object> data = new HashMap<>();
                data.put("bio", bio);
                firestore.collection("users").document(user.getUid()).set(data, SetOptions.merge());
            }
        });
        b.setNegativeButton("İptal", null);
        b.show();
    }

    private void loadBio() {
        if (user == null || user.isAnonymous()) return;
        firestore.collection("users").document(user.getUid()).get()
            .addOnSuccessListener(doc -> {
                if (doc != null && doc.contains("bio")) {
                    String bio = doc.getString("bio");
                    if (bio != null && !bio.isEmpty()) {
                        bioView.setText(bio);
                        bioView.setTextColor(0xFF9B8EC4);
                    }
                }
            });
    }

    private int[] getStats() {
        int archiveCount = 0, bookCount = 0, totalPages = 0;
        if (db != null) {
            try {
                android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM archive", null);
                if (c.moveToFirst()) archiveCount = c.getInt(0); c.close();
            } catch (Exception ignored) {}
            try {
                android.database.Cursor c = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(last_page),0) FROM library", null);
                if (c.moveToFirst()) { bookCount = c.getInt(0); totalPages = c.getInt(1); } c.close();
            } catch (Exception ignored) {}
        }
        return new int[]{bookCount, totalPages, archiveCount};
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

    private void loadStats(int accentColor) {
        if (db == null) return;
        int archiveCount = 0, bookCount = 0, totalPages = 0;
        try {
            android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM archive", null);
            if (c.moveToFirst()) archiveCount = c.getInt(0); c.close();
        } catch (Exception ignored) {}
        try {
            android.database.Cursor c = db.rawQuery("SELECT COUNT(*), COALESCE(SUM(last_page),0) FROM library", null);
            if (c.moveToFirst()) { bookCount = c.getInt(0); totalPages = c.getInt(1); } c.close();
        } catch (Exception ignored) {}
        statsContainer.addView(makeStatCard("📚", String.valueOf(bookCount), "Kitap", accentColor));
        statsContainer.addView(makeStatCard("📄", String.valueOf(totalPages), "Sayfa", accentColor));
        statsContainer.addView(makeStatCard("💎", String.valueOf(archiveCount), "Alıntı", accentColor));
    }

    private void loadBadges() { /* background thread kullanılıyor */ }

    private void addBadge(String emoji, String label) {
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(16, 16, 16, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2D2B55);
        bg.setCornerRadius(16);
        badge.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 12, 0);
        badge.setLayoutParams(lp);
        TextView eTv = new TextView(this);
        eTv.setText(emoji); eTv.setTextSize(28); eTv.setGravity(Gravity.CENTER);
        badge.addView(eTv);
        TextView lTv = new TextView(this);
        lTv.setText(label); lTv.setTextColor(0xFF9B8EC4); lTv.setTextSize(10); lTv.setGravity(Gravity.CENTER);
        badge.addView(lTv);
        badgeContainer.addView(badge);
    }

    private View makeStatCard(String emoji, String value, String label, int accentColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(24, 24, 24, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2D2B55);
        bg.setCornerRadius(20);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(0, 0, 8, 0);
        card.setLayoutParams(lp);
        TextView eTv = new TextView(this); eTv.setText(emoji); eTv.setTextSize(24); eTv.setGravity(Gravity.CENTER);
        card.addView(eTv);
        TextView vTv = new TextView(this); vTv.setText(value); vTv.setTextColor(0xFFE2D9F3);
        vTv.setTextSize(22); vTv.setTypeface(null, Typeface.BOLD); vTv.setGravity(Gravity.CENTER);
        card.addView(vTv);
        TextView lTv = new TextView(this); lTv.setText(label); lTv.setTextColor(0xFF9B8EC4);
        lTv.setTextSize(11); lTv.setGravity(Gravity.CENTER);
        card.addView(lTv);
        return card;
    }

    private View makeRow(String text, int color, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(24, 20, 24, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A1831);
        bg.setCornerRadius(16);
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 10);
        row.setLayoutParams(lp);
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(0xFFE2D9F3); tv.setTextSize(14);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tv.setLayoutParams(tvLp);
        row.addView(tv);
        TextView arrow = new TextView(this);
        arrow.setText("›"); arrow.setTextColor(0xFF9B8EC4); arrow.setTextSize(20);
        row.addView(arrow);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private TextView makeSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(0xFF9B8EC4); tv.setTextSize(11);
        tv.setTypeface(null, Typeface.BOLD); tv.setPadding(0, 24, 0, 12);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private String getUserName() {
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty())
            return user.getDisplayName();
        return "Misafir";
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
}
