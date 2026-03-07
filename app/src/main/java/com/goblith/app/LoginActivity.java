package com.goblith.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;

public class LoginActivity extends Activity {
    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;
    private static final String WEB_CLIENT_ID =
        "737474279071-tel5mf3l6os1s1afomb3f3r5ie7eed0d.apps.googleusercontent.com";

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private Button btnGoogle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();

        // Crash log göster
        try {
            android.content.SharedPreferences crashPrefs = getSharedPreferences("crash", MODE_PRIVATE);
            String lastCrash = crashPrefs.getString("last_crash", null);
            if (lastCrash != null) {
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Son Hata")
                    .setMessage(lastCrash.substring(0, Math.min(lastCrash.length(), 800)))
                    .setPositiveButton("Temizle", (d, w) -> crashPrefs.edit().remove("last_crash").apply())
                    .setNegativeButton("Kapat", null)
                    .show();
            }
        } catch (Exception ignored) {}

        // Gerçek Google girişi yapılmışsa direkt geç
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            startMain(); return;
        }

        // Google Sign-In yapılandır
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        buildUI();
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0E17);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 0, 48, 0);

        // Logo
        TextView logo = new TextView(this);
        logo.setText("Goblith");
        logo.setTextSize(52);
        logo.setTextColor(0xFF7C3AED);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo);

        TextView sub = new TextView(this);
        sub.setText("Kişisel Bilgi Sistemi");
        sub.setTextSize(14);
        sub.setTextColor(0xFF9B8EC4);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 8, 0, 80);
        root.addView(sub);

        // Google ile giriş
        btnGoogle = new Button(this);
        btnGoogle.setText("Google ile Giriş Yap");
        btnGoogle.setBackgroundColor(0xFF7C3AED);
        btnGoogle.setTextColor(0xFFFFFFFF);
        btnGoogle.setTextSize(15);
        btnGoogle.setTypeface(null, android.graphics.Typeface.BOLD);
        btnGoogle.setPadding(32, 28, 32, 28);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.setMargins(0, 0, 0, 16);
        btnGoogle.setLayoutParams(lp1);
        btnGoogle.setOnClickListener(v -> startGoogleSignIn());
        root.addView(btnGoogle);

        // Misafir olarak devam et
        Button btnGuest = new Button(this);
        btnGuest.setText("Misafir Olarak Devam Et");
        btnGuest.setBackgroundColor(0xFF2D2B55);
        btnGuest.setTextColor(0xFF9B8EC4);
        btnGuest.setTextSize(13);
        btnGuest.setPadding(32, 24, 32, 24);
        btnGuest.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnGuest.setOnClickListener(v -> signInAnonymously());
        root.addView(btnGuest);

        TextView info = new TextView(this);
        info.setText("Google ile giriş yaparak verilerinizi\ntüm cihazlarda senkronize edin.");
        info.setTextSize(11);
        info.setTextColor(0xFF4A4560);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 40, 0, 0);
        root.addView(info);

        setContentView(root);
    }

    private void startGoogleSignIn() {
        btnGoogle.setEnabled(false);
        btnGoogle.setText("Bekleniyor...");
        // Önce çıkış yap ki hesap seçici çıksın
        mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        btnGoogle.setEnabled(true);
        btnGoogle.setText("Google ile Giriş Yap");

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task =
                GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "Google sign in OK: " + account.getEmail());
                firebaseAuth(account.getIdToken());
            } catch (ApiException e) {
                Log.e(TAG, "Google sign in failed: " + e.getStatusCode() + " - " + e.getMessage());
                Toast.makeText(this,
                    "Google girişi başarısız (Kod: " + e.getStatusCode() + ")",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuth(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
            .addOnSuccessListener(r -> {
                Log.d(TAG, "Firebase auth OK");
                startMain();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Firebase auth failed: " + e.getMessage());
                Toast.makeText(this, "Firebase hatası: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            });
    }

    private void signInAnonymously() {
        mAuth.signInAnonymously()
            .addOnSuccessListener(r -> startMain())
            .addOnFailureListener(e -> startMain()); // offline bile çalışsın
    }

    private void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
