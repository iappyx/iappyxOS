package com.iappyx.generated.placeholder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receives notification action button taps and relaunches ShellActivity
 * to deliver the event to JavaScript.
 */
public class NotificationActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String actionId = intent.getStringExtra("actionId");
        String notificationId = intent.getStringExtra("notificationId");
        String callbackFn = intent.getStringExtra("callbackFn");

        if (actionId == null || callbackFn == null) return;

        // Store for ShellActivity to pick up
        context.getSharedPreferences("iappyx_notif_action", Context.MODE_PRIVATE).edit()
            .putString("pending_actionId", actionId)
            .putString("pending_notificationId", notificationId != null ? notificationId : "")
            .putString("pending_callbackFn", callbackFn)
            .apply();

        // Launch ShellActivity
        try {
            Intent launch = new Intent();
            launch.setComponent(new android.content.ComponentName(
                context.getPackageName(),
                "com.iappyx.generated.placeholder.ShellActivity"));
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            launch.putExtra("notification_action", true);
            context.startActivity(launch);
        } catch (Exception e) {
            Log.e("iappyxOS", "NotificationActionReceiver: " + e.getMessage());
        }
    }
}
