package com.goblith.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_PDF = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnOpenPdf = findViewById(R.id.btnOpenPdf);
        btnOpenPdf.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/pdf");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_PDF);
        });

        Button btnNotes = findViewById(R.id.btnNotes);
        btnNotes.setOnClickListener(v -> {
            startActivity(new Intent(this, NotesActivity.class));
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PDF && resultCode == Activity.RESULT_OK && data != null) {
            Uri pdfUri = data.getData();
            Intent viewer = new Intent(this, PdfViewerActivity.class);
            viewer.putExtra("pdfUri", pdfUri.toString());
            startActivity(viewer);
        }
    }
}
