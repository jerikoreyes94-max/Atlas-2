package com.atlas.personalai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.UUID;

public class CoreSettingsActivity extends Activity {
    private EditText openRouter, groq, gemini;
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);

        TextView title = text("ATLAS 1.1 - FREE AI CORE", 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);
        root.addView(text("Add ONE free-tier key. Atlas automatically routes and fails over between configured providers. Keys are encrypted with Android Keystore and are not stored in the GitHub source. Atlas sends sensitive phone context only when your request calls for it.", 15));

        status = text(AtlasCore.status(this), 14);
        root.addView(status);

        openRouter = secretField("OpenRouter API key" + configured("openrouter_key"));
        root.addView(openRouter);
        Button or = button("OPEN OPENROUTER KEY PAGE");
        or.setOnClickListener(v -> open("https://openrouter.ai/settings/keys"));
        root.addView(or);

        groq = secretField("Groq API key" + configured("groq_key"));
        root.addView(groq);
        Button g = button("OPEN GROQ KEY PAGE");
        g.setOnClickListener(v -> open("https://console.groq.com/keys"));
        root.addView(g);

        gemini = secretField("Gemini API key" + configured("gemini_key"));
        root.addView(gemini);
        Button gm = button("OPEN GOOGLE AI STUDIO KEY PAGE");
        gm.setOnClickListener(v -> open("https://aistudio.google.com/apikey"));
        root.addView(gm);

        Button save = button("SAVE CORE KEYS");
        save.setOnClickListener(v -> saveKeys());
        root.addView(save);

        Button test = button("TEST ATLAS CORE");
        test.setOnClickListener(v -> testCore());
        root.addView(test);

        Button clear = button("CLEAR ALL CORE KEYS");
        clear.setOnClickListener(v -> {
            SecureStore.clearCoreKeys(this);
            toast("Core keys cleared");
            status.setText(AtlasCore.status(this));
        });
        root.addView(clear);

        root.addView(text("AUTO ROUTER: questions needing fresh web information prefer Groq Compound when configured. General reasoning prefers OpenRouter's free-model router. Gemini Gemma 4 is another free fallback. Your existing custom Atlas Core URL remains a final fallback.", 14));

        Button back = button("BACK TO ATLAS");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private String configured(String key) {
        return SecureStore.has(this, key) ? " - CONFIGURED (leave blank to keep)" : "";
    }

    private EditText secretField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setPadding(12, 18, 12, 18);
        return e;
    }

    private TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setPadding(12, 12, 12, 12);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this); b.setText(s); return b;
    }

    private void saveKeys() {
        String o = openRouter.getText().toString().trim();
        String g = groq.getText().toString().trim();
        String m = gemini.getText().toString().trim();
        if (!o.isEmpty()) SecureStore.set(this, "openrouter_key", o);
        if (!g.isEmpty()) SecureStore.set(this, "groq_key", g);
        if (!m.isEmpty()) SecureStore.set(this, "gemini_key", m);
        openRouter.setText(""); groq.setText(""); gemini.setText("");
        status.setText(AtlasCore.status(this));
        toast("Atlas core settings saved");
    }

    private void testCore() {
        saveKeys();
        if (!AtlasCore.isConfigured(this) && AtlasStore.coreUrl(this).isEmpty()) {
            toast("Add at least one key first"); return;
        }
        status.setText("Testing core...");
        new Thread(() -> {
            try {
                JSONObject r = AtlasCore.ask(this,
                        "This is a core connection test. Reply briefly that Atlas core is online. Do not run any phone action.",
                        UUID.randomUUID().toString());
                String provider = r.optString("provider", "core");
                String reply = r.optString("reply", "online");
                runOnUiThread(() -> status.setText("ONLINE via " + provider + " - " + reply));
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("TEST FAILED - " + e.getMessage()));
            }
        }).start();
    }

    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("Could not open browser"); }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
