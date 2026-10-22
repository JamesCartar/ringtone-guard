package com.family.ringtoneguard;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status, history, targetLabel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        public void run() {
            status.setText(RingControl.status(MainActivity.this));
            history.setText(RingControl.prefs(MainActivity.this).getString("history", "No corrections yet."));
            handler.postDelayed(this, 1000);
        }
    };
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private TextView text(LinearLayout root, String value, int size) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setPadding(0, dp(8), 0, dp(8)); root.addView(t); return t;
    }
    private void button(LinearLayout root, String label, View.OnClickListener action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setOnClickListener(action);
        root.addView(button, new LinearLayout.LayoutParams(-1, dp(52)));
    }
    private void settings(String action) {
        try { startActivity(new Intent(action)); }
        catch (ActivityNotFoundException e) { Toast.makeText(this, "Setting unavailable on this phone", Toast.LENGTH_LONG).show(); }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(root); setContentView(scroll);
        text(root, "Ringtone Guard", 28);
        text(root, "Keep your parents' calls audible", 16);
        status = text(root, "", 16);
        int percent = RingControl.prefs(this).getInt("percent", 80);
        targetLabel = text(root, "Minimum ringtone volume: " + percent + "%", 17);
        SeekBar slider = new SeekBar(this); slider.setMax(9); slider.setProgress(percent / 10 - 1); root.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                int value = (progress + 1) * 10;
                targetLabel.setText("Minimum ringtone volume: " + value + "%");
                if (user) RingControl.prefs(MainActivity.this).edit().putInt("percent", value).apply();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        button(root, "Restore Ringing Now", v -> {
            String result = RingControl.restore(this); status.setText(result);
            Toast.makeText(this, "Checked ringing settings — see status below title", Toast.LENGTH_LONG).show();
        });
        button(root, "Start Protection", v -> new AlertDialog.Builder(this)
            .setTitle("Keep ringing audible?")
            .setMessage("While enabled, the guard will undo volume reductions and silent/vibrate mode. Pause it whenever quiet is needed. Some phones link notification and ringtone volume. Default minimum is 80%.")
            .setNegativeButton("Cancel", null).setPositiveButton("Start", (d,w) -> {
                RingControl.prefs(this).edit().putBoolean("enabled", true).apply();
                try { startForegroundService(new Intent(this, GuardService.class)); }
                catch (RuntimeException e) {
                    RingControl.prefs(this).edit().putBoolean("enabled", false).apply();
                    RingControl.record(this, "Could not start protection: " + e.getClass().getSimpleName());
                }
            }).show());
        button(root, "Pause Protection", v -> {
            RingControl.prefs(this).edit().putBoolean("enabled", false).apply();
            stopService(new Intent(this, GuardService.class));
        });
        button(root, "Grant DND / sound-mode access", v -> settings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        CheckBox dnd = new CheckBox(this); dnd.setText("Also turn off Do Not Disturb (optional)");
        dnd.setChecked(RingControl.prefs(this).getBoolean("overrideDnd", false)); root.addView(dnd);
        dnd.setOnCheckedChangeListener((b, checked) -> {
            if (checked && !RingControl.hasAccess(this)) {
                b.setChecked(false); Toast.makeText(this, "Grant DND access first", Toast.LENGTH_LONG).show(); return;
            }
            RingControl.prefs(this).edit().putBoolean("overrideDnd", checked).apply();
        });
        button(root, "Battery / background settings", v -> {
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
            catch (ActivityNotFoundException e) { settings(Settings.ACTION_SETTINGS); }
        });
        text(root, "Allow background activity / unrestricted battery usage and Auto-start in XOS. DND access may be needed to leave silent mode. DND itself is respected unless you opt in above. No internet, contacts, microphone, or call permission needed.", 14);
        text(root, "Recent activity", 19);
        history = text(root, "", 13);
        text(root, "Prototype: event detection and a 5-second check while the CPU is awake. Deep sleep, force-stop, and XOS battery management can interrupt protection. Test locked, unplugged, and after a reboot before relying on it. This app cannot fix a silent ringtone file or network problems.", 13);
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
