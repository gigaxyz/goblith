package com.goblith.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlashcardActivity extends AppCompatActivity {

    private SQLiteDatabase db;
    private List<String[]> cards = new ArrayList<>(); // [color, page, note, tag, bookName]
    private int currentIndex = 0;
    private boolean isFlipped = false;
    private int correctCount = 0;
    private int totalSeen = 0;

    private LinearLayout cardFront, cardBack, cardContainer;
    private TextView tvProgress, tvCorrect, tvTotal;
    private Button btnFlip, btnKnow, btnDontKnow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Başlık
        TextView title = new TextView(this);
        title.setText("FLASHCARD");
        title.setTextColor(0xFFE94560);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 32, 24, 4);
        root.addView(title);

        // İstatistik satırı
        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setPadding(24, 0, 24, 16);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);

        tvProgress = new TextView(this);
        tvProgress.setTextColor(0xFF888888);
        tvProgress.setTextSize(13);
        tvProgress.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statsRow.addView(tvProgress);

        LinearLayout scoreBox = new LinearLayout(this);
        scoreBox.setOrientation(LinearLayout.HORIZONTAL);
        scoreBox.setGravity(Gravity.CENTER_VERTICAL);

        tvCorrect = new TextView(this);
        tvCorrect.setTextColor(0xFF44CC66);
        tvCorrect.setTextSize(15);
        tvCorrect.setTypeface(null, Typeface.BOLD);
        scoreBox.addView(tvCorrect);

        TextView slash = new TextView(this);
        slash.setText("  /  ");
        slash.setTextColor(0xFF555555);
        slash.setTextSize(13);
        scoreBox.addView(slash);

        tvTotal = new TextView(this);
        tvTotal.setTextColor(0xFF888888);
        tvTotal.setTextSize(15);
        scoreBox.addView(tvTotal);

        statsRow.addView(scoreBox);
        root.addView(statsRow);

        // Kart alanı
        cardContainer = new LinearLayout(this);
        cardContainer.setOrientation(LinearLayout.VERTICAL);
        cardContainer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ccP = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        ccP.setMargins(16, 0, 16, 0);
        cardContainer.setLayoutParams(ccP);
        cardContainer.setPadding(8, 8, 8, 8);

        // Ön yüz
        cardFront = new LinearLayout(this);
        cardFront.setOrientation(LinearLayout.VERTICAL);
        cardFront.setBackgroundColor(0xFF16213E);
        cardFront.setGravity(Gravity.CENTER);
        cardFront.setPadding(32, 40, 32, 40);
        cardFront.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView frontHint = new TextView(this);
        frontHint.setText("KATEGORİ & SAYFA");
        frontHint.setTextColor(0xFF555555);
        frontHint.setTextSize(11);
        frontHint.setTypeface(null, Typeface.BOLD);
        frontHint.setGravity(Gravity.CENTER);
        cardFront.addView(frontHint);

        TextView frontCategory = new TextView(this);
        frontCategory.setTag("category");
        frontCategory.setTextColor(0xFFE94560);
        frontCategory.setTextSize(28);
        frontCategory.setTypeface(null, Typeface.BOLD);
        frontCategory.setGravity(Gravity.CENTER);
        frontCategory.setPadding(0, 16, 0, 8);
        cardFront.addView(frontCategory);

        TextView frontPage = new TextView(this);
        frontPage.setTag("page");
        frontPage.setTextColor(0xFF888888);
        frontPage.setTextSize(16);
        frontPage.setGravity(Gravity.CENTER);
        cardFront.addView(frontPage);

        TextView frontTag = new TextView(this);
        frontTag.setTag("tag");
        frontTag.setTextColor(0xFF6A1B9A);
        frontTag.setTextSize(13);
        frontTag.setGravity(Gravity.CENTER);
        frontTag.setPadding(0, 8, 0, 0);
        cardFront.addView(frontTag);

        TextView frontPrompt = new TextView(this);
        frontPrompt.setText("\n\nNotunu hatırlıyor musun?");
        frontPrompt.setTextColor(0xFF444466);
        frontPrompt.setTextSize(13);
        frontPrompt.setGravity(Gravity.CENTER);
        cardFront.addView(frontPrompt);

        cardContainer.addView(cardFront);

        // Arka yüz
        cardBack = new LinearLayout(this);
        cardBack.setOrientation(LinearLayout.VERTICAL);
        cardBack.setBackgroundColor(0xFF0F2040);
        cardBack.setGravity(Gravity.CENTER);
        cardBack.setPadding(32, 40, 32, 40);
        cardBack.setVisibility(android.view.View.GONE);
        cardBack.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView backHint = new TextView(this);
        backHint.setText("NOTUN");
        backHint.setTextColor(0xFF555555);
        backHint.setTextSize(11);
        backHint.setTypeface(null, Typeface.BOLD);
        backHint.setGravity(Gravity.CENTER);
        cardBack.addView(backHint);

        TextView backNote = new TextView(this);
        backNote.setTag("note");
        backNote.setTextColor(0xFFFFFFFF);
        backNote.setTextSize(18);
        backNote.setGravity(Gravity.CENTER);
        backNote.setPadding(0, 20, 0, 8);
        backNote.setLineSpacing(4, 1.3f);
        cardBack.addView(backNote);

        TextView backBook = new TextView(this);
        backBook.setTag("book");
        backBook.setTextColor(0xFF555566);
        backBook.setTextSize(12);
        backBook.setGravity(Gravity.CENTER);
        backBook.setPadding(0, 12, 0, 0);
        cardBack.addView(backBook);

        cardContainer.addView(cardBack);
        root.addView(cardContainer);

        // Butonlar
        LinearLayout btnRow1 = new LinearLayout(this);
        btnRow1.setOrientation(LinearLayout.HORIZONTAL);
        btnRow1.setPadding(16, 8, 16, 8);

        btnFlip = new Button(this);
        btnFlip.setText("↩  CEVABİ GÖR");
        btnFlip.setBackgroundColor(0xFF0F3460);
        btnFlip.setTextColor(0xFFFFFFFF);
        btnFlip.setTextSize(14);
        btnFlip.setTypeface(null, Typeface.BOLD);
        btnFlip.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnFlip.setPadding(16, 16, 16, 16);
        btnRow1.addView(btnFlip);
        root.addView(btnRow1);

        LinearLayout btnRow2 = new LinearLayout(this);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);
        btnRow2.setPadding(16, 0, 16, 24);

        btnKnow = new Button(this);
        btnKnow.setText("✓  BİLİYORUM");
        btnKnow.setBackgroundColor(0xFF2E7D32);
        btnKnow.setTextColor(0xFFFFFFFF);
        btnKnow.setTextSize(14);
        btnKnow.setTypeface(null, Typeface.BOLD);
        btnKnow.setVisibility(android.view.View.GONE);
        btnKnow.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btnKnow.setPadding(16, 16, 16, 16);
        btnRow2.addView(btnKnow);

        btnDontKnow = new Button(this);
        btnDontKnow.setText("✗  TEKRAR ET");
        btnDontKnow.setBackgroundColor(0xFFE94560);
        btnDontKnow.setTextColor(0xFFFFFFFF);
        btnDontKnow.setTextSize(14);
        btnDontKnow.setTypeface(null, Typeface.BOLD);
        btnDontKnow.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams dkP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        dkP.setMargins(10, 0, 0, 0);
        btnDontKnow.setLayoutParams(dkP);
        btnDontKnow.setPadding(16, 16, 16, 16);
        btnRow2.addView(btnDontKnow);

        root.addView(btnRow2);
        setContentView(root);

        loadCards();

        btnFlip.setOnClickListener(v -> {
            if (!isFlipped) {
                cardFront.setVisibility(android.view.View.GONE);
                cardBack.setVisibility(android.view.View.VISIBLE);
                btnFlip.setVisibility(android.view.View.GONE);
                btnKnow.setVisibility(android.view.View.VISIBLE);
                btnDontKnow.setVisibility(android.view.View.VISIBLE);
                isFlipped = true;
            }
        });

        btnKnow.setOnClickListener(v -> {
            correctCount++;
            totalSeen++;
            nextCard(true);
        });

        btnDontKnow.setOnClickListener(v -> {
            // Kartı listenin sonuna ekle
            if (currentIndex < cards.size()) {
                cards.add(cards.get(currentIndex));
            }
            totalSeen++;
            nextCard(false);
        });
    }

    private void loadCards() {
        cards.clear();
        Cursor c = db.rawQuery(
            "SELECT h.color, h.page, h.note, h.tag, l.custom_name " +
            "FROM highlights h LEFT JOIN library l ON h.pdf_uri=l.pdf_uri " +
            "WHERE h.note IS NOT NULL AND h.note != '' ORDER BY RANDOM()", null);
        while (c.moveToNext()) {
            cards.add(new String[]{
                c.getString(0), String.valueOf(c.getInt(1)+1),
                c.getString(2), c.getString(3), c.getString(4)
            });
        }
        c.close();

        if (cards.isEmpty()) {
            Toast.makeText(this, "Henuz not yok! Once kitap okuyup not ekle.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        currentIndex = 0;
        showCard(0);
    }

    private void showCard(int index) {
        if (index >= cards.size()) {
            showFinish();
            return;
        }

        String[] card = cards.get(index);
        String color = card[0] != null ? card[0] : "green";
        String page = card[1];
        String note = card[2];
        String tag = card[3];
        String book = card[4];

        String label;
        int labelColor;
        switch (color) {
            case "red":  label="İTİRAZ";  labelColor=0xFFE94560; break;
            case "blue": label="ARGÜMAN"; labelColor=0xFF4488FF; break;
            default:     label="VERİ";    labelColor=0xFF44CC66; break;
        }

        ((TextView)cardFront.findViewWithTag("category")).setText(label);
        ((TextView)cardFront.findViewWithTag("category")).setTextColor(labelColor);
        ((TextView)cardFront.findViewWithTag("page")).setText("Sayfa " + page);
        TextView tagView = cardFront.findViewWithTag("tag");
        tagView.setText(tag != null && !tag.isEmpty() ? "# " + tag : "");

        ((TextView)cardBack.findViewWithTag("note")).setText(note);
        ((TextView)cardBack.findViewWithTag("book")).setText(book != null ? book : "");

        cardFront.setVisibility(android.view.View.VISIBLE);
        cardBack.setVisibility(android.view.View.GONE);
        btnFlip.setVisibility(android.view.View.VISIBLE);
        btnKnow.setVisibility(android.view.View.GONE);
        btnDontKnow.setVisibility(android.view.View.GONE);
        isFlipped = false;

        tvProgress.setText("Kart " + (index+1) + " / " + cards.size());
        tvCorrect.setText(String.valueOf(correctCount));
        tvTotal.setText(String.valueOf(totalSeen));
    }

    private void nextCard(boolean knew) {
        currentIndex++;
        showCard(currentIndex);
    }

    private void showFinish() {
        cardFront.setVisibility(android.view.View.GONE);
        cardBack.setVisibility(android.view.View.GONE);
        btnFlip.setVisibility(android.view.View.GONE);
        btnKnow.setVisibility(android.view.View.GONE);
        btnDontKnow.setVisibility(android.view.View.GONE);

        LinearLayout finishCard = new LinearLayout(this);
        finishCard.setOrientation(LinearLayout.VERTICAL);
        finishCard.setBackgroundColor(0xFF16213E);
        finishCard.setGravity(Gravity.CENTER);
        finishCard.setPadding(32, 48, 32, 48);
        finishCard.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView emoji = new TextView(this);
        emoji.setText(correctCount >= cards.size()*0.8 ? "🏆" : correctCount >= cards.size()*0.5 ? "👍" : "📚");
        emoji.setTextSize(48);
        emoji.setGravity(Gravity.CENTER);
        finishCard.addView(emoji);

        TextView finishTitle = new TextView(this);
        finishTitle.setText("Tamamlandı!");
        finishTitle.setTextColor(0xFFFFFFFF);
        finishTitle.setTextSize(24);
        finishTitle.setTypeface(null, Typeface.BOLD);
        finishTitle.setGravity(Gravity.CENTER);
        finishTitle.setPadding(0, 16, 0, 8);
        finishCard.addView(finishTitle);

        int pct = cards.isEmpty() ? 0 : (int)(correctCount * 100.0 / totalSeen);
        TextView finishScore = new TextView(this);
        finishScore.setText("Başarı oranı: %" + pct + "\n" + correctCount + " / " + totalSeen + " bildin");
        finishScore.setTextColor(0xFF888888);
        finishScore.setTextSize(15);
        finishScore.setGravity(Gravity.CENTER);
        finishCard.addView(finishScore);

        cardContainer.addView(finishCard);

        Button btnRestart = new Button(this);
        btnRestart.setText("↺  TEKRAR BAŞLA");
        btnRestart.setBackgroundColor(0xFFE94560);
        btnRestart.setTextColor(0xFFFFFFFF);
        btnRestart.setTextSize(14);
        btnRestart.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(16, 8, 16, 0);
        btnRestart.setLayoutParams(rp);
        btnRestart.setPadding(16, 16, 16, 16);

        // root'a ekle — layout trick
        ((LinearLayout) cardContainer.getParent()).addView(btnRestart,
            ((LinearLayout)cardContainer.getParent()).indexOfChild(cardContainer)+1);

        btnRestart.setOnClickListener(v -> {
            ((LinearLayout)cardContainer.getParent()).removeView(btnRestart);
            cardContainer.removeView(finishCard);
            correctCount = 0;
            totalSeen = 0;
            loadCards();
        });
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(FlashcardActivity.this, "goblith.db", null, 8); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, tag TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try{db.execSQL("CREATE TABLE IF NOT EXISTS archive (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, book_name TEXT, page INTEGER, quote TEXT, topic TEXT, importance INTEGER DEFAULT 2, created_at TEXT)");}catch(Exception e){}
            try { db.execSQL("ALTER TABLE highlights ADD COLUMN tag TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception e) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS page_highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, x1 REAL, y1 REAL, x2 REAL, y2 REAL, color TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)"); } catch (Exception e) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, title TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)"); } catch (Exception e) {}
            try { db.execSQL("CREATE TABLE IF NOT EXISTS reading_list (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, status TEXT DEFAULT 'okunacak', notes_text TEXT, added_date TEXT)"); } catch (Exception e) {}
        }
    }
}
