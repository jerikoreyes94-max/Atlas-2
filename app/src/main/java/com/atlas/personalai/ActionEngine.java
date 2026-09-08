package com.atlas.personalai;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.provider.MediaStore;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

public final class ActionEngine {
    public enum Policy { ALLOW, ASK, DENY }
    private ActionEngine() {}

    public static Policy policy(String type) {
        if (type == null) return Policy.DENY;
        switch (type) {
            case "open_app": case "navigate": case "web_search": case "open_camera": case "get_location":
            case "media_play": case "media_pause": case "media_toggle": case "media_next": case "media_previous":
            case "volume_up": case "volume_down": case "flashlight_on": case "flashlight_off": case "clipboard_copy":
            case "read_sms": case "dial_number":
            case "ui_back": case "ui_home": case "ui_recents": case "ui_notifications": case "ui_quick_settings":
            case "ui_scroll_down": case "ui_scroll_up": case "ui_swipe_up": case "ui_swipe_down": case "ui_swipe_left": case "ui_swipe_right":
                return Policy.ALLOW;
            case "call_contact": case "send_sms": case "create_calendar_event": case "clipboard_read":
            case "answer_call": case "end_call": case "ui_click_text": case "ui_type_text": case "ui_screenshot": case "ui_lock_screen":
                return Policy.ASK;
            default: return Policy.DENY;
        }
    }

    public static String confirmation(Context c, JSONObject a) {
        String type = a.optString("type", "");
        if ("call_contact".equals(type)) {
            if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                return "ERROR: Contacts access isn't granted yet.";
            ContactTools.ContactHit hit = ContactTools.find(c, a.optString("target", ""));
            if (hit == null) return "ERROR: I couldn't find that contact.";
            try { a.put("resolvedName", hit.name); a.put("resolvedNumber", hit.number); } catch (Exception ignored) {}
            return "Call " + hit.name + " at the number ending in " + ContactTools.last4(hit.number) + "? Say yes to confirm.";
        }
        if ("send_sms".equals(type)) {
            String target = a.optString("target", "");
            ContactTools.ContactHit hit = ContactTools.find(c, target);
            if (hit != null) {
                try { a.put("resolvedName", hit.name); a.put("resolvedNumber", hit.number); } catch (Exception ignored) {}
                return "Send this text to " + hit.name + "? Say yes to confirm.";
            }
            return "Send this text to " + target + "? Say yes to confirm.";
        }
        if ("create_calendar_event".equals(type))
            return "Add " + a.optString("title", "this event") + " to your calendar? Say yes to confirm.";
        if ("clipboard_read".equals(type)) return "Read the current clipboard aloud? Say yes to confirm.";
        if ("answer_call".equals(type)) return "Answer the ringing call? Say yes to confirm.";
        if ("end_call".equals(type)) return "End the current call? Say yes to confirm.";
        if ("ui_click_text".equals(type)) return "Tap " + a.optString("target", "that item") + " on screen? Say yes to confirm.";
        if ("ui_type_text".equals(type)) return "Type the requested text into the focused field? Say yes to confirm.";
        if ("ui_screenshot".equals(type)) return "Take a screenshot? Say yes to confirm.";
        if ("ui_lock_screen".equals(type)) return "Lock the screen? Say yes to confirm.";
        return "Run this action? Say yes to confirm.";
    }

    public static String execute(Context c, JSONObject a) {
        String type = a.optString("type", "");
        try {
            switch (type) {
                case "open_app": return openApp(c, a.optString("app", a.optString("target", "")));
                case "navigate": return navigate(c, a.optString("query", a.optString("target", "")));
                case "web_search": return webSearch(c, a.optString("query", a.optString("target", "")));
                case "open_camera": return openCamera(c);
                case "get_location": return DeviceContext.locationSummary(c);
                case "call_contact": return callContact(c, a);
                case "dial_number": return PhoneTools.dial(c, a.optString("number", a.optString("target", "")));
                case "send_sms": return sendSms(c, a);
                case "read_sms": return SmsTools.summary(c);
                case "create_calendar_event": return CalendarTools.createEvent(c, a);
                case "media_play": return media(c, "play");
                case "media_pause": return media(c, "pause");
                case "media_toggle": return media(c, "toggle");
                case "media_next": return media(c, "next");
                case "media_previous": return media(c, "previous");
                case "volume_up": return volume(c, true);
                case "volume_down": return volume(c, false);
                case "flashlight_on": return flashlight(c, true);
                case "flashlight_off": return flashlight(c, false);
                case "clipboard_copy": return clipboardCopy(c, a.optString("text", a.optString("target", "")));
                case "clipboard_read": return clipboardRead(c);
                case "answer_call": return PhoneTools.answer(c);
                case "end_call": return PhoneTools.end(c);
                case "ui_back": return AtlasAccessibilityService.run("back", "");
                case "ui_home": return AtlasAccessibilityService.run("home", "");
                case "ui_recents": return AtlasAccessibilityService.run("recents", "");
                case "ui_notifications": return AtlasAccessibilityService.run("notifications", "");
                case "ui_quick_settings": return AtlasAccessibilityService.run("quick_settings", "");
                case "ui_scroll_down": return AtlasAccessibilityService.run("scroll_down", "");
                case "ui_scroll_up": return AtlasAccessibilityService.run("scroll_up", "");
                case "ui_swipe_up": return AtlasAccessibilityService.run("swipe_up", "");
                case "ui_swipe_down": return AtlasAccessibilityService.run("swipe_down", "");
                case "ui_swipe_left": return AtlasAccessibilityService.run("swipe_left", "");
                case "ui_swipe_right": return AtlasAccessibilityService.run("swipe_right", "");
                case "ui_click_text": return AtlasAccessibilityService.run("click_text", a.optString("target", ""));
                case "ui_type_text": return AtlasAccessibilityService.run("type_text", a.optString("text", ""));
                case "ui_screenshot": return AtlasAccessibilityService.run("screenshot", "");
                case "ui_lock_screen": return AtlasAccessibilityService.run("lock_screen", "");
                default: return "That action isn't supported yet.";
            }
        } catch (Exception e) { return "Action error: " + e.getMessage(); }
    }

    private static String openApp(Context c, String name) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) return "Tell me which app to open.";
        if (name.equalsIgnoreCase("settings")) {
            Intent s = new Intent(android.provider.Settings.ACTION_SETTINGS);
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(s);
            return "Opening Settings.";
        }
        PackageManager pm = c.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
        ResolveInfo best = null;
        String low = name.toLowerCase(Locale.US);
        for (ResolveInfo r : list) {
            String label = r.loadLabel(pm).toString();
            String l = label.toLowerCase(Locale.US);
            if (l.equals(low)) { best = r; break; }
            if (best == null && l.contains(low)) best = r;
        }
        if (best == null) return "I couldn't find an installed app called " + name + ".";
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch == null) return "I found the app but couldn't launch it.";
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(launch);
        return "Opening " + best.loadLabel(pm) + ".";
    }

    private static String navigate(Context c, String q) {
        if (q == null || q.trim().isEmpty()) return "Tell me where you want to go.";
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        return "Opening navigation for " + q + ".";
    }

    private static String webSearch(Context c, String q) {
        if (q == null || q.trim().isEmpty()) return "Tell me what to search for.";
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        return "Searching the web for " + q + ".";
    }

    private static String openCamera(Context c) {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); return "Opening the camera."; }
        catch (Exception e) { return "I couldn't open the camera."; }
    }

    private static String callContact(Context c, JSONObject a) {
        if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            return "Phone call access isn't granted yet.";
        String num = a.optString("resolvedNumber", "");
        String name = a.optString("resolvedName", a.optString("target", "contact"));
        if (num.isEmpty()) {
            ContactTools.ContactHit hit = ContactTools.find(c, a.optString("target", ""));
            if (hit == null) return "I couldn't find that contact.";
            num = hit.number; name = hit.name;
        }
        Intent i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(num)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        return "Calling " + name + ".";
    }

    private static String sendSms(Context c, JSONObject a) {
        String number = a.optString("resolvedNumber", "");
        if (number.isEmpty()) {
            ContactTools.ContactHit hit = ContactTools.find(c, a.optString("target", ""));
            if (hit != null) number = hit.number;
            else number = a.optString("target", "");
        }
        return SmsTools.send(c, number, a.optString("message", a.optString("text", "")));
    }

    private static String media(Context c, String cmd) {
        try {
            MediaSessionManager msm = (MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName listener = new ComponentName(c, AtlasNotificationListener.class);
            List<MediaController> controllers = msm.getActiveSessions(listener);
            if (controllers == null || controllers.isEmpty()) return "I don't see an active media session. Enable Atlas Notification Access.";
            MediaController mc = controllers.get(0);
            MediaController.TransportControls t = mc.getTransportControls();
            if ("play".equals(cmd)) t.play();
            else if ("pause".equals(cmd)) t.pause();
            else if ("next".equals(cmd)) t.skipToNext();
            else if ("previous".equals(cmd)) t.skipToPrevious();
            else {
                PlaybackState ps = mc.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) t.pause(); else t.play();
            }
            return "Media command sent.";
        } catch (SecurityException e) { return "Atlas needs Notification Access for media control."; }
        catch (Exception e) { return "I couldn't control the current media session."; }
    }

    private static String volume(Context c, boolean up) {
        AudioManager am = (AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        return up ? "Volume up." : "Volume down.";
    }

    private static String flashlight(Context c, boolean on) {
        if (c.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return "Camera access is required for flashlight control.";
        try {
            CameraManager cm = (CameraManager)c.getSystemService(Context.CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash) && (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) {
                    cm.setTorchMode(id, on);
                    return on ? "Flashlight on." : "Flashlight off.";
                }
            }
            return "I couldn't find a flashlight on this phone.";
        } catch (Exception e) { return "I couldn't control the flashlight."; }
    }

    private static String clipboardCopy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Atlas", text == null ? "" : text));
        return "Copied to clipboard.";
    }

    private static String clipboardRead(Context c) {
        ClipboardManager cm = (ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (!cm.hasPrimaryClip() || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return "The clipboard is empty.";
        CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(c);
        return "Clipboard says: " + (t == null ? "" : t.toString());
    }
}
