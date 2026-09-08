package com.atlas.personalai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;

public final class CapabilityManager {
    private CapabilityManager() {}

    public static JSONObject snapshot(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("voice", granted(c, Manifest.permission.RECORD_AUDIO));
            o.put("contacts", granted(c, Manifest.permission.READ_CONTACTS));
            o.put("calls", granted(c, Manifest.permission.CALL_PHONE));
            o.put("answerCalls", granted(c, Manifest.permission.ANSWER_PHONE_CALLS));
            o.put("calendarRead", granted(c, Manifest.permission.READ_CALENDAR));
            o.put("calendarWrite", granted(c, Manifest.permission.WRITE_CALENDAR));
            o.put("location", granted(c, Manifest.permission.ACCESS_COARSE_LOCATION) || granted(c, Manifest.permission.ACCESS_FINE_LOCATION));
            o.put("smsRead", granted(c, Manifest.permission.READ_SMS));
            o.put("smsSend", granted(c, Manifest.permission.SEND_SMS));
            o.put("camera", granted(c, Manifest.permission.CAMERA));
            o.put("accessibility", AtlasAccessibilityService.isReady() || AtlasAccessibilityService.isEnabled(c));
            o.put("notificationAccess", notificationAccess(c));
            o.put("callScreening", PhoneTools.callScreeningReady(c));
            o.put("appLaunch", true);
            o.put("navigation", true);
            o.put("mediaControl", true);
            o.put("supportedActions", new JSONArray()
                .put("open_app").put("navigate").put("web_search").put("open_camera")
                .put("get_location").put("call_contact").put("dial_number").put("send_sms")
                .put("read_sms").put("create_calendar_event").put("media_play").put("media_pause")
                .put("media_toggle").put("media_next").put("media_previous").put("volume_up").put("volume_down")
                .put("flashlight_on").put("flashlight_off").put("clipboard_copy").put("clipboard_read")
                .put("answer_call").put("end_call")
                .put("ui_back").put("ui_home").put("ui_recents").put("ui_notifications").put("ui_quick_settings")
                .put("ui_scroll_down").put("ui_scroll_up").put("ui_swipe_up").put("ui_swipe_down")
                .put("ui_swipe_left").put("ui_swipe_right").put("ui_click_text").put("ui_type_text")
                .put("ui_screenshot").put("ui_lock_screen"));
        } catch (Exception ignored) {}
        return o;
    }

    public static String summary(Context c) {
        JSONObject o = snapshot(c);
        return "Voice " + on(o, "voice") + ", contacts " + on(o, "contacts") + ", calling " + on(o, "calls") +
            ", SMS " + on(o, "smsRead") + "/" + on(o, "smsSend") + ", calendar " + on(o, "calendarRead") +
            ", location " + on(o, "location") + ", Full Access " + on(o, "accessibility") +
            ", notifications " + on(o, "notificationAccess") + ", call gate " + on(o, "callScreening") + ".";
    }

    private static boolean granted(Context c, String p) {
        return c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean notificationAccess(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            return s != null && s.toLowerCase().contains(c.getPackageName().toLowerCase());
        } catch (Exception e) { return false; }
    }

    private static String on(JSONObject o, String k) { return o.optBoolean(k) ? "ready" : "not granted"; }
}
