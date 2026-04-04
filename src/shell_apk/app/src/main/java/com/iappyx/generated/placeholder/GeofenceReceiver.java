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
