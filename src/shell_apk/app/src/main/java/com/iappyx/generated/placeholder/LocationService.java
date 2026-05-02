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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Foreground service for continuous GPS tracking.
 * Keeps location updates running when app is backgrounded or screen is off.
 */
public class LocationService extends Service {
    private static final String CH = "iappyx_location";
    private static final String TAG = "iappyxOS";
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";

    private LocationManager locationManager;
    private LocationListener locationListener;
    private String callbackFn;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTracking();
            stopForeground(true);
            // Don't stopSelf() — keep alive for quick restart, avoids foreground service race
            return START_NOT_STICKY;
        }

        callbackFn = intent.getStringExtra("callbackFn");
        String title = intent.getStringExtra("title");
        long interval = intent.getLongExtra("interval", 2000);
        float minDistance = intent.getFloatExtra("minDistance", 1f);

        startForeground(889, buildNotification(title != null ? title : "Tracking location"));
        startTracking(interval, minDistance);

        return START_STICKY;
    }

    private void startTracking(long interval, float minDistance) {
        stopTracking();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return;

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location l) {
                String json = "{\"lat\":" + l.getLatitude() +
                    ",\"lon\":" + l.getLongitude() + ",\"accuracy\":" + l.getAccuracy() +
                    ",\"altitude\":" + l.getAltitude() + ",\"speed\":" + l.getSpeed() +
                    ",\"bearing\":" + l.getBearing() +
                    ",\"time\":" + l.getTime() + "}";
                // Pass the JSON + callback name DIRECTLY in the broadcast
                // extras. The previous design wrote to SharedPreferences
                // and broadcast an empty intent — the receiver raced
                // against prefs.apply() (async) and routinely read null.
                // Same in-process broadcast, just race-free. Receiver-side
                // change in ShellActivity.locationUpdateReceiver must land
                // in the same commit to keep the contract intact.
                Intent update = new Intent("com.iappyx.LOCATION_UPDATE");
                update.setPackage(getPackageName());
                update.putExtra("json", json);
                update.putExtra("callbackFn", callbackFn);
                sendBroadcast(update);
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };

        // Subscribe to BOTH providers. GPS is precise but unavailable
        // indoors / in tunnels — NETWORK_PROVIDER (cell + wifi
        // triangulation) fills those gaps. Same listener handles both;
        // duplicate fixes within the interval are harmless (callers do
        // their own dedupe / throttling). Each provider is wrapped in
        // its own try/catch so a missing provider on a stripped-down
        // device doesn't tank the whole subscription.
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, interval, minDistance, locationListener);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "LocationService GPS: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "GPS_PROVIDER unavailable on this device");
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, interval, minDistance, locationListener);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "LocationService NETWORK: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "NETWORK_PROVIDER unavailable on this device");
        }
    }

    private void stopTracking() {
        if (locationListener != null && locationManager != null) {
            locationManager.removeUpdates(locationListener);
            locationListener = null;
        }
    }

    private Notification buildNotification(String title) {
        createChannel();
        Intent launch = new Intent();
        launch.setComponent(new android.content.ComponentName(
            getPackageName(),
            "com.iappyx.generated.placeholder.ShellActivity"));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, flags);
        return new NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText("Tracking in progress")
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CH, "iappyxOS Location", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onTaskRemoved(Intent rootIntent) {
        stopTracking();
        stopForeground(true);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() { stopTracking(); super.onDestroy(); }
}
