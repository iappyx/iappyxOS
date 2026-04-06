package com.iappyx.generated.placeholder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

/**
 * Foreground service that keeps the process alive while persistent network
 * connections are active (SSH, TCP, BLE, HTTP Server).
 * Uses reference counting — starts on first request, stops when count hits 0.
 */
public class NetworkService extends Service {
    private static final String CH = "iappyx_network";
    private static final String TAG = "iappyxOS";
    private static int refCount = 0;
    private static final Object lock = new Object();
    private static String currentLabel = "Active connection";

    public static void requestStart(Context ctx, String label) {
        synchronized (lock) {
            refCount++;
            currentLabel = label;
            Intent intent = new Intent(ctx, NetworkService.class);
            intent.putExtra("label", label);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent);
            } else {
                ctx.startService(intent);
            }
        }
    }

    public static void requestStop(Context ctx) {
        synchronized (lock) {
            refCount--;
            if (refCount <= 0) {
                refCount = 0;
                ctx.stopService(new Intent(ctx, NetworkService.class));
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH, "Network", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Active network connections");
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String label = "Active connection";
        if (intent != null && intent.hasExtra("label")) {
            label = intent.getStringExtra("label");
        }

        Intent tapIntent = new Intent(this, ShellActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(label)
            .setContentText("Tap to return to app")
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        startForeground(9999, notification);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Don't reset refCount — callers manage their own start/stop balance.
        // If system kills the service, callers will call requestStop() when
        // connections close, which safely clamps to 0.
    }
}
