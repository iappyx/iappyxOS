package com.iappyx.generated.placeholder;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

/**
 * Shared bridge logic used by both ShellActivity and TaskService.
 * All methods are static and take a Context — no dependency on Activity lifecycle.
 */
public class BridgeUtils {

    // ── Storage ──

    private static final String STORE_PREFS = "iappyx_store";

    public static void save(Context ctx, String key, String value) {
        if (key == null) return;
        ctx.getSharedPreferences(STORE_PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply();
    }

    public static String load(Context ctx, String key) {
        return ctx.getSharedPreferences(STORE_PREFS, Context.MODE_PRIVATE).getString(key, null);
    }

    public static void remove(Context ctx, String key) {
        ctx.getSharedPreferences(STORE_PREFS, Context.MODE_PRIVATE).edit().remove(key).apply();
    }

    // ── Widget ──

    public static void updateWidget(Context ctx, String configJson) {
        try {
            new JSONObject(configJson); // validate
            // Enable widget provider on first use
            android.content.pm.PackageManager pm = ctx.getPackageManager();
            ComponentName widgetComp = new ComponentName(ctx.getPackageName(),
                "com.iappyx.generated.placeholder.WidgetProvider");
            if (pm.getComponentEnabledSetting(widgetComp) != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(widgetComp,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            }
            ctx.getSharedPreferences("iappyx_widget", Context.MODE_PRIVATE)
                .edit().putString("config", configJson).commit();
            AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
            int[] ids = awm.getAppWidgetIds(widgetComp);
            if (ids.length > 0) {
                new WidgetProvider().onUpdate(ctx, awm, ids);
            }
        } catch (Exception e) {
            android.util.Log.e("iappyxOS", "BridgeUtils.updateWidget: " + e.getMessage());
        }
    }

    public static void clearWidget(Context ctx) {
        ctx.getSharedPreferences("iappyx_widget", Context.MODE_PRIVATE)
            .edit().remove("config").commit();
        ComponentName widgetComp = new ComponentName(ctx.getPackageName(),
            "com.iappyx.generated.placeholder.WidgetProvider");
        AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
        int[] ids = awm.getAppWidgetIds(widgetComp);
        if (ids.length > 0) {
            new WidgetProvider().onUpdate(ctx, awm, ids);
        }
    }

    // ── Notification ──

    private static final String NOTIF_CH = "iappyx";

    public static void sendNotification(Context ctx, String title, String body) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(NOTIF_CH, "iappyxOS", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        int id = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
        try {
            nm.notify(id, new NotificationCompat.Builder(ctx, NOTIF_CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(body).setAutoCancel(true).build());
        } catch (Exception ignored) {}
    }

    // ── Device Info ──

    public static String getConnectivity(Context ctx) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "{\"connected\":false}";
            android.net.Network net = cm.getActiveNetwork();
            if (net == null) return "{\"connected\":false,\"type\":\"none\",\"metered\":true}";
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps == null) return "{\"connected\":false,\"type\":\"none\",\"metered\":true}";
            boolean connected = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
            String type = caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                : caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ? "cellular"
                : caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) ? "ethernet" : "other";
            boolean metered = cm.isActiveNetworkMetered();
            return "{\"connected\":" + connected + ",\"type\":\"" + type + "\",\"metered\":" + metered + "}";
        } catch (Exception e) { return "{\"connected\":false}"; }
    }

    // ── HTTP Client (simplified for background tasks) ──

    public static void httpRequest(String optionsJson, TaskCallback callback) {
        try {
            JSONObject opts = new JSONObject(optionsJson);
            String url = opts.getString("url");
            String method = opts.optString("method", "GET").toUpperCase();
            int timeout = opts.optInt("timeout", 15000);

            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();

            okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(url);
            JSONObject headers = opts.optJSONObject("headers");
            if (headers != null) {
                java.util.Iterator<String> keys = headers.keys();
                while (keys.hasNext()) { String k = keys.next(); rb.header(k, headers.getString(k)); }
            }

            String body = opts.optString("body", "");
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                String contentType = opts.optString("contentType", "application/json");
                rb.method(method, okhttp3.RequestBody.create(body.getBytes(), okhttp3.MediaType.parse(contentType)));
            } else if (!"GET".equals(method)) {
                rb.method(method, null); // DELETE, HEAD, OPTIONS
            }

            try (okhttp3.Response resp = client.newCall(rb.build()).execute()) {
                String respBody = resp.body() != null ? resp.body().string() : "";
                JSONObject result = new JSONObject();
                result.put("ok", true);
                result.put("status", resp.code());
                result.put("body", respBody);
                callback.onResult(result.toString());
            }
        } catch (Exception e) {
            callback.onResult("{\"ok\":false,\"error\":\"" + ShellActivity.escapeJson(e.getMessage()) + "\"}");
        }
    }

    public interface TaskCallback {
        void onResult(String json);
    }
}
