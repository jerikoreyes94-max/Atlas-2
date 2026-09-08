package com.atlas.personalai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AtlasBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            context.startForegroundService(new Intent(context, AtlasForegroundService.class));
        } catch (Exception ignored) {}
    }
}
