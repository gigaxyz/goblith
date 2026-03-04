package com.goblith.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
import com.google.firebase.auth.*;

public class ProfileActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            android.content.SharedPreferences p = getSharedPreferences("crash", MODE_PRIVATE);
            p.edit().putString("profile_crash", ex.getClass().getName() + ": " + ex.getMessage() +
                "\n" + android.util.Log.getStackTraceString(ex)).apply();
            finish();
        });
        
        // Son crash göster
        android.content.SharedPreferences p = getSharedPreferences("crash", MODE_PRIVATE);
        String crash = p.getString("profile_crash", null);
        if (crash != null) {
            p.edit().remove("profile_crash").apply();
            new android.app.AlertDialog.Builder(this)
                .setTitle("Hata")
                .setMessage(crash.length() > 800 ? crash.substring(0, 800) : crash)
                .setPositiveButton("Tamam", null)
                .show();
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0E17);
        root.setPadding(32, 48, 32, 32);

        // Başlık
        TextView title = new TextView(this);
        title.setText("← Profil");
        title.setTextColor(0xFF9B8EC4);
        title.setTextSize(14);
        title.setPadding(0, 0, 0, 32);
        title.setOnClickListener(v -> finish());
        root.addView(title);

        // Kullanıcı bilgisi kartı
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFF1A1831);
        card.setPadding(32, 32, 32, 32);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, 24);
        card.setLayoutParams(cardLp);

        TextView nameView = new TextView(this);
        TextView emailView = new TextView(this);
        TextView statusView = new TextView(this);

        if (user != null && !user.isAnonymous()) {
            nameView.setText(user.getDisplayName() != null ? user.getDisplayName() : "İsimsiz");
            emailView.setText(user.getEmail() != null ? user.getEmail() : "");
            statusView.setText("✓ Google ile giriş yapıldı");
            statusView.setTextColor(0xFF4ADE80);
        } else {
            nameView.setText("Misafir");
            emailView.setText("Giriş yapılmadı");
            statusView.setText("Anonim kullanıcı");
            statusView.setTextColor(0xFF9B8EC4);
        }

        nameView.setTextSize(20);
        nameView.setTextColor(0xFFE2D9F3);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        emailView.setTextSize(13);
        emailView.setTextColor(0xFF9B8EC4);
        emailView.setPadding(0, 8, 0, 8);
        statusView.setTextSize(12);

        card.addView(nameView);
        card.addView(emailView);
        card.addView(statusView);
        root.addView(card);

        // Google ile giriş yap (anonim ise göster)
        if (user == null || user.isAnonymous()) {
            Button btnGoogle = makeBtn("Google ile Giriş Yap", 0xFF7C3AED);
            btnGoogle.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
            root.addView(btnGoogle);
        }

        // Ayarlar bölümü
        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("AYARLAR");
        settingsTitle.setTextColor(0xFF9B8EC4);
        settingsTitle.setTextSize(11);
        settingsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        settingsTitle.setPadding(0, 32, 0, 16);
        root.addView(settingsTitle);

        // Tema seçimi (ileride genişletilebilir)
        Button btnTheme = makeBtn("Tema: Karanlık Mor", 0xFF2D2B55);
        btnTheme.setOnClickListener(v ->
            Toast.makeText(this, "Yakında daha fazla tema eklenecek", Toast.LENGTH_SHORT).show());
        root.addView(btnTheme);

        // Verileri temizle
        Button btnClear = makeBtn("Yerel Verileri Temizle", 0xFF2D2B55);
        btnClear.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Emin misiniz?")
                .setMessage("Tüm yerel veriler silinecek. Bu işlem geri alınamaz.")
                .setPositiveButton("Sil", (d, w) -> {
                    deleteDatabase("goblith.db");
                    Toast.makeText(this, "Veriler silindi", Toast.LENGTH_SHORT).show();
                    finishAffinity();
                })
                .setNegativeButton("İptal", null)
                .show();
        });
        root.addView(btnClear);

        // Çıkış yap
        Button btnLogout = makeBtn("Hesaptan Çıkış Yap", 0xFF7C3AED);
        btnLogout.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Çıkış Yap")
                .setMessage("Hesabınızdan çıkmak istiyor musunuz?")
                .setPositiveButton("Çıkış", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .setNegativeButton("İptal", null)
                .show();
        });
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logoutLp.setMargins(0, 16, 0, 0);
        btnLogout.setLayoutParams(logoutLp);
        root.addView(btnLogout);

        // Uygulama versiyonu
        TextView version = new TextView(this);
        version.setText("Goblith v1.0 — Kişisel Bilgi Sistemi");
        version.setTextSize(11);
        version.setTextColor(0xFF4A4560);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, 48, 0, 0);
        root.addView(version);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private Button makeBtn(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setBackgroundColor(color);
        b.setTextColor(0xFFE2D9F3);
        b.setTextSize(13);
        b.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8);
        b.setLayoutParams(lp);
        return b;
    }
}
