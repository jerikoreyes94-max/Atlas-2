package com.atlas.personalai;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AtlasCore {
    private AtlasCore() {}

    public static boolean isConfigured(Context c) {
        return SecureStore.has(c, "openrouter_key") ||
                SecureStore.has(c, "groq_key") ||
                SecureStore.has(c, "gemini_key");
    }

    public static String shortStatus(Context c) {
        if (SecureStore.has(c, "groq_key")) return "GROQ/WEB";
        if (SecureStore.has(c, "openrouter_key")) return "OPENROUTER FREE";
        if (SecureStore.has(c, "gemini_key")) return "GEMINI FREE";
        if (!AtlasStore.coreUrl(c).isEmpty()) return "CUSTOM CORE";
        return "NO CORE";
    }

    public static String status(Context c) {
        List<String> x = new ArrayList<>();
        if (SecureStore.has(c, "openrouter_key")) x.add("OpenRouter free configured");
        if (SecureStore.has(c, "groq_key")) x.add("Groq free configured");
        if (SecureStore.has(c, "gemini_key")) x.add("Gemini free configured");
        if (!AtlasStore.coreUrl(c).isEmpty()) x.add("custom Core URL configured");
        if (x.isEmpty()) return "AI core not configured yet.";
        return "AI core ready: " + String.join(" | ", x);
    }

    public static JSONObject ask(Context c, String text, String conversationId) throws Exception {
        List<String> errors = new ArrayList<>();
        boolean web = needsFreshWeb(text);

        if (web && SecureStore.has(c, "groq_key")) {
            try { return normalize(callGroq(c, text, conversationId), "groq-compound"); }
            catch (Exception e) { errors.add("Groq: " + shortError(e)); }
        }
        if (SecureStore.has(c, "openrouter_key")) {
            try { return normalize(callOpenRouter(c, text, conversationId), "openrouter-free"); }
            catch (Exception e) { errors.add("OpenRouter: " + shortError(e)); }
        }
        if (!web && SecureStore.has(c, "groq_key")) {
            try { return normalize(callGroq(c, text, conversationId), "groq-compound"); }
            catch (Exception e) { errors.add("Groq: " + shortError(e)); }
        }
        if (SecureStore.has(c, "gemini_key")) {
            try { return normalize(callGemini(c, text, conversationId), "gemini-gemma4"); }
            catch (Exception e) { errors.add("Gemini: " + shortError(e)); }
        }
        if (!AtlasStore.coreUrl(c).isEmpty()) {
            try { return callCustomCore(c, text, conversationId); }
            catch (Exception e) { errors.add("Custom: " + shortError(e)); }
        }
        if (errors.isEmpty()) throw new Exception("No AI core configured. Open Free AI Core Setup and add one free-tier key.");
        throw new Exception("All configured cores failed. " + String.join(" | ", errors));
    }

    private static boolean needsFreshWeb(String text) {
        String s = text == null ? "" : text.toLowerCase(Locale.US);
        String[] hints = {"today", "latest", "current", "news", "weather", "score", "price", "open now", "right now", "this week", "search the web", "look online", "find online"};
        for (String h : hints) if (s.contains(h)) return true;
        return false;
    }

    private static String callOpenRouter(Context c, String text, String conversationId) throws Exception {
        JSONObject body = openAiBody(c, text, conversationId, "openrouter/free");
        HttpURLConnection h = connection("https://openrouter.ai/api/v1/chat/completions");
        h.setRequestProperty("Authorization", "Bearer " + SecureStore.get(c, "openrouter_key"));
        h.setRequestProperty("HTTP-Referer", "https://github.com/jerikoreyes94-max/Atlas-2");
        h.setRequestProperty("X-Title", "Atlas Personal AI");
        String raw = post(h, body.toString());
        JSONObject o = new JSONObject(raw);
        return o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
    }

    private static String callGroq(Context c, String text, String conversationId) throws Exception {
        JSONObject body = openAiBody(c, text, conversationId, "groq/compound");
        HttpURLConnection h = connection("https://api.groq.com/openai/v1/chat/completions");
        h.setRequestProperty("Authorization", "Bearer " + SecureStore.get(c, "groq_key"));
        String raw = post(h, body.toString());
        JSONObject o = new JSONObject(raw);
        return o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
    }

    private static String callGemini(Context c, String text, String conversationId) throws Exception {
        String key = URLEncoder.encode(SecureStore.get(c, "gemini_key"), StandardCharsets.UTF_8.toString());
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemma-4-26b-a4b-it:generateContent?key=" + key;
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        part.put("text", systemPrompt() + "\n\nPHONE_CONTEXT:\n" + context(c, conversationId, text).toString() + "\n\nUSER REQUEST:\n" + text);
        parts.put(part); content.put("parts", parts); contents.put(content); body.put("contents", contents);
        JSONObject config = new JSONObject();
        config.put("temperature", 0.25);
        config.put("maxOutputTokens", 1600);
        body.put("generationConfig", config);
        HttpURLConnection h = connection(endpoint);
        String raw = post(h, body.toString());
        JSONObject o = new JSONObject(raw);
        return o.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).optString("text", "");
    }

    private static JSONObject callCustomCore(Context c, String text, String conversationId) throws Exception {
        String base = AtlasStore.coreUrl(c);
        String endpoint = base.endsWith("/") ? base + "api/chat" : base + "/api/chat";
        JSONObject body = new JSONObject();
        body.put("text", text);
        body.put("device", "Titan 2");
        body.put("source", "voice_or_text");
        body.put("conversationId", conversationId);
        body.put("context", context(c, conversationId, text));
        HttpURLConnection h = connection(endpoint);
        String raw = post(h, body.toString());
        JSONObject out = new JSONObject(raw);
        out.put("provider", "custom-core");
        if (!out.has("actions")) out.put("actions", new JSONArray());
        return out;
    }

    private static JSONObject openAiBody(Context c, String text, String conversationId, String model) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", 0.25);
        body.put("max_tokens", 1600);
        JSONArray messages = new JSONArray();
        messages.put(message("system", systemPrompt()));
        messages.put(message("user", "PHONE_CONTEXT:\n" + context(c, conversationId, text).toString() + "\n\nUSER REQUEST:\n" + text));
        body.put("messages", messages);
        return body;
    }

    private static JSONObject message(String role, String content) throws Exception {
        JSONObject m = new JSONObject(); m.put("role", role); m.put("content", content); return m;
    }

    private static JSONObject context(Context c, String conversationId, String request) {
        JSONObject x = new JSONObject();
        try {
            String q = request == null ? "" : request.toLowerCase(Locale.US);
            boolean needMessages = containsAny(q, "text", "sms", "message", "messages");
            boolean needNotifications = containsAny(q, "notification", "notifications", "missed", "what did i miss");
            boolean needCalendar = containsAny(q, "calendar", "schedule", "appointment", "meeting", "event");
            boolean needLocation = containsAny(q, "where am i", "near me", "nearby", "navigate", "directions", "weather", "location");
            x.put("conversationId", conversationId);
            x.put("timeMillis", System.currentTimeMillis());
            JSONObject device = DeviceContext.snapshot(c);
            if (!needLocation) device.remove("location");
            x.put("device", device);
            x.put("capabilities", CapabilityManager.snapshot(c));
            x.put("memories", tail(AtlasStore.memories(c), 40));
            x.put("recentConversation", tail(AtlasStore.conversation(c), 18));
            if (needNotifications) x.put("recentNotifications", tail(AtlasStore.notifications(c), 24));
            if (needCalendar) x.put("upcomingCalendar", CalendarTools.upcoming(c));
            if (needMessages) x.put("recentSms", SmsTools.recent(c));
        } catch (Exception ignored) {}
        return x;
    }

    private static boolean containsAny(String s, String... terms) {
        for (String t : terms) if (s.contains(t)) return true;
        return false;
    }

    private static JSONArray tail(JSONArray a, int max) {
        JSONArray out = new JSONArray();
        if (a == null) return out;
        int start = Math.max(0, a.length() - max);
        for (int i = start; i < a.length(); i++) out.put(a.opt(i));
        return out;
    }

    private static String systemPrompt() {
        return "You are Atlas, the user's personal Android AI agent. Think carefully, be concise in speech, use the supplied phone context, and never invent device state. " +
                "You can request phone actions, but the Android app enforces permissions and confirmation for risky actions. " +
                "Return ONLY one valid JSON object and no markdown. Schema: " +
                "{\"reply\":\"natural spoken answer\",\"actions\":[{\"type\":\"action_name\", ...}],\"memories\":[\"durable user facts/preferences worth remembering\"]}. " +
                "Use an empty actions array when no phone action is needed. Use memories only for durable facts, not temporary chatter. " +
                "Supported action types include: open_app, navigate, web_search, get_location, call_contact, dial_number, send_sms, read_sms, create_calendar_event, " +
                "media_play, media_pause, media_next, media_previous, volume_up, volume_down, flashlight_on, flashlight_off, open_camera, clipboard_copy, clipboard_read, " +
                "answer_call, end_call, ui_back, ui_home, ui_recents, ui_notifications, ui_quick_settings, ui_scroll_down, ui_scroll_up, ui_swipe_up, ui_swipe_down, " +
                "ui_swipe_left, ui_swipe_right, ui_click_text, ui_type_text, ui_screenshot, ui_lock_screen. " +
                "For multi-step tasks, return actions in execution order. Do not claim an action succeeded before the phone reports the result. " +
                "If an action requires missing details, ask the user instead of guessing. For ordinary questions, answer directly without an action.";
    }

    private static JSONObject normalize(String raw, String provider) throws Exception {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl >= 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3).trim();
        }
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        JSONObject out;
        if (a >= 0 && b > a) {
            try { out = new JSONObject(s.substring(a, b + 1)); }
            catch (Exception e) { out = new JSONObject().put("reply", s); }
        } else {
            out = new JSONObject().put("reply", s);
        }
        if (!out.has("reply")) out.put("reply", "Done.");
        if (!out.has("actions")) out.put("actions", new JSONArray());
        if (!out.has("memories")) out.put("memories", new JSONArray());
        out.put("provider", provider);
        return out;
    }

    private static HttpURLConnection connection(String endpoint) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(endpoint).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(60000);
        h.setRequestMethod("POST");
        h.setDoOutput(true);
        h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        h.setRequestProperty("Accept", "application/json");
        return h;
    }

    private static String post(HttpURLConnection h, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = h.getOutputStream()) { os.write(bytes); }
        int code = h.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? h.getInputStream() : h.getErrorStream();
        String body = readAll(in);
        h.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " " + trim(body, 240));
        return body;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        s = s.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static String shortError(Exception e) {
        return trim(e == null ? "unknown" : String.valueOf(e.getMessage()), 180);
    }
}
