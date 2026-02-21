package com.goblith.app;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;

public class PdfViewerActivity extends AppCompatActivity {

    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFF1A1A2E);
        setContentView(layout);

        String uriString = getIntent().getStringExtra("pdfUri");
        Uri pdfUri = Uri.parse(uriString);

        try {
            fileDescriptor = getContentResolver().openFileDescriptor(pdfUri, "r");
            pdfRenderer = new PdfRenderer(fileDescriptor);

            for (int i = 0; i < pdfRenderer.getPageCount(); i++) {
                PdfRenderer.Page page = pdfRenderer.openPage(i);
                Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();

                ImageView imageView = new ImageView(this);
                imageView.setImageBitmap(bitmap);
                imageView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
                layout.addView(imageView);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) fileDescriptor.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
