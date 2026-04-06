package com.iappyx.generated.placeholder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Handles incoming FCM push notifications.
 * Foreground: passes to PushBridge JS callback.
 * Background: shows Android notification, opens app on tap.
 */
public class PushService extends FirebaseMessagingService {
    private static final String TAG = "iappyxOS";
    private static final String CH = "iappyx_push";

    // Static callback for foreground messages (set by PushBridge)
    static volatile String foregroundCallbackFn;
    static volatile ShellActivity activeActivity;

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        Log.i(TAG, "Push received: " + msg.getMessageId());

        String title = "";
        String body = "";
        if (msg.getNotification() != null) {
            title = msg.getNotification().getTitle() != null ? msg.getNotification().getTitle() : "";
            body = msg.getNotification().getBody() != null ? msg.getNotification().getBody() : "";
        }

        // Build data JSON
        StringBuilder dataJson = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, String> e : msg.getData().entrySet()) {
            if (!first) dataJson.append(",");
            dataJson.append("\"").append(e.getKey().replace("\"", "\\\"")).append("\":\"")
                .append(e.getValue().replace("\"", "\\\"")).append("\"");
            first = false;
        }
        dataJson.append("}");

        // If app is in foreground and callback is set, deliver to JS
        ShellActivity activity = activeActivity;
        String fn = foregroundCallbackFn;
        if (activity != null && fn != null) {
            String json = "{\"title\":\"" + title.replace("\"", "\\\"") +
                "\",\"body\":\"" + body.replace("\"", "\\\"") +
                "\",\"data\":" + dataJson + "}";
            activity.fireEvent(fn, json);
        } else {
            // Background — show notification
            showNotification(title.isEmpty() ? "New message" : title, body, dataJson.toString());
        }
    }

    @Override
    public void onNewToken(String token) {
        Log.i(TAG, "FCM token refreshed");
        ShellActivity activity = activeActivity;
        String fn = tokenRefreshFn;
        if (activity != null && fn != null) {
            activity.fireEvent(fn, "{\"token\":\"" + token + "\"}");
        }
    }

    static volatile String tokenRefreshFn;

    private void showNotification(String title, String body, String dataJson) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH, "Push Notifications", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        Intent intent = new Intent(this, ShellActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("push_data", dataJson);
        intent.putExtra("push_title", title);
        intent.putExtra("push_body", body);
        PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) System.currentTimeMillis(), nb.build());
    }
}
