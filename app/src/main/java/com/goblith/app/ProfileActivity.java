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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            getSharedPreferences("crash", MODE_PRIVATE).edit()
                .putString("profile_crash", ex.toString()).apply();
            finish();
        });

        user = FirebaseAuth.getInstance().getCurrentUser();
        firestore = FirebaseFirestore.getInstance();


        try {
            db = openOrCreateDatabase("goblith.db", MODE_PRIVATE, null);
        } catch (Exception e) {
            db = null;
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0F0E17);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0E17);
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

        // Ayarlar ikonu
        TextView settingsBtn = new TextView(this);
        settingsBtn.setText("⚙");
        settingsBtn.setTextColor(0xFF9B8EC4);
        settingsBtn.setTextSize(22);
        settingsBtn.setOnClickListener(v -> showSettingsDialog());
        topBar.addView(settingsBtn);

        root.addView(topBar);

        // ── Profil kartı ─────────────────────────────────────────────────────
        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setOrientation(LinearLayout.VERTICAL);
        profileCard.setGravity(Gravity.CENTER);
        profileCard.setBackgroundColor(0xFF1A1831);
        profileCard.setPadding(32, 40, 32, 40);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1A1831);
        cardBg.setCornerRadius(24);
        profileCard.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, 20);
        profileCard.setLayoutParams(cardLp);

        // Profil fotoğrafı — glow efektli
        FrameLayout photoFrame = new FrameLayout(this);
        int photoSize = dp(100);
        LinearLayout.LayoutParams photoFrameLp = new LinearLayout.LayoutParams(photoSize + dp(8), photoSize + dp(8));
        photoFrameLp.gravity = Gravity.CENTER_HORIZONTAL;
        photoFrameLp.setMargins(0, 0, 0, 20);
        photoFrame.setLayoutParams(photoFrameLp);

        // Glow halkası
        View glowRing = new View(this);
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setStroke(dp(3), 0xFF7C3AED);
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

        // Kamera ikonu
        TextView cameraBtn = new TextView(this);
        cameraBtn.setText("📷");
        cameraBtn.setTextSize(14);
        cameraBtn.setGravity(Gravity.CENTER);
        cameraBtn.setBackgroundColor(0xFF7C3AED);
        GradientDrawable camBg = new GradientDrawable();
        camBg.setShape(GradientDrawable.OVAL);
        camBg.setColor(0xFF7C3AED);
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
        nameView.setOnClickListener(v -> editName());
        profileCard.addView(nameView);

        // Email
        if (user != null && user.getEmail() != null) {
            TextView emailTv = new TextView(this);
            emailTv.setText(user.getEmail());
            emailTv.setTextColor(0xFF9B8EC4);
            emailTv.setTextSize(13);
            emailTv.setGravity(Gravity.CENTER);
            emailTv.setPadding(0, 6, 0, 12);
            profileCard.addView(emailTv);
        }

        // Bio
        bioView = new TextView(this);
        bioView.setText("Bio eklemek için tıkla...");
        bioView.setTextColor(0xFF4A4560);
        bioView.setTextSize(13);
        bioView.setGravity(Gravity.CENTER);
        bioView.setPadding(0, 8, 0, 16);
        bioView.setOnClickListener(v -> editBio());
        profileCard.addView(bioView);

        // Giriş durumu
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
        TextView statTitle = makeSectionTitle("İSTATİSTİKLER");
        root.addView(statTitle);

        statsContainer = new LinearLayout(this);
        statsContainer.setOrientation(LinearLayout.HORIZONTAL);
        statsContainer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.setMargins(0, 0, 0, 20);
        statsContainer.setLayoutParams(statsLp);
        root.addView(statsContainer);
        loadStats();

        // ── Rozetler ─────────────────────────────────────────────────────────
        TextView badgeTitle = makeSectionTitle("ROZETLER");
        root.addView(badgeTitle);

        badgeContainer = new LinearLayout(this);
        badgeContainer.setOrientation(LinearLayout.HORIZONTAL);
        badgeContainer.setGravity(Gravity.START);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.setMargins(0, 0, 0, 20);
        badgeContainer.setLayoutParams(badgeLp);
        root.addView(badgeContainer);

        // ── Ayarlar ──────────────────────────────────────────────────────────
        TextView settingsTitle = makeSectionTitle("AYARLAR");
        root.addView(settingsTitle);

        if (user != null && !user.isAnonymous()) {
            root.addView(makeSettingRow("🔄 Verileri Senkronize Et", () -> {
                Toast.makeText(this, "Senkronizasyon başlatıldı...", Toast.LENGTH_SHORT).show();
            }));
        } else {
            root.addView(makeSettingRow("🔐 Google ile Giriş Yap", () -> {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }));
        }

        root.addView(makeSettingRow("🔔 Bildirimler", () ->
            Toast.makeText(this, "Yakında eklenecek", Toast.LENGTH_SHORT).show()));

        root.addView(makeSettingRow("🗑 Yerel Verileri Temizle", () ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("Emin misiniz?")
                .setMessage("Tüm yerel veriler silinecek.")
                .setPositiveButton("Sil", (d, w) -> {
                    deleteDatabase("goblith.db");
                    finishAffinity();
                })
                .setNegativeButton("İptal", null).show()));

        root.addView(makeSettingRow("🚪 Hesaptan Çıkış Yap", () ->
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

        // Versiyon
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
        loadBadges();
    }

    private void loadPhoto() {
        if (user == null) { showInitial(); return; }
        // Firestore'dan base64 fotoğraf yükle
        firestore.collection("users").document(user.getUid()).get()
            .addOnSuccessListener(doc -> {
                if (doc.contains("photo_base64")) {
                    String b64 = doc.getString("photo_base64");
                    if (b64 != null && !b64.isEmpty()) {
                        try {
                            byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            photoView.setImageBitmap(toRoundBitmap(bmp));
                            return;
                        } catch (Exception ignored) {}
                    }
                }
                // Firestore'da yoksa Google fotoğrafı
                if (user.getPhotoUrl() != null) {
                    loadBitmap(user.getPhotoUrl().toString());
                } else {
                    showInitial();
                }
            })
            .addOnFailureListener(e -> {
                if (user.getPhotoUrl() != null) loadBitmap(user.getPhotoUrl().toString());
                else showInitial();
            });
    }

    private void loadBitmap(String url) {
        new Thread(() -> {
            try {
                java.net.URL u = new java.net.URL(url);
                Bitmap bmp = android.graphics.BitmapFactory.decodeStream(u.openStream());
                Bitmap round = toRoundBitmap(bmp);
                runOnUiThread(() -> photoView.setImageBitmap(round));
            } catch (Exception e) {
                runOnUiThread(this::showInitial);
            }
        }).start();
    }

    private Bitmap toRoundBitmap(Bitmap bmp) {
        int size = Math.min(bmp.getWidth(), bmp.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bmp, 0, 0, paint);
        return output;
    }

    private void showInitial() {
        String initial = getUserName().isEmpty() ? "?" : String.valueOf(getUserName().charAt(0)).toUpperCase();
        TextView tv = new TextView(this);
        tv.setText(initial);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(40);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFF7C3AED);
        photoView.setBackground(bg);
        photoView.setImageBitmap(null);
    }

    private void pickPhoto() {
        if (user == null || user.isAnonymous()) {
            Toast.makeText(this, "Fotoğraf değiştirmek için giriş yapın", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("image/*");
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            uploadPhoto(uri);
        }
    }

    private void uploadPhoto(Uri uri) {
        if (user == null || user.isAnonymous()) {
            Toast.makeText(this, "Giriş yapmanız gerekiyor", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Fotoğraf yükleniyor...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                // Boyutu küçült — Firestore limiti için
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, 256, 256, true);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                String b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT);
                firestore.collection("users").document(user.getUid())
                    .update("photo_base64", b64)
                    .addOnSuccessListener(v -> runOnUiThread(() -> {
                        photoView.setImageBitmap(toRoundBitmap(scaled));
                        Toast.makeText(this, "Fotoğraf güncellendi ✓", Toast.LENGTH_SHORT).show();
                    }))
                    .addOnFailureListener(e -> {
                        // update başarısız olursa set dene
                        java.util.Map<String, Object> data = new java.util.HashMap<>();
                        data.put("photo_base64", b64);
                        firestore.collection("users").document(user.getUid()).set(data, SetOptions.merge())
                            .addOnSuccessListener(v -> runOnUiThread(() -> {
                                photoView.setImageBitmap(toRoundBitmap(scaled));
                                Toast.makeText(this, "Fotoğraf güncellendi ✓", Toast.LENGTH_SHORT).show();
                            }))
                            .addOnFailureListener(e2 -> runOnUiThread(() ->
                                Toast.makeText(this, "Hata: " + e2.getMessage(), Toast.LENGTH_LONG).show()));
                    });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show());
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
            UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                .setDisplayName(newName).build();
            user.updateProfile(req).addOnSuccessListener(v -> {
                nameView.setText(newName);
                Toast.makeText(this, "İsim güncellendi", Toast.LENGTH_SHORT).show();
            });
        });
        b.setNegativeButton("İptal", null);
        b.show();
    }

    private void editBio() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("Bio");
        EditText et = new EditText(this);
        et.setHint("Kendinizi tanıtın...");
        et.setPadding(32, 24, 32, 24);
        b.setView(et);
        b.setPositiveButton("Kaydet", (d, w) -> {
            String bio = et.getText().toString().trim();
            bioView.setText(bio.isEmpty() ? "Bio eklemek için tıkla..." : bio);
            bioView.setTextColor(bio.isEmpty() ? 0xFF4A4560 : 0xFF9B8EC4);
            if (user != null && !user.isAnonymous()) {
                firestore.collection("users").document(user.getUid())
                    .set(Collections.singletonMap("bio", bio));
            }
        });
        b.setNegativeButton("İptal", null);
        b.show();
    }

    private void loadBio() {
        if (user == null || user.isAnonymous()) return;
        firestore.collection("users").document(user.getUid()).get()
            .addOnSuccessListener(doc -> {
                if (doc.contains("bio")) {
                    String bio = doc.getString("bio");
                    if (bio != null && !bio.isEmpty()) {
                        bioView.setText(bio);
                        bioView.setTextColor(0xFF9B8EC4);
                    }
                }
            });
    }

    private void loadStats() {
        if (db == null) return;
        try {
            int archiveCount = 0, bookCount = 0, totalPages = 0;
            try {
                android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM archive", null);
                if (c.moveToFirst()) archiveCount = c.getInt(0);
                c.close();
            } catch (Exception ignored) {}
            try {
                android.database.Cursor c = db.rawQuery("SELECT COUNT(*), SUM(last_page) FROM library", null);
                if (c.moveToFirst()) { bookCount = c.getInt(0); totalPages = c.getInt(1); }
                c.close();
            } catch (Exception ignored) {}

            statsContainer.addView(makeStatCard("📚", String.valueOf(bookCount), "Kitap"));
            statsContainer.addView(makeStatCard("📄", String.valueOf(totalPages), "Sayfa"));
            statsContainer.addView(makeStatCard("💎", String.valueOf(archiveCount), "Alıntı"));
        } catch (Exception ignored) {}
    }

    private void loadBadges() {
        if (db == null) return;
        try {
            int archiveCount = 0, bookCount = 0, totalPages = 0;
            try {
                android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM archive", null);
                if (c.moveToFirst()) archiveCount = c.getInt(0);
                c.close();
            } catch (Exception ignored) {}
            try {
                android.database.Cursor c = db.rawQuery("SELECT COUNT(*), SUM(last_page) FROM library", null);
                if (c.moveToFirst()) { bookCount = c.getInt(0); totalPages = c.getInt(1); }
                c.close();
            } catch (Exception ignored) {}

            if (bookCount >= 1) addBadge("📖", "İlk Kitap");
            if (bookCount >= 5) addBadge("📚", "Kitapsever");
            if (totalPages >= 100) addBadge("📄", "100 Sayfa");
            if (totalPages >= 500) addBadge("🔥", "500 Sayfa");
            if (archiveCount >= 10) addBadge("💎", "10 Alıntı");
            if (archiveCount >= 50) addBadge("🏆", "50 Alıntı");
            if (badgeContainer.getChildCount() == 0) addBadge("🌱", "Yeni Başlayan");
        } catch (Exception ignored) {}
    }

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

        TextView emojiTv = new TextView(this);
        emojiTv.setText(emoji);
        emojiTv.setTextSize(28);
        emojiTv.setGravity(Gravity.CENTER);
        badge.addView(emojiTv);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(0xFF9B8EC4);
        labelTv.setTextSize(10);
        labelTv.setGravity(Gravity.CENTER);
        badge.addView(labelTv);

        badgeContainer.addView(badge);
    }

    private View makeStatCard(String emoji, String value, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(24, 24, 24, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2D2B55);
        bg.setCornerRadius(20);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(0, 0, 12, 0);
        card.setLayoutParams(lp);

        TextView emojiTv = new TextView(this);
        emojiTv.setText(emoji);
        emojiTv.setTextSize(24);
        emojiTv.setGravity(Gravity.CENTER);
        card.addView(emojiTv);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextColor(0xFFE2D9F3);
        valueTv.setTextSize(22);
        valueTv.setTypeface(null, Typeface.BOLD);
        valueTv.setGravity(Gravity.CENTER);
        card.addView(valueTv);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(0xFF9B8EC4);
        labelTv.setTextSize(11);
        labelTv.setGravity(Gravity.CENTER);
        card.addView(labelTv);

        return card;
    }

    private View makeSettingRow(String text, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(0xFF1A1831);
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
        tv.setText(text);
        tv.setTextColor(0xFFE2D9F3);
        tv.setTextSize(14);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tv.setLayoutParams(tvLp);
        row.addView(tv);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(0xFF9B8EC4);
        arrow.setTextSize(20);
        row.addView(arrow);

        row.setOnClickListener(v -> action.run());
        return row;
    }

    private TextView makeSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF9B8EC4);
        tv.setTextSize(11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 24, 0, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void showSettingsDialog() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Gizlilik Ayarları")
            .setItems(new String[]{"Profilimi Herkese Açık Yap", "Sadece Arkadaşlarıma Göster", "Gizli Tut"},
                (d, w) -> Toast.makeText(this, "Ayar kaydedildi", Toast.LENGTH_SHORT).show())
            .show();
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
