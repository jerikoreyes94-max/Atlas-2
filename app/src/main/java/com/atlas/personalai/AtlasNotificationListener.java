package com.atlas.personalai;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class AtlasNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            Notification n = sbn.getNotification();
            CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE, "");
            CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT, "");
            AtlasStore.addNotification(this,
                System.currentTimeMillis() + " | " + sbn.getPackageName() + " | " + title + " | " + text);
        } catch (Exception ignored) {}
    }
}
