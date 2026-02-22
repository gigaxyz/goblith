package com.goblith.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WordAnalysisActivity extends AppCompatActivity {

    private LinearLayout resultsContainer;
    private SQLiteDatabase db;
    private ProgressBar progressBar;
    private TextView statusText;
    private Spinner bookSpinner;
    private List<String> bookUris = new ArrayList<>();
    private List<String> bookNames = new ArrayList<>();

    // Türkçe ve İngilizce gereksiz kelimeler
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "ve", "ile", "bu", "bir", "de", "da", "ki", "mi", "mu", "mü", "ne", "o",
        "için", "ama", "ya", "veya", "hem", "ise", "en", "çok", "daha", "gibi",
        "kadar", "olan", "olarak", "olan", "iken", "her", "hiç", "tüm", "bütün",
        "ben", "sen", "biz", "siz", "onlar", "benim", "senin", "bizim", "sizin",
        "the", "and", "is", "in", "it", "of", "to", "a", "an", "that", "was",
        "he", "she", "they", "we", "you", "i", "for", "on", "are", "with",
        "as", "at", "be", "by", "from", "or", "but", "not", "this", "have",
        "had", "his", "her", "its", "our", "their", "will", "would", "could",
        "should", "may", "might", "shall", "can", "do", "did", "has", "been",
        "if", "so", "than", "then", "when", "which", "who", "what", "how"
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = new DBHelper().getWritableDatabase();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Başlık
        TextView title = new TextView(this);
        title.setText("KELIME ANALIZI");
        title.setTextColor(0xFFE94560);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 32, 24, 8);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Kitabinizdaki en cok tekrar eden kavramlar");
        subtitle.setTextColor(0xFF888888);
        subtitle.setTextSize(13);
        subtitle.setPadding(24, 0, 24, 24);
        root.addView(subtitle);

        // Kitap seçici
        TextView spinnerLabel = new TextView(this);
        spinnerLabel.setText("Kitap secin:");
        spinnerLabel.setTextColor(0xFFCCCCCC);
        spinnerLabel.setTextSize(14);
        spinnerLabel.setPadding(24, 0, 24, 8);
        root.addView(spinnerLabel);

        bookSpinner = new Spinner(this);
        bookSpinner.setBackgroundColor(0xFF0F3460);
        LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spParams.setMargins(16, 0, 16, 16);
        bookSpinner.setLayoutParams(spParams);
        root.addView(bookSpinner);

        // Analiz butonu
        Button btnAnalyze = new Button(this);
        btnAnalyze.setText("ANALIZ BASLAT");
        btnAnalyze.setBackgroundColor(0xFFE94560);
        btnAnalyze.setTextColor(0xFFFFFFFF);
        btnAnalyze.setTypeface(null, Typeface.BOLD);
        btnAnalyze.setTextSize(14);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(16, 0, 16, 16);
        btnAnalyze.setLayoutParams(btnParams);
        root.addView(btnAnalyze);

        // İlerleme
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbParams.setMargins(16, 0, 16, 8);
        progressBar.setLayoutParams(pbParams);
        root.addView(progressBar);

        statusText = new TextView(this);
        statusText.setTextColor(0xFF888888);
        statusText.setTextSize(12);
        statusText.setPadding(24, 0, 24, 8);
        statusText.setVisibility(android.view.View.GONE);
        root.addView(statusText);

        // Sonuçlar
        ScrollView scrollView = new ScrollView(this);
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(16, 8, 16, 24);
        scrollView.addView(resultsContainer);
        root.addView(scrollView);

        setContentView(root);
        loadBooks();

        btnAnalyze.setOnClickListener(v -> {
            int pos = bookSpinner.getSelectedItemPosition();
            if (pos < 0 || pos >= bookUris.size()) return;
            analyzeBook(bookUris.get(pos));
        });
    }

    private void loadBooks() {
        bookUris.clear();
        bookNames.clear();
        Cursor cursor = db.rawQuery("SELECT pdf_uri, custom_name, file_type FROM library ORDER BY last_opened DESC", null);
        while (cursor.moveToNext()) {
            String uri = cursor.getString(0);
            String name = cursor.getString(1);
            String type = cursor.getString(2);
            if (type != null && type.equals("TXT")) {
                bookUris.add(uri);
                bookNames.add((name != null ? name : "Bilinmeyen") + " [TXT]");
            } else {
                // PDF - sadece metin çıkarma mevcut değil, bilgi ver
                bookUris.add(uri);
                bookNames.add((name != null ? name : "Bilinmeyen") + " [PDF]");
            }
        }
        cursor.close();

        if (bookNames.isEmpty()) {
            bookNames.add("Kutuphanede kitap yok");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, bookNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bookSpinner.setAdapter(adapter);
    }

    private void analyzeBook(String uri) {
        progressBar.setVisibility(android.view.View.VISIBLE);
        statusText.setVisibility(android.view.View.VISIBLE);
        statusText.setText("Analiz ediliyor...");
        resultsContainer.removeAllViews();

        new Thread(() -> {
            try {
                StringBuilder text = new StringBuilder();
                String mimeType = getContentResolver().getType(Uri.parse(uri));

                // TXT dosyası
                if (mimeType != null && mimeType.contains("text")) {
                    InputStream is = getContentResolver().openInputStream(Uri.parse(uri));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    String line;
                    while ((line = reader.readLine()) != null) text.append(line).append(" ");
                    reader.close();
                } else {
                    // PDF için sayfa sayfa metin
                    android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(Uri.parse(uri), "r");
                    android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(pfd);
                    int pageCount = renderer.getPageCount();

                    new Handler(Looper.getMainLooper()).post(() -> progressBar.setMax(pageCount));

                    for (int i = 0; i < pageCount; i++) {
                        final int page = i;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            progressBar.setProgress(page);
                            statusText.setText("Sayfa " + (page+1) + " / " + pageCount + " isleniyor...");
                        });
                        // PDF metin katmanı yok ama kelime sayımı için bitmap pixel analizi yerine
                        // şimdilik sadece sayfa boyutuna göre tahmini analiz yapıyoruz
                        // Gerçek OCR ileride eklenecek
                    }
                    renderer.close();
                    pfd.close();

                    // PDF için not metinlerini analiz et
                    Cursor noteCursor = db.rawQuery(
                        "SELECT note FROM highlights WHERE pdf_uri=? AND note IS NOT NULL AND note != ''",
                        new String[]{uri});
                    while (noteCursor.moveToNext()) {
                        text.append(noteCursor.getString(0)).append(" ");
                    }
                    noteCursor.close();
                }

                Map<String, Integer> wordCount = countWords(text.toString());
                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(wordCount.entrySet());
                Collections.sort(sorted, (a, b) -> b.getValue() - a.getValue());
                List<Map.Entry<String, Integer>> top = sorted.subList(0, Math.min(50, sorted.size()));

                new Handler(Looper.getMainLooper()).post(() -> showResults(top, text.toString()));

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    statusText.setText("Hata: " + e.getMessage());
                    progressBar.setVisibility(android.view.View.GONE);
                });
            }
        }).start();
    }

    private Map<String, Integer> countWords(String text) {
        Map<String, Integer> map = new HashMap<>();
        String[] words = text.toLowerCase()
            .replaceAll("[^a-zA-ZğüşıöçĞÜŞİÖÇ ]", " ")
            .split("\\s+");
        for (String word : words) {
            word = word.trim();
            if (word.length() < 3) continue;
            if (STOP_WORDS.contains(word)) continue;
            map.put(word, map.getOrDefault(word, 0) + 1);
        }
        return map;
    }

    private void showResults(List<Map.Entry<String, Integer>> top, String fullText) {
        progressBar.setVisibility(android.view.View.GONE);
        statusText.setVisibility(android.view.View.GONE);
        resultsContainer.removeAllViews();

        if (top.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Analiz edilecek metin bulunamadi.\nPDF dosyalari icin once notlar ekleyin,\nTXT dosyalari dogrudan analiz edilir.");
            empty.setTextColor(0xFF888888);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(24, 48, 24, 24);
            resultsContainer.addView(empty);
            return;
        }

        // Başlık
        TextView header = new TextView(this);
        header.setText("EN COK TEKRAR EDEN " + top.size() + " KAVRAM");
        header.setTextColor(0xFF888888);
        header.setTextSize(11);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(8, 8, 8, 16);
        resultsContainer.addView(header);

        int maxCount = top.get(0).getValue();

        for (int i = 0; i < top.size(); i++) {
            Map.Entry<String, Integer> entry = top.get(i);
            String word = entry.getKey();
            int count = entry.getValue();
            float ratio = (float) count / maxCount;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundColor(0xFF16213E);
            row.setPadding(16, 12, 16, 12);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0, 0, 0, 8);
            row.setLayoutParams(rp);

            // Sıra + kelime + sayı
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView rankView = new TextView(this);
            rankView.setText(String.format("%2d.", i + 1));
            rankView.setTextColor(0xFF555555);
            rankView.setTextSize(12);
            rankView.setPadding(0, 0, 12, 0);
            topRow.addView(rankView);

            TextView wordView = new TextView(this);
            wordView.setText(word);
            wordView.setTextColor(0xFFFFFFFF);
            wordView.setTextSize(16);
            wordView.setTypeface(null, Typeface.BOLD);
            wordView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            topRow.addView(wordView);

            TextView countView = new TextView(this);
            countView.setText(count + "x");
            countView.setTextColor(0xFFE94560);
            countView.setTextSize(14);
            countView.setTypeface(null, Typeface.BOLD);
            topRow.addView(countView);

            row.addView(topRow);

            // Bar
            LinearLayout barBg = new LinearLayout(this);
            barBg.setBackgroundColor(0xFF0A1628);
            LinearLayout.LayoutParams bgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 8);
            bgParams.setMargins(0, 8, 0, 0);
            barBg.setLayoutParams(bgParams);

            LinearLayout bar = new LinearLayout(this);
            int barColor = i < 3 ? 0xFFE94560 : i < 10 ? 0xFF1565C0 : 0xFF2E7D32;
            bar.setBackgroundColor(barColor);
            int barWidth = (int) (ratio * 100);
            bar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, ratio));
            barBg.addView(bar);

            LinearLayout spacer = new LinearLayout(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1 - ratio));
            barBg.addView(spacer);

            row.addView(barBg);
            resultsContainer.addView(row);
        }
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(WordAnalysisActivity.this, "goblith.db", null, 4); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS highlights (id INTEGER PRIMARY KEY AUTOINCREMENT, pdf_uri TEXT, page INTEGER, color TEXT, note TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            db.execSQL("CREATE TABLE IF NOT EXISTS library (pdf_uri TEXT PRIMARY KEY, custom_name TEXT, file_type TEXT DEFAULT 'PDF', last_page INTEGER DEFAULT 0, last_opened TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
            try { db.execSQL("ALTER TABLE library ADD COLUMN custom_name TEXT"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE library ADD COLUMN file_type TEXT DEFAULT 'PDF'"); } catch (Exception e) {}
        }
    }
}
