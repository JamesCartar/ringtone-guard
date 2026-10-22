package com.family.ringtoneguard;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && RingControl.prefs(context).getBoolean("enabled", false)) {
            try { context.startForegroundService(new Intent(context, GuardService.class)); }
            catch (RuntimeException e) { RingControl.record(context, "Auto-start blocked: open the app and start protection. " + e.getClass().getSimpleName()); }
        }
    }
}
