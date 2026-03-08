package com.goblith.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            boolean autoSync = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("auto_sync", true);
            if (!autoSync) return;
            SyncManager sync = new SyncManager(context, GoblithApp.getDb());
            if (sync.isLoggedIn()) {
                new Thread(() -> {
                    try { sync.syncAll(); } catch (Exception ignored) {}
                }).start();
            }
        } catch (Exception ignored) {}
    }
}
