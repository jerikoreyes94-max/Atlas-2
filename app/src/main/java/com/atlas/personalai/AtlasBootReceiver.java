package com.atlas.personalai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AtlasBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try { c.startForegroundService(new Intent(c, AtlasForegroundService.class)); }
        catch (Exception ignored) {}
    }
}
