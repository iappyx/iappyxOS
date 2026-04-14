/*
 * MIT License
 *
 * Copyright (c) 2026 iappyx
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.iappyx.generated.placeholder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.car.app.connection.CarConnection;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LifecycleService;
import androidx.lifecycle.Observer;

import org.json.JSONObject;

/**
 * Keeps the app process alive while any persistent trigger is registered.
 *
 * Rationale: on Android 15+ the dynamic {@link TriggerReceiver} only fires while
 * the process is alive. Without this service, triggers die when the user swipes
 * the app away, or when Android evicts the process under memory pressure. A
 * low-priority foreground-service notification gives the process "perceptible"
 * importance, so broadcasts continue to land.
 *
 * Lifecycle:
 *   - Started via {@link #start(Context)} from {@link ShellActivity.TriggerBridge}
 *     when a caller registers a trigger with {@code persistent: true}.
 *     Also started from {@link AlarmReceiver} on {@code BOOT_COMPLETED} if any
 *     persistent trigger survives in the store.
 *   - Stopped via {@link #stop(Context)} when the last persistent trigger is
 *     cancelled.
 *   - On {@code onCreate} re-registers the {@link TriggerReceiver} dynamically
 *     so the receiver survives the {@link ShellActivity} being destroyed.
 */
public class TriggerKeepaliveService extends LifecycleService {
    private static final String TAG = "iappyxOS";
    private static final String CH = "iappyx_trigger_keepalive";
    private static final int NOTIF_ID = 9131;
    private static final String ACTION_REFRESH = "com.iappyx.trigger.REFRESH";

    private CarConnection carConnection;
    private Observer<Integer> carConnectionObserver;
    private int lastCarConnectionType = CarConnection.CONNECTION_TYPE_NOT_CONNECTED;

    public static void start(Context ctx) {
        try {
            Intent i = new Intent(ctx, TriggerKeepaliveService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception e) {
            Log.w(TAG, "TriggerKeepaliveService.start: " + e.getMessage());
        }
    }

    public static void stop(Context ctx) {
        try { ctx.stopService(new Intent(ctx, TriggerKeepaliveService.class)); }
        catch (Exception e) { Log.w(TAG, "TriggerKeepaliveService.stop: " + e.getMessage()); }
    }

    /** Update the visible count in the notification after a register/cancel. */
    public static void refresh(Context ctx) {
        if (!TriggerStore.hasAnyPersistent(ctx)) { stop(ctx); return; }
        try {
            Intent i = new Intent(ctx, TriggerKeepaliveService.class);
            i.setAction(ACTION_REFRESH);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception e) {
            Log.w(TAG, "TriggerKeepaliveService.refresh: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Register TriggerReceiver here too so the receiver outlives ShellActivity.
        TriggerReceiver.registerDynamic(this);

        // Subscribe to CarConnection LiveData. Fires on state transitions;
        // dispatch to existing trigger machinery so debounce/persistence apply.
        try {
            carConnection = new CarConnection(this);
            carConnectionObserver = new Observer<Integer>() {
                @Override public void onChanged(Integer state) {
                    if (state == null) return;
                    int prev = lastCarConnectionType;
                    lastCarConnectionType = state;
                    boolean wasConnected = prev != CarConnection.CONNECTION_TYPE_NOT_CONNECTED;
                    boolean isConnected  = state != CarConnection.CONNECTION_TYPE_NOT_CONNECTED;
                    if (wasConnected == isConnected) return; // no edge
                    try {
                        JSONObject extras = new JSONObject();
                        extras.put("connectionType",
                            state == CarConnection.CONNECTION_TYPE_NATIVE ? "native"
                                : state == CarConnection.CONNECTION_TYPE_PROJECTION ? "projection"
                                : "none");
                        TriggerReceiver.dispatchExternal(TriggerKeepaliveService.this,
                            "auto", isConnected ? "connected" : "disconnected", null, extras);
                    } catch (Exception e) {
                        Log.w(TAG, "auto dispatch: " + e.getMessage());
                    }
                }
            };
            carConnection.getType().observe(this, carConnectionObserver);
        } catch (Throwable t) {
            // CarConnection can fail on devices without the Auto host — fine, no-op.
            Log.w(TAG, "CarConnection unavailable: " + t.getMessage());
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        startForeground(NOTIF_ID, buildNotification());
        return START_STICKY;
    }

    private Notification buildNotification() {
        createChannel();
        Intent launch = new Intent();
        launch.setComponent(new ComponentName(getPackageName(),
            "com.iappyx.generated.placeholder.ShellActivity"));
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, piFlags);

        int count = TriggerStore.countPersistent(this);
        String text = count == 1 ? "Watching for 1 event" : "Watching for " + count + " events";

        return new NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Triggers active")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
            CH, "Background triggers", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        ch.setDescription("Keeps trigger watchers alive when the app is closed");
        nm.createNotificationChannel(ch);
    }

    @Override
    @Nullable
    public IBinder onBind(Intent intent) { super.onBind(intent); return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // User swiped the app away — service stays foreground so receiver survives.
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        // LifecycleService removes observers bound with observe(this, ...) automatically,
        // but clear field refs to let the CarConnection LiveData be GC'd.
        try {
            if (carConnection != null && carConnectionObserver != null) {
                carConnection.getType().removeObserver(carConnectionObserver);
            }
        } catch (Exception ignored) {}
        carConnection = null;
        carConnectionObserver = null;
        super.onDestroy();
    }
}
