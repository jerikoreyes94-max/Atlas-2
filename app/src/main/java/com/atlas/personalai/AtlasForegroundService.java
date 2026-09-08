package com.atlas.personalai;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AtlasForegroundService extends Service {
    private static final String CH = "atlas_core";

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH, "Atlas Core", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification n = new android.app.Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.atlas_small_icon)
            .setContentTitle("Atlas is active")
            .setContentText("Phone agent, memory, notifications, and assistant bridge ready")
            .setOngoing(true)
            .setContentIntent(pi)
            .build();
        startForeground(700, n);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
}
