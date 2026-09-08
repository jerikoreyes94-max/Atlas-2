package com.atlas.personalai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int MIC = 10, ACCESS = 12;
    private TextView status, heard, reply, voiceInfo, accessInfo;
    private EditText core, commandInput;
    private Button conversationButton;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String conversationId = UUID.randomUUID().toString();
    private final List<Voice> voiceChoices = new ArrayList<>();
    private int voiceIndex = -1;
    private boolean conversationMode, resumeAfterSpeech, destroyed;
    private JSONObject pendingAction;
    private JSONArray queuedActions = new JSONArray();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        conversationMode = AtlasStore.conversationMode(this);
        buildUi();
        tts = new TextToSpeech(this, this);
        try { startForegroundService(new Intent(this, AtlasForegroundService.class)); } catch (Exception ignored) {}
        if (getIntent().getBooleanExtra("atlas_auto_listen", false)) handler.postDelayed(this::listen, 900);
        refreshAccess();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("atlas_auto_listen", false)) handler.postDelayed(this::listen, 350);
    }

    private TextView tv(String s, int sp) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setPadding(20, 12, 20, 12);
        return v;
    }

    private Button button(String s) { Button b = new Button(this); b.setText(s); return b; }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.atlas_icon);
        icon.setAdjustViewBounds(true);
        root.addView(icon, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 220));

        TextView title = tv("ATLAS 1.1", 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        status = tv("STARTING...", 16); root.addView(status);
        accessInfo = tv("ACCESS: checking...", 13); root.addView(accessInfo);

        Button grant = button("GRANT PHONE ACCESS");
        grant.setOnClickListener(v -> requestPhoneAccess());
        root.addView(grant);

        LinearLayout accessRow = new LinearLayout(this);
        accessRow.setOrientation(LinearLayout.HORIZONTAL);
        Button full = button("FULL ACCESS");
        full.setOnClickListener(v -> openAccessibility());
        accessRow.addView(full, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button notif = button("NOTIFICATIONS");
        notif.setOnClickListener(v -> openNotificationAccess());
        accessRow.addView(notif, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(accessRow);

        LinearLayout callRow = new LinearLayout(this);
        callRow.setOrientation(LinearLayout.HORIZONTAL);
        Button gate = button("CALL GATE");
        gate.setOnClickListener(v -> { PhoneTools.requestCallScreening(this); handler.postDelayed(this::refreshAccess, 1000); });
        callRow.addView(gate, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button battery = button("UNRESTRICT BATTERY");
        battery.setOnClickListener(v -> openBatteryUnrestricted());
        callRow.addView(battery, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(callRow);

        conversationButton = button("");
        updateConversationButton();
        conversationButton.setOnClickListener(v -> {
            conversationMode = !conversationMode;
            AtlasStore.setConversationMode(this, conversationMode);
            updateConversationButton();
            toast(conversationMode ? "Conversation mode on" : "Conversation mode off");
        });
        root.addView(conversationButton);

        Button talk = button("TALK TO ATLAS");
        talk.setOnClickListener(v -> listen());
        root.addView(talk);

        Button interrupt = button("INTERRUPT / LISTEN NOW");
        interrupt.setOnClickListener(v -> { stopSpeech(); listen(); });
        root.addView(interrupt);

        commandInput = new EditText(this);
        commandInput.setHint("Type a command to Atlas");
        root.addView(commandInput);
        Button send = button("SEND COMMAND");
        send.setOnClickListener(v -> {
            String t = commandInput.getText().toString().trim();
            if (!t.isEmpty()) { commandInput.setText(""); process(t); }
        });
        root.addView(send);

        voiceInfo = tv("VOICE: loading...", 13); root.addView(voiceInfo);
        LinearLayout vr = new LinearLayout(this); vr.setOrientation(LinearLayout.HORIZONTAL);
        Button next = button("NEXT VOICE"); next.setOnClickListener(v -> nextVoice());
        vr.addView(next, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button slower = button("SLOWER"); slower.setOnClickListener(v -> adjustRate(-0.05f));
        vr.addView(slower, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button faster = button("FASTER"); faster.setOnClickListener(v -> adjustRate(0.05f));
        vr.addView(faster, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(vr);

        core = new EditText(this);
        core.setHint("Custom Atlas Core URL (optional fallback)");
        core.setSingleLine(true);
        core.setText(AtlasStore.coreUrl(this));
        root.addView(core);
        Button save = button("SAVE CORE URL");
        save.setOnClickListener(v -> { AtlasStore.setCoreUrl(this, core.getText().toString()); toast("Core URL saved"); });
        root.addView(save);

        Button aiCore = button("FREE AI CORE SETUP");
        aiCore.setOnClickListener(v -> startActivity(new Intent(this, CoreSettingsActivity.class)));
        root.addView(aiCore);

        TextView coreState = tv("AI CORE: " + AtlasCore.status(this), 13);
        root.addView(coreState);

        Button tools = button("WHAT CAN ATLAS ACCESS?");
        tools.setOnClickListener(v -> speakReply(CapabilityManager.summary(this), false));
        root.addView(tools);

        heard = tv("You: —", 16); root.addView(heard);
        reply = tv("Atlas: —", 17); reply.setTypeface(Typeface.DEFAULT_BOLD); root.addView(reply);
        TextView hint = tv("Commands: open apps • call/text contacts • navigate • web search • calendar • texts • media • volume • flashlight • camera • clipboard • answer/hang up • Back/Home/Recents • notifications • quick settings • scroll/swipe • tap/type on screen • screenshot • lock screen", 14);
        root.addView(hint);

        ScrollView sc = new ScrollView(this);
        sc.addView(root);
        setContentView(sc);
    }

    private void requestPhoneAccess() {
        ArrayList<String> p = new ArrayList<>();
        addIfMissing(p, Manifest.permission.RECORD_AUDIO);
        addIfMissing(p, Manifest.permission.READ_CONTACTS);
        addIfMissing(p, Manifest.permission.CALL_PHONE);
        addIfMissing(p, Manifest.permission.READ_PHONE_STATE);
        addIfMissing(p, Manifest.permission.ANSWER_PHONE_CALLS);
        addIfMissing(p, Manifest.permission.READ_CALENDAR);
        addIfMissing(p, Manifest.permission.WRITE_CALENDAR);
        addIfMissing(p, Manifest.permission.ACCESS_COARSE_LOCATION);
        addIfMissing(p, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(p, Manifest.permission.READ_SMS);
        addIfMissing(p, Manifest.permission.SEND_SMS);
        addIfMissing(p, Manifest.permission.CAMERA);
        if (android.os.Build.VERSION.SDK_INT >= 33) addIfMissing(p, Manifest.permission.POST_NOTIFICATIONS);
        if (p.isEmpty()) { toast("Phone access already granted"); refreshAccess(); return; }
        requestPermissions(p.toArray(new String[0]), ACCESS);
    }

    private void addIfMissing(List<String> list, String perm) {
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) list.add(perm);
    }

    private void openAccessibility() {
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (Exception e) { toast("Open Accessibility settings and enable Atlas Full Access."); }
    }

    private void openNotificationAccess() {
        try { startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")); }
        catch (Exception e) { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {} }
    }

    private void openBatteryUnrestricted() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    private void refreshAccess() {
        if (accessInfo != null) accessInfo.setText("ACCESS: " + CapabilityManager.summary(this));
    }

    private void updateConversationButton() {
        if (conversationButton != null) conversationButton.setText("CONVERSATION MODE: " + (conversationMode ? "ON" : "OFF"));
    }

    private void listen() {
        resumeAfterSpeech = false;
        stopSpeech();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { speakReply("Speech recognition isn't available on this phone.", false); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC); return;
        }
        if (sr != null) {
            try { sr.cancel(); } catch (Exception ignored) {}
            try { sr.destroy(); } catch (Exception ignored) {}
        }
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p) { status.setText("LISTENING"); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float r) {}
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { status.setText("THINKING"); }
            public void onError(int e) {
                status.setText("IDLE — recognition error " + e);
                if (conversationMode && e != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) handler.postDelayed(MainActivity.this::listen, 1200);
            }
            public void onResults(Bundle r) {
                ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (l != null && !l.isEmpty()) process(l.get(0)); else status.setText("IDLE");
            }
            public void onPartialResults(Bundle p) {}
            public void onEvent(int t, Bundle p) {}
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        sr.startListening(i);
    }

    private void process(String text) {
        heard.setText("You: " + text);
        AtlasStore.addTurn(this, "user", text);
        String low = text.toLowerCase(Locale.US).trim();

        if (pendingAction != null) {
            if (isYes(low)) {
                JSONObject a = pendingAction; pendingAction = null;
                String result = ActionEngine.execute(this, a);
                if (queuedActions.length() > 0) runQueuedActions(result); else speakReply(result, conversationMode);
                return;
            }
            if (isNo(low)) {
                pendingAction = null; queuedActions = new JSONArray(); speakReply("Cancelled.", conversationMode); return;
            }
        }

        if (low.startsWith("remember ")) {
            String m = text.substring(9).trim(); AtlasStore.remember(this, m); speakReply("Got it. I'll remember " + m, conversationMode); return;
        }
        if (low.equals("what do you remember") || low.equals("what do you remember?")) { speakReply(memorySummary(), conversationMode); return; }
        if (low.equals("what did i miss") || low.equals("what did i miss?")) { speakReply(notificationSummary(), conversationMode); return; }
        if (low.equals("core status") || low.equals("core status?")) { speakReply(AtlasCore.status(this), conversationMode); return; }
        if (low.equals("setup core") || low.equals("core setup")) { startActivity(new Intent(this, CoreSettingsActivity.class)); speakReply("Opening free AI core setup.", false); return; }
        if (low.contains("battery")) { speakReply(DeviceContext.batterySummary(this), conversationMode); return; }
        if (low.contains("calendar") && (low.contains("what") || low.contains("next") || low.contains("schedule"))) { speakReply(CalendarTools.summary(this), conversationMode); return; }
        if (low.equals("read my texts") || low.equals("recent texts") || low.equals("what texts did i get")) { routeAction(action("read_sms", null, null), null); return; }
        if (low.equals("where am i") || low.equals("where am i?")) { routeAction(action("get_location", null, null), null); return; }
        if (low.startsWith("open ")) { routeAction(action("open_app", "app", text.substring(5).trim()), null); return; }
        if (low.startsWith("search for ")) { routeAction(action("web_search", "query", text.substring(11).trim()), null); return; }
        if (low.startsWith("search web for ")) { routeAction(action("web_search", "query", text.substring(15).trim()), null); return; }
        if (low.startsWith("navigate to ")) { routeAction(action("navigate", "query", text.substring(12).trim()), null); return; }
        if (low.startsWith("take me to ")) { routeAction(action("navigate", "query", text.substring(11).trim()), null); return; }
        if (low.startsWith("call ")) { routeAction(action("call_contact", "target", text.substring(5).trim()), null); return; }
        if (low.startsWith("dial ")) { routeAction(action("dial_number", "number", text.substring(5).trim()), null); return; }
        if (low.startsWith("text ")) {
            String rest = text.substring(5).trim(); int split = rest.indexOf(' ');
            if (split > 0) {
                JSONObject a = action("send_sms", "target", rest.substring(0, split));
                try { a.put("message", rest.substring(split + 1).trim()); } catch (Exception ignored) {}
                routeAction(a, null); return;
            }
        }
        if (low.equals("pause music") || low.equals("pause")) { routeAction(action("media_pause", null, null), null); return; }
        if (low.equals("play music") || low.equals("resume music") || low.equals("play")) { routeAction(action("media_play", null, null), null); return; }
        if (low.equals("next song") || low.equals("skip song") || low.equals("next")) { routeAction(action("media_next", null, null), null); return; }
        if (low.equals("previous song") || low.equals("previous")) { routeAction(action("media_previous", null, null), null); return; }
        if (low.equals("volume up")) { routeAction(action("volume_up", null, null), null); return; }
        if (low.equals("volume down")) { routeAction(action("volume_down", null, null), null); return; }
        if (low.equals("flashlight on") || low.equals("turn on flashlight")) { routeAction(action("flashlight_on", null, null), null); return; }
        if (low.equals("flashlight off") || low.equals("turn off flashlight")) { routeAction(action("flashlight_off", null, null), null); return; }
        if (low.equals("open camera") || low.equals("camera")) { routeAction(action("open_camera", null, null), null); return; }
        if (low.startsWith("copy ")) { routeAction(action("clipboard_copy", "text", text.substring(5).trim()), null); return; }
        if (low.equals("read clipboard") || low.equals("what's on my clipboard")) { routeAction(action("clipboard_read", null, null), null); return; }
        if (low.equals("answer call") || low.equals("answer the call")) { routeAction(action("answer_call", null, null), null); return; }
        if (low.equals("hang up") || low.equals("end call") || low.equals("end the call")) { routeAction(action("end_call", null, null), null); return; }
        if (low.equals("go back") || low.equals("back")) { routeAction(action("ui_back", null, null), null); return; }
        if (low.equals("go home") || low.equals("home")) { routeAction(action("ui_home", null, null), null); return; }
        if (low.equals("recent apps") || low.equals("recents")) { routeAction(action("ui_recents", null, null), null); return; }
        if (low.equals("notifications") || low.equals("open notifications")) { routeAction(action("ui_notifications", null, null), null); return; }
        if (low.equals("quick settings") || low.equals("open quick settings")) { routeAction(action("ui_quick_settings", null, null), null); return; }
        if (low.equals("scroll down")) { routeAction(action("ui_scroll_down", null, null), null); return; }
        if (low.equals("scroll up")) { routeAction(action("ui_scroll_up", null, null), null); return; }
        if (low.equals("swipe up")) { routeAction(action("ui_swipe_up", null, null), null); return; }
        if (low.equals("swipe down")) { routeAction(action("ui_swipe_down", null, null), null); return; }
        if (low.equals("swipe left")) { routeAction(action("ui_swipe_left", null, null), null); return; }
        if (low.equals("swipe right")) { routeAction(action("ui_swipe_right", null, null), null); return; }
        if (low.startsWith("tap ")) { routeAction(action("ui_click_text", "target", text.substring(4).trim()), null); return; }
        if (low.startsWith("type ")) { routeAction(action("ui_type_text", "text", text.substring(5).trim()), null); return; }
        if (low.equals("take screenshot") || low.equals("screenshot")) { routeAction(action("ui_screenshot", null, null), null); return; }
        if (low.equals("lock screen") || low.equals("lock the screen")) { routeAction(action("ui_lock_screen", null, null), null); return; }
        if (low.equals("what can you do") || low.equals("what can you access") || low.equals("capabilities")) { speakReply(CapabilityManager.summary(this), conversationMode); return; }

        if (!AtlasCore.isConfigured(this) && AtlasStore.coreUrl(this).isEmpty()) {
            speakReply("Atlas 1.1 is ready for a free AI core. Tap Free AI Core Setup and add any OpenRouter, Groq, or Gemini free-tier API key.", conversationMode);
            return;
        }
        status.setText("THINKING - " + AtlasCore.shortStatus(this));
        new Thread(() -> callCore("", text)).start();
    }

    private boolean isYes(String s) { return s.equals("yes") || s.equals("confirm") || s.equals("do it") || s.equals("yes do it") || s.equals("go ahead"); }
    private boolean isNo(String s) { return s.equals("no") || s.equals("cancel") || s.equals("never mind") || s.equals("stop"); }

    private JSONObject action(String type, String key, String value) {
        JSONObject a = new JSONObject();
        try { a.put("type", type); if (key != null) a.put(key, value); } catch (Exception ignored) {}
        return a;
    }

    private void routeAction(JSONObject a, String coreReply) {
        queuedActions = new JSONArray();
        runSingleAction(a, coreReply == null ? "" : coreReply, conversationMode);
    }

    private void runSingleAction(JSONObject a, String prefix, boolean cont) {
        String type = a.optString("type", "");
        ActionEngine.Policy p = ActionEngine.policy(type);
        if (p == ActionEngine.Policy.DENY) {
            speakReply(join(prefix, "That action is blocked or unsupported."), cont); return;
        }
        if (p == ActionEngine.Policy.ASK) {
            String c = ActionEngine.confirmation(this, a);
            if (c.startsWith("ERROR:")) { speakReply(c.substring(6).trim(), cont); return; }
            pendingAction = a;
            speakReply(join(prefix, c), false);
            return;
        }
        String result = ActionEngine.execute(this, a);
        speakReply(join(prefix, result), cont);
    }

    private void runQueuedActions(String prefix) {
        StringBuilder results = new StringBuilder(prefix == null ? "" : prefix);
        while (queuedActions.length() > 0) {
            JSONObject a = queuedActions.optJSONObject(0);
            JSONArray rest = new JSONArray();
            for (int i = 1; i < queuedActions.length(); i++) rest.put(queuedActions.optJSONObject(i));
            queuedActions = rest;
            if (a == null) continue;
            ActionEngine.Policy p = ActionEngine.policy(a.optString("type", ""));
            if (p == ActionEngine.Policy.DENY) { appendResult(results, "Blocked unsupported action."); continue; }
            if (p == ActionEngine.Policy.ASK) {
                String c = ActionEngine.confirmation(this, a);
                if (c.startsWith("ERROR:")) { appendResult(results, c.substring(6).trim()); continue; }
                pendingAction = a;
                speakReply(join(results.toString(), c), false);
                return;
            }
            appendResult(results, ActionEngine.execute(this, a));
        }
        speakReply(results.length() == 0 ? "Done." : results.toString(), conversationMode);
    }

    private void appendResult(StringBuilder sb, String s) {
        if (s == null || s.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append(" ");
        sb.append(s.trim());
    }

    private String join(String a, String b) {
        if (a == null || a.trim().isEmpty()) return b == null ? "" : b.trim();
        if (b == null || b.trim().isEmpty()) return a.trim();
        return a.trim() + " " + b.trim();
    }

    private void callCore(String ignored, String text) {
        try {
            JSONObject out = AtlasCore.ask(this, text, conversationId);
            String r = out.optString("reply", "");
            JSONArray memories = out.optJSONArray("memories");
            if (memories != null) {
                for (int i = 0; i < memories.length(); i++) {
                    String memory = memories.optString(i, "").trim();
                    if (!memory.isEmpty()) AtlasStore.remember(this, memory);
                }
            }
            JSONArray actions = out.optJSONArray("actions");
            JSONObject single = out.optJSONObject("action");
            String provider = out.optString("provider", "core");
            final String shown = r.isEmpty() ? "Atlas core returned no reply." : r;
            runOnUiThread(() -> {
                status.setText("CORE: " + provider.toUpperCase(Locale.US));
                if (actions != null && actions.length() > 0) {
                    queuedActions = actions;
                    runQueuedActions(shown);
                } else if (single != null) {
                    routeAction(single, shown);
                } else {
                    speakReply(shown, conversationMode);
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> speakReply("Core error: " + e.getMessage(), conversationMode));
        }
    }

    private String memorySummary() {
        JSONArray a = AtlasStore.memories(this);
        if (a.length() == 0) return "I don't have any saved memories yet.";
        StringBuilder s = new StringBuilder("Latest memories. ");
        int st = Math.max(0, a.length() - 8);
        for (int i = st; i < a.length(); i++) { if (i > st) s.append(". "); s.append(a.optString(i)); }
        return s.toString();
    }

    private String notificationSummary() {
        JSONArray a = AtlasStore.notifications(this);
        if (a.length() == 0) return "I don't have recent notifications stored yet.";
        StringBuilder s = new StringBuilder("Latest notifications. ");
        int st = Math.max(0, a.length() - 8);
        for (int i = st; i < a.length(); i++) { if (i > st) s.append(". "); s.append(a.optString(i)); }
        return s.toString();
    }

    private void speakReply(String s, boolean cont) {
        AtlasStore.addTurn(this, "assistant", s);
        reply.setText("Atlas: " + s);
        status.setText("SPEAKING");
        say(s, cont);
    }

    private void say(String s, boolean cont) {
        if (tts == null) return;
        resumeAfterSpeech = cont;
        tts.speak(s, TextToSpeech.QUEUE_FLUSH, new Bundle(), "atlas-" + System.currentTimeMillis());
    }

    private void stopSpeech() {
        resumeAfterSpeech = false;
        if (tts != null) try { tts.stop(); } catch (Exception ignored) {}
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void configureVoices() {
        if (tts == null) return;
        voiceChoices.clear();
        Set<Voice> all = tts.getVoices();
        if (all != null) for (Voice v : all) {
            Locale l = v.getLocale();
            if (l != null && "en".equalsIgnoreCase(l.getLanguage())) voiceChoices.add(v);
        }
        Collections.sort(voiceChoices, new Comparator<Voice>() {
            public int compare(Voice a, Voice b) {
                int netA = a.isNetworkConnectionRequired() ? 1 : 0;
                int netB = b.isNetworkConnectionRequired() ? 1 : 0;
                if (netA != netB) return Integer.compare(netA, netB);
                int q = Integer.compare(b.getQuality(), a.getQuality());
                if (q != 0) return q;
                int ua = "US".equalsIgnoreCase(a.getLocale().getCountry()) ? 1 : 0;
                int ub = "US".equalsIgnoreCase(b.getLocale().getCountry()) ? 1 : 0;
                if (ua != ub) return Integer.compare(ub, ua);
                return a.getName().compareTo(b.getName());
            }
        });
        String saved = AtlasStore.voiceName(this);
        voiceIndex = -1;
        for (int i = 0; i < voiceChoices.size(); i++) if (voiceChoices.get(i).getName().equals(saved)) { voiceIndex = i; break; }
        if (voiceIndex < 0 && !voiceChoices.isEmpty()) voiceIndex = 0;
        applyVoice();
    }

    private void applyVoice() {
        if (tts == null) return;
        if (voiceIndex >= 0 && voiceIndex < voiceChoices.size()) {
            Voice v = voiceChoices.get(voiceIndex);
            if (tts.setVoice(v) == TextToSpeech.SUCCESS) AtlasStore.setVoiceName(this, v.getName());
        }
        tts.setSpeechRate(AtlasStore.speechRate(this));
        tts.setPitch(AtlasStore.pitch(this));
        updateVoiceInfo();
    }

    private void updateVoiceInfo() {
        if (voiceInfo == null) return;
        String name = voiceIndex >= 0 && voiceIndex < voiceChoices.size() ? voiceChoices.get(voiceIndex).getName() : "default";
        voiceInfo.setText("VOICE: " + name + " • speed " + String.format(Locale.US, "%.2f", AtlasStore.speechRate(this)));
    }

    private void nextVoice() {
        if (voiceChoices.isEmpty()) { toast("No alternate English TTS voices installed"); return; }
        voiceIndex = (voiceIndex + 1) % voiceChoices.size();
        applyVoice();
        say("This is Atlas.", false);
    }

    private void adjustRate(float delta) {
        float v = Math.max(0.65f, Math.min(1.25f, AtlasStore.speechRate(this) + delta));
        AtlasStore.setSpeechRate(this, v);
        if (tts != null) tts.setSpeechRate(v);
        updateVoiceInfo();
        say("Speed adjusted.", false);
    }

    @Override public void onInit(int statusCode) {
        if (statusCode == TextToSpeech.SUCCESS) {
            configureVoices();
            tts.setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id) { runOnUiThread(() -> status.setText("SPEAKING")); }
                public void onDone(String id) {
                    runOnUiThread(() -> {
                        status.setText("IDLE");
                        if (resumeAfterSpeech && conversationMode && !destroyed) handler.postDelayed(MainActivity.this::listen, 450);
                    });
                }
                public void onError(String id) { runOnUiThread(() -> status.setText("IDLE")); }
            });
            status.setText("IDLE");
        } else status.setText("TTS ERROR");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshAccess();
        if (requestCode == MIC && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen();
    }

    @Override protected void onResume() { super.onResume(); refreshAccess(); }

    @Override protected void onDestroy() {
        destroyed = true;
        stopSpeech();
        if (sr != null) { try { sr.destroy(); } catch (Exception ignored) {} }
        if (tts != null) { try { tts.shutdown(); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
