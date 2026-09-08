package com.atlas.personalai;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AtlasStore {
    private static final String PREF = "atlas_v4";
    private AtlasStore() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String coreUrl(Context c) { return p(c).getString("core_url", "").trim(); }
    public static void setCoreUrl(Context c, String s) { p(c).edit().putString("core_url", s == null ? "" : s.trim()).apply(); }
    public static String voiceName(Context c) { return p(c).getString("voice_name", ""); }
    public static void setVoiceName(Context c, String s) { p(c).edit().putString("voice_name", s == null ? "" : s).apply(); }
    public static float speechRate(Context c) { return p(c).getFloat("speech_rate", 0.93f); }
    public static void setSpeechRate(Context c, float v) { p(c).edit().putFloat("speech_rate", v).apply(); }
    public static float pitch(Context c) { return p(c).getFloat("pitch", 0.97f); }
    public static void setPitch(Context c, float v) { p(c).edit().putFloat("pitch", v).apply(); }
    public static boolean conversationMode(Context c) { return p(c).getBoolean("conversation_mode", true); }
    public static void setConversationMode(Context c, boolean v) { p(c).edit().putBoolean("conversation_mode", v).apply(); }

    public static JSONArray memories(Context c) {
        try { return new JSONArray(p(c).getString("memories", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static void remember(Context c, String text) {
        JSONArray a = memories(c);
        a.put(text);
        while (a.length() > 100) a.remove(0);
        p(c).edit().putString("memories", a.toString()).apply();
    }

    public static JSONArray notifications(Context c) {
        try { return new JSONArray(p(c).getString("notifications", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static void addNotification(Context c, String text) {
        JSONArray a = notifications(c);
        a.put(text);
        while (a.length() > 150) a.remove(0);
        p(c).edit().putString("notifications", a.toString()).apply();
    }

    public static JSONArray conversation(Context c) {
        try { return new JSONArray(p(c).getString("conversation", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static void addTurn(Context c, String role, String text) {
        try {
            JSONArray a = conversation(c);
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("text", text);
            o.put("time", System.currentTimeMillis());
            a.put(o);
            while (a.length() > 40) a.remove(0);
            p(c).edit().putString("conversation", a.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void clearConversation(Context c) {
        p(c).edit().putString("conversation", "[]").apply();
    }
}
