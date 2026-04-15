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
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import org.json.JSONObject;

/**
 * Receives geofence transition broadcasts from Play Services' GeofencingClient and
 * routes them through the TriggerReceiver dispatch path so iappyx.trigger.geofence
 * callbacks fire with the same debounce/persistence rules as other trigger types.
 *
 * Manifest-declared, exported=false. Play Services delivers here via a PendingIntent
 * we registered at trigger.geofence() time.
 */
public class GeofenceTransitionReceiver extends BroadcastReceiver {
    private static final String TAG = "iappyxOS";
    public static final String ACTION = "com.iappyx.trigger.GEOFENCE_TRANSITION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        GeofencingEvent ev = GeofencingEvent.fromIntent(intent);
        if (ev == null) return;
        if (ev.hasError()) {
            Log.w(TAG, "Geofence event error: " + ev.getErrorCode());
            return;
        }

        int transition = ev.getGeofenceTransition();
        String eventName;
        switch (transition) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:  eventName = "enter"; break;
            case Geofence.GEOFENCE_TRANSITION_EXIT:   eventName = "exit";  break;
            case Geofence.GEOFENCE_TRANSITION_DWELL:  eventName = "dwell"; break;
            default: return; // unknown transition — ignore
        }

        java.util.List<Geofence> triggered = ev.getTriggeringGeofences();
        if (triggered == null || triggered.isEmpty()) return;

        android.location.Location loc = ev.getTriggeringLocation();
        double evLat = loc != null ? loc.getLatitude() : 0;
        double evLon = loc != null ? loc.getLongitude() : 0;

        for (Geofence fence : triggered) {
            String fenceId = fence.getRequestId();
            if (fenceId == null) continue;
            try {
                JSONObject t = TriggerStore.get(context, fenceId);
                if (t == null) continue;
                if (!"geofence".equals(t.optString("type"))) continue;
                JSONObject extras = new JSONObject();
                extras.put("lat", evLat);
                extras.put("lon", evLon);
                extras.put("radiusM", t.optDouble("radiusM", 0));
                TriggerReceiver.dispatchExternal(context, "geofence", eventName, fenceId, extras);
            } catch (Exception e) {
                Log.w(TAG, "geofence dispatch failed for " + fenceId + ": " + e.getMessage());
            }
        }
    }
}
