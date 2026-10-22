package com.family.ringtoneguard;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.os.*;

public class GuardService extends Service {
    static volatile boolean running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable check = new Runnable() {
        public void run() {
            if (!RingControl.prefs(GuardService.this).getBoolean("enabled", false)) { stopSelf(); return; }
            RingControl.restore(GuardService.this);
            handler.postDelayed(this, 5000);
        }
    };
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            handler.removeCallbacks(check);
            handler.post(check);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("guard", "Ringtone protection", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, GuardService.class).setAction("STOP");
        PendingIntent pause = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "guard")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Ringtone Guard is protecting this phone")
            .setContentText("Keeps ringing audible. Tap to manage or pause.")
            .setContentIntent(pending).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Pause", pause).build()).build();
        startForeground(1, notification);
        running = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        // Vendor-dependent hint, not a public API: polling is the fallback.
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(changes, filter);
        RingControl.record(this, "Protection service started.");
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            RingControl.prefs(this).edit().putBoolean("enabled", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!RingControl.prefs(this).getBoolean("enabled", false)) { stopSelf(); return START_NOT_STICKY; }
        handler.removeCallbacks(check);
        handler.post(check);
        return START_STICKY;
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(changes);
        running = false;
        RingControl.record(this, "Protection service stopped.");
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
