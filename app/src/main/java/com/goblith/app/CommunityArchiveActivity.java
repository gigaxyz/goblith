package com.goblith.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Typeface;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class CommunityArchiveActivity extends AppCompatActivity {

    private DatabaseReference dbRef;
    private LinearLayout listContainer;
    private ProgressBar progressBar;
    private String userName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbRef = FirebaseDatabase.getInstance("https://goblith-default-rtdb.firebaseio.com/")
                .getReference("community_archive");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Başlık
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setPadding(24, 40, 24, 8);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("TOPLULUK ARŞİVİ");
        title.setTextColor(0xFFE94560);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleBar.addView(title);

        Button btnName = new Button(this);
        btnName.setText("👤 AD");
        btnName.setBackgroundColor(0xFF1A3A6A);
        btnName.setTextColor(0xFFFFFFFF);
        btnName.setTextSize(11);
        btnName.setPadding(16, 8, 16, 8);
        titleBar.addView(btnName);
        root.addView(titleBar);

        // Bilgi satırı
        TextView info = new TextView(this);
        info.setText("Tüm kullanıcıların paylaştığı alıntılar");
        info.setTextColor(0xFF888888);
        info.setTextSize(12);
        info.setPadding(24, 0, 24, 16);
        root.addView(info);

        // Loading
        progressBar = new ProgressBar(this);
        progressBar.setPadding(0, 16, 0, 16);
        root.addView(progressBar);

        ScrollView sv = new ScrollView(this);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(16, 0, 16, 32);
        sv.addView(listContainer);
        root.addView(sv);

        setContentView(root);

        // Kullanıcı adı al
        btnName.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Takma Adın");
            EditText in = new EditText(this);
            in.setHint("Adını gir...");
            if (!userName.isEmpty()) in.setText(userName);
            b.setView(in);
            b.setPositiveButton("Kaydet", (d, w) -> {
                userName = in.getText().toString().trim();
                if (!userName.isEmpty()) {
                    btnName.setText("👤 " + userName);
                    Toast.makeText(this, "Ad kaydedildi", Toast.LENGTH_SHORT).show();
                }
            });
            b.setNegativeButton("İptal", null);
            b.show();
        });

        loadCommunityArchive();
    }

    private void loadCommunityArchive() {
        progressBar.setVisibility(android.view.View.VISIBLE);
        dbRef.orderByChild("timestamp").limitToLast(100)
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    progressBar.setVisibility(android.view.View.GONE);
                    listContainer.removeAllViews();

                    if (!snapshot.exists()) {
                        TextView empty = new TextView(CommunityArchiveActivity.this);
                        empty.setText("Henüz paylaşım yok.\nİlk paylaşımı sen yap!");
                        empty.setTextColor(0xFF555555);
                        empty.setTextSize(14);
                        empty.setGravity(Gravity.CENTER);
                        empty.setPadding(24, 64, 24, 24);
                        listContainer.addView(empty);
                        return;
                    }

                    // Yeniden eskiye sırala
                    java.util.List<DataSnapshot> items = new java.util.ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) items.add(0, child);

                    for (DataSnapshot child : items) {
                        String key      = child.getKey();
                        String quote    = child.child("quote").getValue(String.class);
                        String bookName = child.child("bookName").getValue(String.class);
                        String topic    = child.child("topic").getValue(String.class);
                        String user     = child.child("userName").getValue(String.class);
                        Long   page     = child.child("page").getValue(Long.class);
                        Long   imp      = child.child("importance").getValue(Long.class);

                        if (quote == null) continue;

                        LinearLayout card = new LinearLayout(CommunityArchiveActivity.this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setBackgroundColor(0xFF16213E);
                        card.setPadding(20, 16, 20, 16);
                        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        cp.setMargins(0, 0, 0, 14);
                        card.setLayoutParams(cp);

                        // Üst satır: yıldız + kaynak + sil
                        LinearLayout topRow = new LinearLayout(CommunityArchiveActivity.this);
                        topRow.setOrientation(LinearLayout.HORIZONTAL);
                        topRow.setGravity(Gravity.CENTER_VERTICAL);

                        int importance = imp != null ? imp.intValue() : 2;
                        String starStr = importance >= 3 ? "★★★" : importance == 2 ? "★★" : "★";
                        TextView stars = new TextView(CommunityArchiveActivity.this);
                        stars.setText(starStr);
                        stars.setTextColor(0xFFFFD700);
                        stars.setTextSize(13);
                        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        sp.setMargins(0, 0, 8, 0);
                        stars.setLayoutParams(sp);
                        topRow.addView(stars);

                        TextView src = new TextView(CommunityArchiveActivity.this);
                        String srcText = (bookName != null ? bookName : "?");
                        if (page != null) srcText += ", s." + (page + 1);
                        src.setText(srcText);
                        src.setTextColor(0xFF4488FF);
                        src.setTextSize(12);
                        src.setTypeface(null, Typeface.BOLD);
                        src.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                        topRow.addView(src);

                        Button btnDel = new Button(CommunityArchiveActivity.this);
                        btnDel.setText("Sil");
                        btnDel.setBackgroundColor(0xFF333333);
                        btnDel.setTextColor(0xFF888888);
                        btnDel.setTextSize(10);
                        btnDel.setPadding(12, 4, 12, 4);
                        topRow.addView(btnDel);
                        card.addView(topRow);

                        // Alıntı
                        TextView quoteView = new TextView(CommunityArchiveActivity.this);
                        quoteView.setText(quote);
                        quoteView.setTextColor(0xFFDDDDDD);
                        quoteView.setTextSize(15);
                        quoteView.setPadding(0, 10, 0, 8);
                        quoteView.setLineSpacing(4, 1.3f);
                        card.addView(quoteView);

                        // Alt satır: konu + kullanıcı
                        LinearLayout bottomRow = new LinearLayout(CommunityArchiveActivity.this);
                        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
                        bottomRow.setGravity(Gravity.CENTER_VERTICAL);

                        if (topic != null && !topic.isEmpty()) {
                            TextView topicTag = new TextView(CommunityArchiveActivity.this);
                            topicTag.setText(" " + topic + " ");
                            topicTag.setTextColor(0xFFFFFFFF);
                            topicTag.setTextSize(10);
                            topicTag.setTypeface(null, Typeface.BOLD);
                            topicTag.setBackgroundColor(0xFF6A1B9A);
                            topicTag.setPadding(10, 4, 10, 4);
                            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            tp.setMargins(0, 0, 8, 0);
                            topicTag.setLayoutParams(tp);
                            bottomRow.addView(topicTag);
                        }

                        TextView userView = new TextView(CommunityArchiveActivity.this);
                        userView.setText("— " + (user != null ? user : "Anonim"));
                        userView.setTextColor(0xFF44CC66);
                        userView.setTextSize(11);
                        userView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                        bottomRow.addView(userView);
                        card.addView(bottomRow);
                        listContainer.addView(card);

                        final String fKey = key;
                        btnDel.setOnClickListener(v -> new AlertDialog.Builder(CommunityArchiveActivity.this)
                            .setTitle("Sil")
                            .setMessage("Bu paylaşım silinsin mi?")
                            .setPositiveButton("Sil", (d, w) -> {
                                dbRef.child(fKey).removeValue();
                                Toast.makeText(CommunityArchiveActivity.this, "Silindi", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("İptal", null)
                            .show());
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    progressBar.setVisibility(android.view.View.GONE);
                    Toast.makeText(CommunityArchiveActivity.this,
                        "Bağlantı hatası: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
    }

    public void shareToFirebase(String bookName, int page, String quote, String topic, int importance) {
        if (userName.isEmpty()) {
            Toast.makeText(this, "Önce adını gir (sağ üst 👤 butonu)", Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("bookName", bookName);
        data.put("page", page);
        data.put("quote", quote);
        data.put("topic", topic);
        data.put("importance", importance);
        data.put("userName", userName);
        data.put("timestamp", System.currentTimeMillis());

        dbRef.push().setValue(data)
            .addOnSuccessListener(a -> Toast.makeText(this, "Toplulukla paylaşıldı!", Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e -> Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
