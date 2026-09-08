package com.atlas.personalai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Deque;

public class AtlasAccessibilityService extends AccessibilityService {
    private static volatile AtlasAccessibilityService instance;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isReady() { return instance != null; }

    public static boolean isEnabled(Context c) {
        try {
            String enabled = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.toLowerCase().contains(c.getPackageName().toLowerCase() + "/");
        } catch (Exception e) { return false; }
    }

    public static JSONObject screenSnapshot() {
        JSONObject o = new JSONObject();
        AtlasAccessibilityService s = instance;
        try {
            if (s == null) { o.put("available", false); return o; }
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) { o.put("available", false); return o; }
            o.put("available", true);
            o.put("package", root.getPackageName() == null ? "" : root.getPackageName().toString());
            o.put("visibleText", s.collectText(root, 3500));
        } catch (Exception e) {
            try { o.put("available", false); } catch (Exception ignored) {}
        }
        return o;
    }

    public static String run(String action, String arg) {
        AtlasAccessibilityService s = instance;
        if (s == null) return "Atlas Full Access is not active. Enable Atlas in Accessibility settings.";
        switch (action) {
            case "back": return s.global(GLOBAL_ACTION_BACK, "Back.");
            case "home": return s.global(GLOBAL_ACTION_HOME, "Home.");
            case "recents": return s.global(GLOBAL_ACTION_RECENTS, "Opening recent apps.");
            case "notifications": return s.global(GLOBAL_ACTION_NOTIFICATIONS, "Opening notifications.");
            case "quick_settings": return s.global(GLOBAL_ACTION_QUICK_SETTINGS, "Opening quick settings.");
            case "screenshot": return s.global(GLOBAL_ACTION_TAKE_SCREENSHOT, "Taking a screenshot.");
            case "lock_screen": return s.global(GLOBAL_ACTION_LOCK_SCREEN, "Locking the screen.");
            case "scroll_down": return s.scroll(true) ? "Scrolled down." : "I couldn't find anything scrollable.";
            case "scroll_up": return s.scroll(false) ? "Scrolled up." : "I couldn't find anything scrollable.";
            case "swipe_up": return s.swipe(0.5f, 0.78f, 0.5f, 0.22f) ? "Swiped up." : "I couldn't swipe.";
            case "swipe_down": return s.swipe(0.5f, 0.22f, 0.5f, 0.78f) ? "Swiped down." : "I couldn't swipe.";
            case "swipe_left": return s.swipe(0.82f, 0.5f, 0.18f, 0.5f) ? "Swiped left." : "I couldn't swipe.";
            case "swipe_right": return s.swipe(0.18f, 0.5f, 0.82f, 0.5f) ? "Swiped right." : "I couldn't swipe.";
            case "click_text": return s.clickText(arg) ? "Tapped " + arg + "." : "I couldn't find " + arg + " on screen.";
            case "type_text": return s.typeText(arg) ? "Typed it." : "I couldn't find an editable field.";
            default: return "That Full Access action isn't supported.";
        }
    }

    private String global(int action, String ok) {
        return performGlobalAction(action) ? ok : "I couldn't perform that system action.";
    }

    private boolean scroll(boolean forward) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int action = forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (n.isScrollable() && n.performAction(action)) return true;
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
        }
        return false;
    }

    private boolean swipe(float sx, float sy, float ex, float ey) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            Path p = new Path();
            p.moveTo(dm.widthPixels * sx, dm.heightPixels * sy);
            p.lineTo(dm.widthPixels * ex, dm.heightPixels * ey);
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(p, 0, 320);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
            return dispatchGesture(gesture, null, null);
        } catch (Exception e) { return false; }
    }

    private boolean clickText(String target) {
        if (TextUtils.isEmpty(target)) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        String wanted = target.trim().toLowerCase();
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            String text = n.getText() == null ? "" : n.getText().toString();
            String desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
            if (text.toLowerCase().contains(wanted) || desc.toLowerCase().contains(wanted)) {
                AccessibilityNodeInfo click = n;
                while (click != null) {
                    if (click.isClickable() && click.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                    click = click.getParent();
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
        }
        return false;
    }

    private boolean typeText(String value) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null || !target.isEditable()) {
            Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
            q.add(root);
            target = null;
            while (!q.isEmpty()) {
                AccessibilityNodeInfo n = q.removeFirst();
                if (n.isEditable() && n.isFocused()) { target = n; break; }
                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo child = n.getChild(i);
                    if (child != null) q.addLast(child);
                }
            }
        }
        if (target == null || !target.isEditable()) return false;
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value == null ? "" : value);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private String collectText(AccessibilityNodeInfo root, int max) {
        StringBuilder sb = new StringBuilder();
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty() && sb.length() < max) {
            AccessibilityNodeInfo n = q.removeFirst();
            CharSequence t = n.getText();
            CharSequence d = n.getContentDescription();
            if (t != null && t.length() > 0) append(sb, t.toString());
            else if (d != null && d.length() > 0) append(sb, d.toString());
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
        }
        return sb.length() > max ? sb.substring(0, max) : sb.toString();
    }

    private void append(StringBuilder sb, String s) {
        if (s == null || s.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append(" | ");
        sb.append(s.replace('\n', ' ').trim());
    }
}
