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

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

/**
 * Handles all broadcast-based trigger types (charger, headphones, bluetooth, wifi).
 *
 * Declared statically in the manifest for charger (must survive process death);
 * dynamically registered in ShellActivity / on BOOT_COMPLETED for the rest.
 *
 * When a broadcast matches a registered trigger, this receiver:
 *   1. Applies the per-trigger debounce (default 30s).
 *   2. Updates lastFiredMs in the store.
 *   3. Spawns TaskService with the callback + payload so the JS handler runs
 *      headlessly even if the app is closed.
 */
public class TriggerReceiver extends BroadcastReceiver {
    private static final String TAG = "iappyxOS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        Log.i(TAG, "TriggerReceiver.onReceive: " + action);

        try {
            switch (action) {
                case Intent.ACTION_POWER_CONNECTED:
                    dispatch(context, "charger", "plugged", null, null);
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    dispatch(context, "charger", "unplugged", null, null);
                    break;
                case Intent.ACTION_HEADSET_PLUG: {
                    int state = intent.getIntExtra("state", -1);
                    dispatch(context, "headphones", state == 1 ? "plugged" : "unplugged", null, null);
                    break;
                }
                case BluetoothDevice.ACTION_ACL_CONNECTED:
                case BluetoothDevice.ACTION_ACL_DISCONNECTED: {
                    BluetoothDevice dev;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    } else {
                        dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    }
                    if (dev == null) return;
                    String addr = dev.getAddress();
                    String name = safeBtName(context, dev);
                    boolean connected = BluetoothDevice.ACTION_ACL_CONNECTED.equals(action);
                    JSONObject extra = new JSONObject();
                    extra.put("address", addr);
                    extra.put("name", name == null ? "" : name);
                    dispatch(context, "bluetooth", connected ? "connected" : "disconnected", addr, extra);
                    break;
                }
                case WifiManager.NETWORK_STATE_CHANGED_ACTION: {
                    NetworkInfo ni = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (ni == null) return;
                    boolean connected = ni.isConnected();
                    // Only fire on edge: CONNECTED or DISCONNECTED; ignore CONNECTING/SUSPENDED
                    NetworkInfo.DetailedState dstate = ni.getDetailedState();
                    if (dstate != NetworkInfo.DetailedState.CONNECTED
                        && dstate != NetworkInfo.DetailedState.DISCONNECTED) return;

                    String ssid = null;
                    String bssid = null;
                    if (connected) {
                        try {
                            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                            if (wm != null) {
                                WifiInfo wi = wm.getConnectionInfo();
                                if (wi != null) {
                                    ssid = wi.getSSID();
                                    if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                                        ssid = ssid.substring(1, ssid.length() - 1);
                                    }
                                    bssid = wi.getBSSID();
                                }
                            }
                        } catch (SecurityException se) {
                            // No location permission → we can dispatch "connected" without ssid filter match
                        }
                    }
                    JSONObject extra = new JSONObject();
                    extra.put("ssid", ssid == null ? "" : ssid);
                    extra.put("bssid", bssid == null ? "" : bssid);
                    dispatch(context, "wifi", connected ? "connected" : "disconnected", ssid, extra);
                    break;
                }
                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "TriggerReceiver error: " + e.getMessage());
        }
    }

    private static String safeBtName(Context context, BluetoothDevice dev) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
            }
            return dev.getName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Look up registered triggers of the given type, filter by event + match string,
     * apply debounce, and fire each surviving one via TaskService.
     */
    /**
     * Public entry point for non-broadcast trigger sources (e.g., the CarConnection
     * observer in TriggerKeepaliveService). Wraps the private dispatch so callers
     * from other threads don't have to handle exceptions.
     */
    public static void dispatchExternal(Context context, String type, String event,
                                         String matchValue, JSONObject extras) {
        try { dispatch(context, type, event, matchValue, extras); }
        catch (Exception e) { Log.w(TAG, "dispatchExternal: " + e.getMessage()); }
    }

    private static void dispatch(Context context, String type, String event,
                                 String matchValue, JSONObject extras) throws Exception {
        long now = System.currentTimeMillis();
        for (JSONObject t : TriggerStore.byType(context, type)) {
            String tEvent = t.optString("event", "any");
            if (!"any".equals(tEvent) && !tEvent.equals(event)) continue;

            String tMatch = t.optString("match", "");
            if (!tMatch.isEmpty() && matchValue != null && !tMatch.equalsIgnoreCase(matchValue)) continue;
            // If the trigger has a match filter but the broadcast didn't expose one (e.g., wifi
            // without location permission), skip — can't confirm the filter is satisfied.
            if (!tMatch.isEmpty() && matchValue == null) continue;

            long last = t.optLong("lastFiredMs", 0);
            long debounce = t.optLong("debounceMs", TriggerStore.DEFAULT_DEBOUNCE_MS);
            if (now - last < debounce) continue;

            String id = t.getString("id");
            String callbackFn = t.optString("callbackFn", null);
            if (callbackFn == null || !ShellActivity.isSafeCallbackName(callbackFn)) continue;

            TriggerStore.updateLastFired(context, id, now);

            // Build payload for the JS callback
            JSONObject payload = new JSONObject();
            payload.put("triggerId", id);
            payload.put("type", type);
            payload.put("event", event);
            payload.put("timestamp", now);
            if (extras != null) {
                java.util.Iterator<String> keys = extras.keys();
                while (keys.hasNext()) { String k = keys.next(); payload.put(k, extras.get(k)); }
            }

            Intent svc = new Intent(context, TaskService.class);
            svc.putExtra("taskId", "trigger_" + id);
            svc.putExtra("callbackFn", callbackFn);
            svc.putExtra("payloadJson", payload.toString());
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc);
                else context.startService(svc);
            } catch (Exception e) {
                // Android 12+ throws ForegroundServiceStartNotAllowedException when a
                // background broadcast tries to start an FGS. Fall back to a direct
                // notification so the trigger still produces visible output; the JS
                // callback is skipped in this path.
                Log.w(TAG, "FGS start blocked for trigger " + id + ": " + e.getMessage());
            }

            // Diagnostic + fallback: always post a lightweight system notification so
            // the user can confirm the receiver matched, independent of the service path.
            postDiagnosticNotification(context, id, type, event, extras);
        }
    }

    private static void postDiagnosticNotification(Context context, String id, String type,
                                                   String event, JSONObject extras) {
        try {
            String CH = "iappyx_trigger_diag";
            android.app.NotificationManager nm = (android.app.NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    CH, "Triggers", android.app.NotificationManager.IMPORTANCE_DEFAULT);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            String body = id + " · " + type + "/" + event;
            if (extras != null) {
                String extra = extras.optString("ssid", "");
                if (extra.isEmpty()) extra = extras.optString("name", extras.optString("address", ""));
                if (!extra.isEmpty()) body = body + " · " + extra;
            }
            androidx.core.app.NotificationCompat.Builder b =
                new androidx.core.app.NotificationCompat.Builder(context, CH)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Trigger fired")
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT);
            nm.notify(("trig_" + id).hashCode(), b.build());
        } catch (Exception e) {
            Log.w(TAG, "diagnostic notif failed: " + e.getMessage());
        }
    }

    /**
     * Re-register dynamic receivers (BT + WiFi) on app start / boot.
     * Charger + headphones are declared statically in the manifest.
     */
    public static void registerDynamic(Context context) {
        try {
            TriggerReceiver r = new TriggerReceiver();
            android.content.IntentFilter f = new android.content.IntentFilter();
            // Android 15+ (targetSdk 35) appears to silently suppress some implicit broadcasts
            // to statically-declared receivers even when the app is alive. Registering
            // dynamically from a running context is more reliable.
            f.addAction(Intent.ACTION_POWER_CONNECTED);
            f.addAction(Intent.ACTION_POWER_DISCONNECTED);
            f.addAction(Intent.ACTION_HEADSET_PLUG);
            f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getApplicationContext().registerReceiver(r, f, Context.RECEIVER_EXPORTED);
            } else {
                context.getApplicationContext().registerReceiver(r, f);
            }
            Log.i(TAG, "TriggerReceiver: dynamic registration OK");
        } catch (Exception e) {
            Log.w(TAG, "registerDynamic: " + e.getMessage());
        }
    }
}
