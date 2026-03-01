package com.goblith.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;

public class LoginActivity extends Activity {
    private static final int RC_SIGN_IN = 9001;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            startMain(); return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // UI
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

        Button btnGoogle = new Button(this);
        btnGoogle.setText("Google ile Giriş Yap");
        btnGoogle.setBackgroundColor(0xFF7C3AED);
        btnGoogle.setTextColor(0xFFE2D9F3);
        btnGoogle.setTextSize(15);
        btnGoogle.setTypeface(null, android.graphics.Typeface.BOLD);
        btnGoogle.setPadding(32, 24, 32, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 16);
        btnGoogle.setLayoutParams(lp);
        btnGoogle.setOnClickListener(v -> signInWithGoogle());
        root.addView(btnGoogle);

        Button btnGuest = new Button(this);
        btnGuest.setText("Giriş Yapmadan Devam Et");
        btnGuest.setBackgroundColor(0xFF2D2B55);
        btnGuest.setTextColor(0xFF9B8EC4);
        btnGuest.setTextSize(13);
        btnGuest.setPadding(32, 20, 32, 20);
        btnGuest.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnGuest.setOnClickListener(v -> startMain());
        root.addView(btnGuest);

        TextView info = new TextView(this);
        info.setText("Giriş yaparak notlarınızı, arşivinizi ve okuma\nilerlemenizi tüm cihazlarınızda senkronize edin.");
        info.setTextSize(11);
        info.setTextColor(0xFF4A4560);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, 32, 0, 0);
        root.addView(info);

        setContentView(root);
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Giriş başarısız: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                startMain();
            } else {
                Toast.makeText(this, "Firebase girişi başarısız", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
