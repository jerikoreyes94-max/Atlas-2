package com.atlas.personalai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;
import android.telephony.SmsManager;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SmsTools {
    private SmsTools() {}

    public static JSONArray recent(Context c) {
        JSONArray out = new JSONArray();
        if (c.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return out;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE},
                null, null, Telephony.Sms.DATE + " DESC"
            );
            int count = 0;
            while (cur != null && cur.moveToNext() && count < 12) {
                JSONObject o = new JSONObject();
                o.put("address", cur.getString(0));
                o.put("body", cur.getString(1));
                o.put("date", cur.getLong(2));
                o.put("type", cur.getInt(3));
                out.put(o);
                count++;
            }
        } catch (Exception ignored) {} finally { if (cur != null) cur.close(); }
        return out;
    }

    public static String summary(Context c) {
        if (c.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
            return "SMS access isn't granted yet. Tap Grant Phone Access.";
        JSONArray a = recent(c);
        if (a.length() == 0) return "I don't see any recent text messages.";
        StringBuilder s = new StringBuilder("Recent texts. ");
        for (int i = 0; i < Math.min(6, a.length()); i++) {
            JSONObject o = a.optJSONObject(i);
            if (i > 0) s.append(". ");
            s.append("From ").append(o.optString("address", "unknown")).append(": ")
                .append(shorten(o.optString("body", ""), 140));
        }
        return s.toString();
    }

    public static String send(Context c, String number, String message) {
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            return "SMS send access isn't granted yet.";
        if (number == null || number.trim().isEmpty()) return "That text is missing a phone number.";
        if (message == null || message.trim().isEmpty()) return "That text is missing a message.";
        try {
            SmsManager.getDefault().sendTextMessage(number, null, message, null, null);
            return "Text sent.";
        } catch (Exception e) { return "I couldn't send that text: " + e.getMessage(); }
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
