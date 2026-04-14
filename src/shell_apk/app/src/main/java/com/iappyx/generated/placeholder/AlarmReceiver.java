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
        // On device reboot — re-register saved alarms on background thread to avoid ANR
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            final PendingResult pendingResult = goAsync();
            final Context ctx = context.getApplicationContext();
            new Thread(() -> {
                try {
                    reRegisterAlarms(ctx);
                    // If any persistent triggers exist, start the keepalive so their
                    // dynamic receiver survives reboot without waiting for the user
                    // to open the app. BOOT_COMPLETED is on the FGS-exempt list.
                    if (TriggerStore.hasAnyPersistent(ctx)) {
                        TriggerKeepaliveService.start(ctx);
                    }
                }
                finally { pendingResult.finish(); }
            }).start();
            return;
        }

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

        // Clear stored alarm data — keep callbackFn and interval for repeating alarms
        android.content.SharedPreferences alarmPrefs = context.getSharedPreferences("iappyx_alarm", Context.MODE_PRIVATE);
        long interval = alarmPrefs.getLong("interval_" + id, 0);
        if (interval > 0) {
            // Repeating alarm — reschedule next occurrence
            alarmPrefs.edit().remove("ts_" + id).apply();
            try {
                android.app.AlarmManager am = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (am != null) {
                    Intent next = new Intent(context, AlarmReceiver.class);
                    next.putExtra("callbackFn", callbackFn);
                    next.putExtra("alarmId", id);
                    int rc = id.hashCode() & 0x7FFFFFFF;
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
                    PendingIntent pi2 = PendingIntent.getBroadcast(context, rc, next, flags);
                    scheduleExact(am, System.currentTimeMillis() + interval, pi2);
                }
            } catch (Exception ignored) {}
        } else {
            // One-shot alarm — clean up everything
            alarmPrefs.edit().remove("callbackFn_" + id).remove("ts_" + id).remove("interval_" + id).apply();
        }
    }

    private void reRegisterAlarms(Context context) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("iappyx_alarm", Context.MODE_PRIVATE);
            java.util.Map<String, ?> all = prefs.getAll();
            android.app.AlarmManager am = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            // Collect alarm IDs from both ts_ and interval_ keys
            java.util.Set<String> alarmIds = new java.util.HashSet<>();
            for (String key : all.keySet()) {
                if (key.startsWith("ts_")) alarmIds.add(key.substring(3));
                else if (key.startsWith("interval_")) alarmIds.add(key.substring(9));
            }
            for (String id : alarmIds) {
                long ts = prefs.getLong("ts_" + id, 0);
                String fn = prefs.getString("callbackFn_" + id, null);
                long interval = prefs.getLong("interval_" + id, 0);
                if (fn == null) continue;
                Intent alarmIntent = new Intent(context, AlarmReceiver.class);
                alarmIntent.putExtra("callbackFn", fn);
                alarmIntent.putExtra("alarmId", id);
                int rc = id.hashCode() & 0x7FFFFFFF;
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
                PendingIntent pi = PendingIntent.getBroadcast(context, rc, alarmIntent, flags);
                if (interval > 0) {
                    // Repeating alarm — schedule next occurrence
                    scheduleExact(am, System.currentTimeMillis() + interval, pi);
                } else if (ts > System.currentTimeMillis()) {
                    // Future one-shot alarm
                    scheduleExact(am, ts, pi);
                }
                // Past one-shot alarms — expired, clean up
                else {
                    prefs.edit().remove("ts_" + id).remove("callbackFn_" + id).remove("interval_" + id).apply();
                }
            }
        } catch (Exception e) {
            android.util.Log.e("iappyxOS", "reRegisterAlarms: " + e.getMessage());
        }
        // Also re-register scheduled tasks
        try {
            android.app.AlarmManager taskAm = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (taskAm == null) return;
            android.content.SharedPreferences taskPrefs = context.getSharedPreferences("iappyx_tasks", Context.MODE_PRIVATE);
            String tasksJson = taskPrefs.getString("tasks", "{}");
            org.json.JSONObject tasks = new org.json.JSONObject(tasksJson);
            java.util.Iterator<String> taskKeys = tasks.keys();
            while (taskKeys.hasNext()) {
                String taskId = taskKeys.next();
                org.json.JSONObject task = tasks.getJSONObject(taskId);
                String fn = task.getString("callbackFn");
                long intervalMs = task.getLong("intervalMs");
                Intent taskIntent = new Intent(context, TaskSchedulerReceiver.class);
                taskIntent.putExtra("taskId", taskId);
                taskIntent.putExtra("callbackFn", fn);
                taskIntent.setAction("com.iappyx.TASK_" + taskId);
                int tFlags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) tFlags |= PendingIntent.FLAG_IMMUTABLE;
                PendingIntent tPi = PendingIntent.getBroadcast(context, taskId.hashCode(), taskIntent, tFlags);
                scheduleExact(taskAm, System.currentTimeMillis() + intervalMs, tPi);
            }
        } catch (Exception e) {
            android.util.Log.e("iappyxOS", "reRegisterTasks: " + e.getMessage());
        }
    }

    private static void scheduleExact(android.app.AlarmManager am, long triggerAt, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
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
