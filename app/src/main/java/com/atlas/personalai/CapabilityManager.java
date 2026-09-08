package com.atlas.personalai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import org.json.JSONObject;

public final class CapabilityManager {
    private CapabilityManager() {}

    public static JSONObject snapshot(Context c) {
        JSONObject o=new JSONObject();
        try {
            o.put("voice", c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED);
            o.put("contacts", c.checkSelfPermission(Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED);
            o.put("calls", c.checkSelfPermission(Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED);
            o.put("calendarRead", c.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED);
            o.put("calendarWrite", c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED);
            o.put("location", c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED);
            o.put("appLaunch", true);
            o.put("navigation", true);
            o.put("notifications", true);
            o.put("mediaControl", true);
            o.put("supportedActions", new org.json.JSONArray()
                .put("open_app").put("navigate").put("get_location")
                .put("call_contact").put("create_calendar_event")
                .put("media_play").put("media_pause").put("media_toggle").put("media_next").put("media_previous"));
        } catch(Exception ignored) {}
        return o;
    }

    public static String summary(Context c) {
        JSONObject o=snapshot(c);
        return "Voice " + on(o,"voice") + ", contacts " + on(o,"contacts") + ", calling " + on(o,"calls") +
            ", calendar " + on(o,"calendarRead") + ", location " + on(o,"location") +
            ". App launch, navigation, notifications, and media tools are built in.";
    }

    private static String on(JSONObject o,String k){return o.optBoolean(k)?"ready":"not granted";}
}
