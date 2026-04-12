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
