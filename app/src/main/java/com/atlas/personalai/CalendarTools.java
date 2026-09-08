package com.atlas.personalai;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;

public final class CalendarTools {
    private CalendarTools() {}

    public static JSONArray upcoming(Context c) {
        JSONArray out = new JSONArray();
        if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return out;
        Cursor cur = null;
        try {
            long now = System.currentTimeMillis();
            long end = now + 7L * 24L * 60L * 60L * 1000L;
            cur = c.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND, CalendarContract.Events.EVENT_LOCATION},
                CalendarContract.Events.DTSTART + ">=? AND " + CalendarContract.Events.DTSTART + "<=?",
                new String[]{String.valueOf(now), String.valueOf(end)},
                CalendarContract.Events.DTSTART + " ASC"
            );
            int count = 0;
            while (cur != null && cur.moveToNext() && count < 8) {
                JSONObject o = new JSONObject();
                o.put("title", cur.getString(0));
                o.put("startMillis", cur.getLong(1));
                o.put("endMillis", cur.getLong(2));
                o.put("location", cur.getString(3));
                out.put(o);
                count++;
            }
        } catch (Exception ignored) {
        } finally { if (cur != null) cur.close(); }
        return out;
    }

    public static String summary(Context c) {
        JSONArray a = upcoming(c);
        if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            return "Calendar access isn't granted yet. Tap Grant Phone Access.";
        if (a.length() == 0) return "I don't see anything on your calendar in the next seven days.";
        StringBuilder s = new StringBuilder("Your next events are. ");
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (int i=0;i<Math.min(5,a.length());i++) {
            JSONObject o=a.optJSONObject(i);
            if (i>0) s.append(". ");
            s.append(o.optString("title","Event")).append(" at ").append(df.format(new Date(o.optLong("startMillis"))));
        }
        return s.toString();
    }

    private static long writableCalendarId(Context c) {
        Cursor cur=null;
        try {
            cur=c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID},
                CalendarContract.Calendars.VISIBLE + "=1 AND " + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?",
                new String[]{String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)}, null);
            if(cur!=null && cur.moveToFirst()) return cur.getLong(0);
        } catch(Exception ignored) {} finally { if(cur!=null) cur.close(); }
        return -1;
    }

    public static String createEvent(Context c, JSONObject a) {
        if (c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)
            return "Calendar write access isn't granted yet. Tap Grant Phone Access and ask again.";
        try {
            long calId = writableCalendarId(c);
            if (calId < 0) return "I couldn't find a writable calendar on the phone.";
            long start = a.optLong("startMillis", 0);
            if (start <= 0) return "That calendar action is missing a start time.";
            long end = a.optLong("endMillis", start + 60L*60L*1000L);
            ContentValues v=new ContentValues();
            v.put(CalendarContract.Events.CALENDAR_ID, calId);
            v.put(CalendarContract.Events.TITLE, a.optString("title","Atlas event"));
            v.put(CalendarContract.Events.DTSTART, start);
            v.put(CalendarContract.Events.DTEND, end);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            String loc=a.optString("location","");
            if(!loc.isEmpty()) v.put(CalendarContract.Events.EVENT_LOCATION, loc);
            android.net.Uri u=c.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,v);
            return u != null ? "Done. I added it to your calendar." : "I couldn't add that calendar event.";
        } catch(Exception e) { return "Calendar error: " + e.getMessage(); }
    }
}
