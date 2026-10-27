package io.github.soullink9.lena;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One plain screen: server address, model, and what the assistant gesture does. */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        col.setPadding(pad, pad, pad, pad);

        col.addView(text("Lena", 26));
        col.addView(text("Open WebUI address", 14));
        EditText url = new EditText(this);
        url.setHint("https://ai.example.com");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setSingleLine(true);
        String current = Prefs.server(this);
        if (current != null) url.setText(current);
        col.addView(url);

        col.addView(text("Model for new chats (optional, the model's ID in Open WebUI)", 14));
        EditText model = new EditText(this);
        model.setHint("lena");
        model.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        model.setSingleLine(true);
        String currentModel = Prefs.model(this);
        if (currentModel != null) model.setText(currentModel);
        col.addView(model);

        CheckBox call = new CheckBox(this);
        call.setText("Assistant gesture starts a voice call");
        call.setChecked(Prefs.callOnAssist(this));
        col.addView(call);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> {
            String server = Prefs.normalize(url.getText().toString());
            if (server == null) {
                url.setError("Use your https:// address");
                return;
            }
            Prefs.save(this, server, model.getText().toString().trim(), call.isChecked());
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        });
        col.addView(save);

        TextView help = text("To open Lena with the assistant gesture, pick it as the "
                + "Digital assistant app in your phone's default apps.", 14);
        help.setPadding(0, dp(24), 0, 0);
        col.addView(help);
        Button defaults = new Button(this);
        defaults.setText("Open default apps");
        defaults.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                // some phones hide this screen; it's under Settings > Apps > Default apps
            }
        });
        col.addView(defaults);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(col);
        setContentView(scroll);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
