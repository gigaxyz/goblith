package com.goblith.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.*;
import java.io.*;
import java.net.*;

public class GeminiService {
    // Gemini 1.5 Flash — ücretsiz tier
    private static final String API_KEY = "GEMINI_API_KEY_BURAYA";
    private static final String API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + API_KEY;

    public interface OnResultListener {
        void onResult(String result);
        void onError(String error);
    }

    // Sayfa metni özetle
    public static void summarizePage(String pageText, String bookName, int pageNum, OnResultListener listener) {
        String prompt = "Sen bir akademik asistansın. Aşağıdaki kitap sayfasını Türkçe olarak özetle. " +
            "Kitap: " + bookName + ", Sayfa: " + (pageNum + 1) + "\n\n" +
            "Sayfa içeriği:\n" + pageText + "\n\n" +
            "Özeti 3-5 cümle ile yaz. Sadece özeti yaz, başka bir şey ekleme.";
        callGemini(prompt, listener);
    }

    // Sayfa hakkında soru sor
    public static void askQuestion(String pageText, String question, String bookName, OnResultListener listener) {
        String prompt = "Aşağıdaki kitap sayfasını okuyarak soruyu Türkçe olarak cevapla.\n" +
            "Kitap: " + bookName + "\n\n" +
            "Sayfa içeriği:\n" + pageText + "\n\n" +
            "Soru: " + question + "\n\n" +
            "Sadece sayfadaki bilgilere dayanarak cevap ver.";
        callGemini(prompt, listener);
    }

    // Arşivdeki alıntıları analiz et
    public static void analyzeArchive(String archiveText, OnResultListener listener) {
        String prompt = "Aşağıdaki alıntı ve notları analiz et. Ortak temaları, önemli fikirleri ve " +
            "bağlantıları Türkçe olarak açıkla. 5-7 cümle ile yaz.\n\n" + archiveText;
        callGemini(prompt, listener);
    }

    // Not hakkında akıllı yorum
    public static void expandNote(String note, String context, OnResultListener listener) {
        String prompt = "Aşağıdaki not hakkında kısa bir açıklama ve yorum yap. " +
            "Bağlam: " + context + "\nNot: " + note + "\n\n" +
            "2-3 cümle ile Türkçe yorum yap.";
        callGemini(prompt, listener);
    }

    private static void callGemini(String prompt, OnResultListener listener) {
        new Thread(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", prompt);
                parts.put(part);
                content.put("parts", parts);
                contents.put(content);
                requestBody.put("contents", contents);

                // Güvenlik ve sıcaklık ayarları
                JSONObject genConfig = new JSONObject();
                genConfig.put("temperature", 0.3);
                genConfig.put("maxOutputTokens", 1024);
                requestBody.put("generationConfig", genConfig);

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                OutputStream os = conn.getOutputStream();
                os.write(requestBody.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                InputStream is = responseCode == 200 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                if (responseCode == 200) {
                    JSONObject response = new JSONObject(sb.toString());
                    String text = response.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0)
                        .getString("text");
                    new Handler(Looper.getMainLooper()).post(() -> listener.onResult(text.trim()));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        listener.onError("API hatası: " + responseCode + " - " + sb.toString()));
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> 
                    listener.onError("Bağlantı hatası: " + e.getMessage()));
            }
        }).start();
    }
}
