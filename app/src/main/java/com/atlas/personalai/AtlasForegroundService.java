package com.atlas.personalai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AtlasForegroundService extends Service {
    private static final String CH = "atlas_core";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH, "Atlas Core", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CH)
            .setContentTitle("Atlas v5 is active")
            .setContentText("Voice + phone tools + agent actions")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build();
        startForeground(500, n);
    }

    @Override public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
}
