package com.goblith.app;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
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
import androidx.appcompat.app.AppCompatActivity;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private SQLiteDatabase db;
    private EditText searchInput;
    private LinearLayout resultsContainer;
    private ProgressBar progressBar;
    private TextView resultCount;

    static class SearchResult {
        String pdfUri, bookName, context;
        int page;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper().getWritableDatabase();
        PDFBoxResourceLoader.init(getApplicationContext());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A1A2E);

        // Başlık
        TextView title = new TextView(this);
        title.setText("PDF ARAMA");
        title.setTextColor(0xFFE94560);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(24, 40, 24, 16);
        root.addView(title);

        // Arama kutusu
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(16, 0, 16, 16);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        searchInput = new EditText(this);
        searchInput.setHint("Aramak istediğin kelime...");
        searchInput.setTextColor(0xFFFFFFFF);
        searchInput.setHintTextColor(0xFF555555);
        searchInput.setBackgroundColor(0xFF16213E);
        searchInput.setPadding(16, 14, 16, 14);
        searchInput.setTextSize(15);
        searchInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        searchRow.addView(searchInput);

        Button btnSearch = new Button(this);
        btnSearch.setText("ARA");
        btnSearch.setBackgroundColor(0xFFE94560);
        btnSearch.setTextColor(0xFFFFFFFF);
        btnSearch.setTextSize(14);
        btnSearch.setTypeface(null, Typeface.BOLD);
        btnSearch.setPadding(24, 14, 24, 14);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(10, 0, 0, 0);
        btnSearch.setLayoutParams(blp);
        searchRow.addView(btnSearch);
        root.addView(searchRow);

        // Sonuç sayısı
        resultCount = new TextView(this);
        resultCount.setTextColor(0xFF888888);
        resultCount.setTextSize(12);
        resultCount.setPadding(24, 0, 24, 8);
        root.addView(resultCount);

        // Yükleniyor
        progressBar = new ProgressBar(this);
        progressBar.setVisibility(android.view.View.GONE);
        progressBar.setPadding(0, 8, 0, 8);
        root.addView(progressBar);

        // Sonuçlar
        ScrollView sv = new ScrollView(this);
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(16, 0, 16, 32);
        sv.addView(resultsContainer);
        root.addView(sv);

        setContentView(root);

        btnSearch.setOnClickListener(v -> {
            String query = searchInput.getText().toString().trim();
            if (query.isEmpty()) {
                Toast.makeText(this, "Kelime gir", Toast.LENGTH_SHORT).show();
                return;
            }
            doSearch(query);
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            btnSearch.performClick();
            return true;
        });
    }

    private void doSearch(String query) {
        progressBar.setVisibility(android.view.View.VISIBLE);
        resultsContainer.removeAllViews();
        resultCount.setText("Aranıyor...");

        new AsyncTask<String, Void, List<SearchResult>>() {
            @Override
            protected List<SearchResult> doInBackground(String... params) {
                String q = params[0].toLowerCase();
                List<SearchResult> results = new ArrayList<>();

                Cursor c = db.rawQuery(
                    "SELECT pdf_uri, custom_name FROM library WHERE file_type='PDF' OR file_type IS NULL",
                    null);

                while (c.moveToNext()) {
                    String uri = c.getString(0);
                    String name = c.getString(1) != null ? c.getString(1) : "?";
                    try {
                        InputStream is = getContentResolver().openInputStream(Uri.parse(uri));
                        if (is == null) continue;
                        PDDocument doc = PDDocument.load(is);
                        int pageCount = doc.getNumberOfPages();

                        for (int p = 0; p < pageCount; p++) {
                            PDFTextStripper stripper = new PDFTextStripper();
                            stripper.setStartPage(p + 1);
                            stripper.setEndPage(p + 1);
                            String text = stripper.getText(doc);
                            if (text.toLowerCase().contains(q)) {
                                // Bağlamı bul
                                int idx = text.toLowerCase().indexOf(q);
                                int start = Math.max(0, idx - 80);
                                int end = Math.min(text.length(), idx + q.length() + 80);
                                String context = "..." + text.substring(start, end).replace("\n", " ") + "...";

                                SearchResult r = new SearchResult();
                                r.pdfUri = uri;
                                r.bookName = name;
                                r.page = p;
                                r.context = context;
                                results.add(r);

                                if (results.size() >= 50) break; // max 50 sonuç
                            }
                        }
                        doc.close();
                        is.close();
                    } catch (Exception ignored) {}
                }
                c.close();
                return results;
            }

            @Override
            protected void onPostExecute(List<SearchResult> results) {
                progressBar.setVisibility(android.view.View.GONE);
                resultCount.setText(results.size() + " sonuç bulundu");

                if (results.isEmpty()) {
                    TextView empty = new TextView(SearchActivity.this);
                    empty.setText("Sonuç bulunamadı.");
                    empty.setTextColor(0xFF555555);
                    empty.setTextSize(14);
                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(24, 48, 24, 24);
                    resultsContainer.addView(empty);
                    return;
                }

                String q = query;
                for (SearchResult r : results) {
                    LinearLayout card = new LinearLayout(SearchActivity.this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setBackgroundColor(0xFF16213E);
                    card.setPadding(20, 14, 20, 14);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cp.setMargins(0, 0, 0, 12);
                    card.setLayoutParams(cp);

                    // Kaynak satırı
                    LinearLayout topRow = new LinearLayout(SearchActivity.this);
                    topRow.setOrientation(LinearLayout.HORIZONTAL);
                    topRow.setGravity(Gravity.CENTER_VERTICAL);

                    TextView src = new TextView(SearchActivity.this);
                    src.setText(r.bookName + "  —  Sayfa " + (r.page + 1));
                    src.setTextColor(0xFF4488FF);
                    src.setTextSize(13);
                    src.setTypeface(null, Typeface.BOLD);
                    src.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    topRow.addView(src);

                    Button btnGo = new Button(SearchActivity.this);
                    btnGo.setText("Git");
                    btnGo.setBackgroundColor(0xFFE94560);
                    btnGo.setTextColor(0xFFFFFFFF);
                    btnGo.setTextSize(11);
                    btnGo.setPadding(16, 6, 16, 6);
                    topRow.addView(btnGo);
                    card.addView(topRow);

                    // Bağlam metni
                    TextView ctx = new TextView(SearchActivity.this);
                    // Aranan kelimeyi büyük harfle vurgula
                    String display = r.context.replace(q, "[" + q.toUpperCase() + "]");
                    ctx.setText(display);
                    ctx.setTextColor(0xFFCCCCCC);
                    ctx.setTextSize(13);
                    ctx.setPadding(0, 8, 0, 0);
                    ctx.setLineSpacing(3, 1.2f);
                    card.addView(ctx);
                    resultsContainer.addView(card);

                    final String fUri = r.pdfUri;
                    final int fPage = r.page;
                    btnGo.setOnClickListener(v -> {
                        Intent i = new Intent(SearchActivity.this, PdfViewerActivity.class);
                        i.putExtra("pdfUri", fUri);
                        i.putExtra("startPage", fPage);
                        i.putExtra("fileType", "PDF");
                        startActivity(i);
                    });
                }
            }
        }.execute(query);
    }

    class DBHelper extends SQLiteOpenHelper {
        DBHelper() { super(SearchActivity.this, "goblith.db", null, 8); }
        @Override public void onCreate(SQLiteDatabase db) {}
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }
}
