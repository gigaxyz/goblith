package com.goblith.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends Activity {
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();

        // Zaten giriş yapılmışsa direkt geç
        if (mAuth.getCurrentUser() != null) {
            startMain(); return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0E17);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 0, 48, 0);

        TextView logo = new TextView(this);
        logo.setText("Goblith");
        logo.setTextSize(48);
        logo.setTextColor(0xFF7C3AED);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);

        TextView sub = new TextView(this);
        sub.setText("Kişisel Bilgi Sistemi");
        sub.setTextSize(14);
        sub.setTextColor(0xFF9B8EC4);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 8, 0, 64);
        root.addView(sub);

        Button btnStart = new Button(this);
        btnStart.setText("BAŞLA");
        btnStart.setBackgroundColor(0xFF7C3AED);
        btnStart.setTextColor(0xFFE2D9F3);
        btnStart.setTextSize(15);
        btnStart.setTypeface(null, android.graphics.Typeface.BOLD);
        btnStart.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnStart.setLayoutParams(lp);
        btnStart.setOnClickListener(v -> signInAnonymously());
        root.addView(btnStart);

        setContentView(root);
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously()
            .addOnSuccessListener(r -> startMain())
            .addOnFailureListener(e -> {
                // Firebase bağlantısı yoksa direkt geç
                startMain();
            });
    }

    private void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
