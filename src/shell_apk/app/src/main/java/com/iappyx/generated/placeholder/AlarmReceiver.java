package com.iappyx.generated.placeholder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * Receives AlarmManager broadcasts and relaunches ShellActivity.
 * The activity then fires the JS callback via onNewIntent / onResume.
 */
public class AlarmReceiver extends BroadcastReceiver {
    private static final String CH = "iappyx_alarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        String callbackFn = intent.getStringExtra("callbackFn");
        String alarmId = intent.getStringExtra("alarmId");
        String id = alarmId != null ? alarmId : "default";

        // Launch the app — use runtime package + original class name
        Intent launch = new Intent();
        launch.setComponent(new android.content.ComponentName(
            context.getPackageName(),
            "com.iappyx.generated.placeholder.ShellActivity"));
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch.putExtra("alarm_fired", true);
        launch.putExtra("callbackFn", callbackFn);
        context.startActivity(launch);

        // Also fire a notification in case the user is on the lock screen
        createChannel(context);
        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
            int rc = id.hashCode() & 0x7FFFFFFF;
            PendingIntent pi = PendingIntent.getActivity(context, rc, launch, flags);
            nm.notify(rc % 100000, new NotificationCompat.Builder(context, CH)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Alarm")
                .setContentText("Tap to open")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build());
        }

        // Clear stored alarm data using the correct key suffix
        context.getSharedPreferences("iappyx_alarm", Context.MODE_PRIVATE)
            .edit().remove("callbackFn_" + id).remove("ts_" + id).apply();
    }

    private void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CH, "iappyxOS Alarms", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
