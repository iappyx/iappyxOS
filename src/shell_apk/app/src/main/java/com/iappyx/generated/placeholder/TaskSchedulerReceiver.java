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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

/** Receives AlarmManager broadcasts to launch TaskService for scheduled background work. */
public class TaskSchedulerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String taskId = intent.getStringExtra("taskId");
        String callbackFn = intent.getStringExtra("callbackFn");
        if (taskId == null || callbackFn == null) return;

        Log.i("iappyxOS-Task", "Alarm fired for task: " + taskId);

        // Start the task service
        Intent svc = new Intent(context, TaskService.class);
        svc.putExtra("taskId", taskId);
        svc.putExtra("callbackFn", callbackFn);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }

        // Reschedule the next alarm (since we use exact alarms, not repeating)
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("iappyx_tasks", Context.MODE_PRIVATE);
            JSONObject tasks = new JSONObject(prefs.getString("tasks", "{}"));
            if (tasks.has(taskId)) {
                long intervalMs = tasks.getJSONObject(taskId).getLong("intervalMs");
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                Intent next = new Intent(context, TaskSchedulerReceiver.class);
                next.putExtra("taskId", taskId);
                next.putExtra("callbackFn", callbackFn);
                next.setAction("com.iappyx.TASK_" + taskId);
                PendingIntent pi = PendingIntent.getBroadcast(context,
                    taskId.hashCode(), next, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                long triggerAt = System.currentTimeMillis() + intervalMs;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
                Log.i("iappyxOS-Task", "Rescheduled task " + taskId + " in " + (intervalMs / 60000) + " min");
            }
        } catch (Exception e) {
            Log.e("iappyxOS-Task", "Reschedule error: " + e.getMessage());
        }
    }
}
