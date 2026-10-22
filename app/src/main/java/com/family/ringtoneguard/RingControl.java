package com.family.ringtoneguard;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.util.Log;
import java.text.DateFormat;
import java.util.Date;

final class RingControl {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("guard", Context.MODE_PRIVATE); }
    static boolean hasAccess(Context c) {
        return c.getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted();
    }
    static void record(Context c, String text) {
        String entry = DateFormat.getTimeInstance().format(new Date()) + "  " + text;
        String old = prefs(c).getString("history", "");
        String history = entry + "\n" + old;
        if (history.length() > 6000) history = history.substring(0, 6000);
        prefs(c).edit().putString("history", history).apply();
        Log.i("RingtoneGuard", text);
    }
    static String restore(Context c) {
        AudioManager audio = c.getSystemService(AudioManager.class);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        StringBuilder result = new StringBuilder();
        boolean access = hasAccess(c);
        boolean overrideDnd = prefs(c).getBoolean("overrideDnd", false);
        boolean dndOn = nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        // Changing ringer mode can implicitly toggle DND on Android. Skip audio
        // corrections entirely while DND is active unless explicitly opted in.
        if (!GuardPolicy.mayCorrect(dndOn, overrideDnd, access)) return status(c);
        // DND is a separate, explicitly opt-in setting. Never clear it by default.
        if (overrideDnd && access && dndOn) {
            try { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL); result.append("DND off. "); }
            catch (SecurityException e) { result.append("DND denied. "); }
        }
        if (audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            try { audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL); result.append("Normal ringing requested. "); }
            catch (SecurityException e) { result.append("Sound mode needs DND access. "); }
        }
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_RING);
        int target = GuardPolicy.target(max, prefs(c).getInt("percent", 80));
        if (audio.getStreamVolume(AudioManager.STREAM_RING) < target) {
            try { audio.setStreamVolume(AudioManager.STREAM_RING, target, 0); result.append("Ringtone raised to ").append(target).append('/').append(max).append(". "); }
            catch (SecurityException e) { result.append("Volume change denied; grant DND access. "); }
        }
        if (result.length() > 0) {
            // Do not log the same denied operation every five seconds.
            String text = result.toString().trim();
            if (!text.equals(prefs(c).getString("lastResult", ""))) record(c, text);
            prefs(c).edit().putString("lastResult", text).apply();
        } else {
            prefs(c).edit().remove("lastResult").apply();
        }
        return status(c);
    }
    static String status(Context c) {
        AudioManager a = c.getSystemService(AudioManager.class);
        NotificationManager n = c.getSystemService(NotificationManager.class);
        int mode = a.getRingerMode();
        String sound = mode == AudioManager.RINGER_MODE_NORMAL ? "Normal" : mode == AudioManager.RINGER_MODE_VIBRATE ? "Vibrate" : "Silent";
        return "Ringtone: " + a.getStreamVolume(AudioManager.STREAM_RING) + "/" + a.getStreamMaxVolume(AudioManager.STREAM_RING)
            + "\nSound mode: " + sound
            + "\nDND: " + (n.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALL ? "Off" : "On / restricted")
            + "\nDND access: " + (hasAccess(c) ? "Granted" : "Not granted")
            + "\nGuard service: " + (GuardService.running ? "Running" : "Stopped")
            + "\nAuto-start requested: " + (prefs(c).getBoolean("enabled", false) ? "Yes" : "No");
    }
}
