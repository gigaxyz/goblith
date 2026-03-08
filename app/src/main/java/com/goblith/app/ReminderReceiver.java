package com.goblith.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "goblith_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            boolean notifEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("notif_reading", true);
            if (!notifEnabled) return;

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Okuma Hatırlatıcısı", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Günlük okuma hatırlatmaları");
                nm.createNotificationChannel(ch);
            }

            Intent openIntent = new Intent(context, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // SQLite'dan bugünkü okuma istatistiğini al
            int pagesRead = 0;
            try {
                android.database.sqlite.SQLiteDatabase db = GoblithApp.getDb();
                android.database.Cursor c = db.rawQuery(
                    "SELECT COALESCE(SUM(last_page),0) FROM library WHERE last_opened LIKE ?",
                    new String[]{new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(new java.util.Date()) + "%"});
                if (c.moveToFirst()) pagesRead = c.getInt(0);
                c.close();
            } catch (Exception ignored) {}

            String msg = pagesRead > 0
                ? "Bugün " + pagesRead + " sayfa okudun! Devam et 🔥"
                : "Bugün henüz okuma yapmadın. Kitabını aç! 📖";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("Goblith — Okuma Zamanı")
                .setContentText(msg)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            nm.notify(1001, builder.build());
        } catch (Exception ignored) {}
    }
}
