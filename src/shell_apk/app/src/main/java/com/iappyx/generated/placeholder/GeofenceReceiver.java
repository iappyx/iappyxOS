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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Receives geofence transition broadcasts from LocationBridge.
 * Stores the transition event for ShellActivity to pick up and fire JS callback.
 */
public class GeofenceReceiver extends BroadcastReceiver {
    private static final String TAG = "iappyxOS";

    @Override
    public void onReceive(Context context, Intent intent) {
        String fenceId = intent.getStringExtra("fenceId");
        String transition = intent.getStringExtra("transition");
        double lat = intent.getDoubleExtra("lat", 0);
        double lon = intent.getDoubleExtra("lon", 0);

        Log.i(TAG, "Geofence transition: " + fenceId + " " + transition);

        // Store event for ShellActivity
        SharedPreferences prefs = context.getSharedPreferences("iappyx_geofence", Context.MODE_PRIVATE);
        String callbackFn = prefs.getString("callback_" + fenceId, null);
        if (callbackFn == null) return;

        prefs.edit()
            .putString("pending_event", "{\"id\":\"" + fenceId +
                "\",\"transition\":\"" + transition +
                "\",\"lat\":" + lat + ",\"lon\":" + lon + "}")
            .putString("pending_callback", callbackFn)
            .apply();

        // Try to launch ShellActivity to deliver the event
        try {
            Intent launch = new Intent();
            launch.setComponent(new android.content.ComponentName(
                context.getPackageName(),
                "com.iappyx.generated.placeholder.ShellActivity"));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            launch.putExtra("geofence_event", true);
            context.startActivity(launch);
        } catch (Exception e) {
            Log.e(TAG, "GeofenceReceiver: " + e.getMessage());
        }
    }
}
