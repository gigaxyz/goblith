package com.goblith.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.google.firebase.auth.*;

public class ProfileActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF0F0E17);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0E17);
        root.setPadding(24, 48, 24, 48);

        TextView back = new TextView(this);
        back.setText("← Geri");
        back.setTextColor(0xFF9B8EC4);
        back.setTextSize(16);
        back.setPadding(0, 0, 0, 32);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView name = new TextView(this);
        name.setText(user != null && user.getDisplayName() != null ? user.getDisplayName() : "Misafir");
        name.setTextColor(0xFFE2D9F3);
        name.setTextSize(24);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(name);

        TextView email = new TextView(this);
        email.setText(user != null && user.getEmail() != null ? user.getEmail() : "Giriş yapılmadı");
        email.setTextColor(0xFF9B8EC4);
        email.setTextSize(14);
        email.setPadding(0, 8, 0, 32);
        root.addView(email);

        Button btnLogout = new Button(this);
        btnLogout.setText("Çıkış Yap");
        btnLogout.setBackgroundColor(0xFF7C3AED);
        btnLogout.setTextColor(0xFFFFFFFF);
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
        root.addView(btnLogout);

        sv.addView(root);
        setContentView(sv);
    }
}
