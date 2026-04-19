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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.NotificationCompat;

/** Headless WebView service for executing background JavaScript tasks. */
public class TaskService extends Service {

    private static final String TAG = "iappyxOS-Task";
    private static final String CH = "iappyx_tasks";
    private static final int NOTIF_ID = 889;
    private static final long TIMEOUT_MS = 30000;

    private WebView webView;
    private Handler handler;
    private boolean done = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH, "Background Tasks",
                NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notif = new NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Updating...")
            .setSilent(true)
            .build();
        startForeground(NOTIF_ID, notif);

        if (intent == null) { finish(); return START_NOT_STICKY; }
        String taskId = intent.getStringExtra("taskId");
        String callbackFn = intent.getStringExtra("callbackFn");
        // Optional JSON payload (trigger dispatch passes this; normal tasks don't).
        // If present, it's parsed client-side and passed to the callback instead of the
        // default {taskId,background:true} args.
        final String payloadJson = intent.getStringExtra("payloadJson");

        if (taskId == null || callbackFn == null) { finish(); return START_NOT_STICKY; }
        if (!ShellActivity.isSafeCallbackName(callbackFn)) { finish(); return START_NOT_STICKY; }

        Log.i(TAG, "Running task: " + taskId);
        final TaskService ctx = this;

        handler.post(() -> {
            try {
                webView = new WebView(new android.view.ContextThemeWrapper(this, android.R.style.Theme));
                WebSettings s = webView.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setAllowFileAccess(true);

                // Task control bridge (done signal)
                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void done() {
                        Log.i(TAG, "Task " + taskId + " signaled done");
                        handler.post(() -> finish());
                    }
                }, "_taskControl");

                // Storage bridge (via BridgeUtils)
                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface public void save(String k, String v) { BridgeUtils.save(ctx, k, v); }
                    @JavascriptInterface public String load(String k) { return BridgeUtils.load(ctx, k); }
                    @JavascriptInterface public void remove(String k) { BridgeUtils.remove(ctx, k); }
                    @JavascriptInterface public void clear() { ctx.getSharedPreferences("iappyx_store", MODE_PRIVATE).edit().clear().apply(); }
                }, "iappyxStorage");

                // Widget bridge (via BridgeUtils)
                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface public void update(String json) { BridgeUtils.updateWidget(ctx, json); }
                    @JavascriptInterface public void clear() { BridgeUtils.clearWidget(ctx); }
                    @JavascriptInterface public void onAction(String fn) {} // no-op in background
                }, "iappyxWidget");

                // Notification bridge (via BridgeUtils)
                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface public void send(String title, String body) { BridgeUtils.sendNotification(ctx, title, body); }
                    @JavascriptInterface public void sendWithId(String id, String title, String body) { BridgeUtils.sendNotification(ctx, title, body); }
                    @JavascriptInterface public void cancel(String id) {}
                    @JavascriptInterface public void cancelAll() {}
                }, "iappyxNotification");

                // Device bridge (via BridgeUtils)
                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface public String getPackageName() { return ctx.getPackageName(); }
                    @JavascriptInterface public String getConnectivity() { return BridgeUtils.getConnectivity(ctx); }
                }, "iappyxDevice");

                // Bundled asset files (read-only, from APK assets/app/data/)
                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface public String listAssets() {
                        try {
                            String[] files = ctx.getAssets().list("app/data");
                            if (files == null || files.length == 0) return "[]";
                            org.json.JSONArray arr = new org.json.JSONArray();
                            for (String n : files) {
                                long sz = 0;
                                try {
                                    android.content.res.AssetFileDescriptor afd = ctx.getAssets().openFd("app/data/" + n);
                                    sz = afd.getLength(); afd.close();
                                } catch (Exception e2) {
                                    try {
                                        java.io.InputStream is3 = ctx.getAssets().open("app/data/" + n);
                                        byte[] buf = new byte[8192]; int rd; long tot = 0;
                                        while ((rd = is3.read(buf)) != -1) tot += rd;
                                        is3.close(); sz = tot;
                                    } catch (Exception ignored) {}
                                }
                                org.json.JSONObject o = new org.json.JSONObject();
                                o.put("name", n); o.put("size", sz); arr.put(o);
                            }
                            return arr.toString();
                        } catch (Exception e) { return "[]"; }
                    }
                    @JavascriptInterface public void readAsset(String name, String cbId) {
                        if (name == null || cbId == null) return;
                        new Thread(() -> {
                            try {
                                java.io.InputStream is2 = ctx.getAssets().open("app/data/" + name.replace("/","_").replace("\\","_"));
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                byte[] buf = new byte[8192]; int n2;
                                while ((n2 = is2.read(buf)) != -1) baos.write(buf, 0, n2);
                                is2.close(); byte[] bytes = baos.toByteArray();
                                if (bytes.length > 25 * 1024 * 1024) {
                                    String safeCb3 = ShellActivity.escapeJson(cbId).replace("'", "\\'");
                                    handler.post(() -> { if (webView != null && !done) webView.evaluateJavascript("if(window._iappyxCb&&window._iappyxCb['" + safeCb3 + "']){window._iappyxCb['" + safeCb3 + "']({ok:false,error:'File too large for readAsset (>25 MB). Use extractAsset() instead.'});delete window._iappyxCb['" + safeCb3 + "'];}", null); });
                                    return;
                                }
                                String b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                                org.json.JSONObject r = new org.json.JSONObject();
                                r.put("ok", true); r.put("text", new String(bytes, "UTF-8"));
                                r.put("base64", b64); r.put("size", bytes.length);
                                if (webView != null && !done) {
                                    String safeCb2 = ShellActivity.escapeJson(cbId).replace("'", "\\'");
                                    String js2 = "if(window._iappyxCb&&window._iappyxCb['" + safeCb2 + "']){window._iappyxCb['" + safeCb2 + "'](" + r.toString() + ");delete window._iappyxCb['" + safeCb2 + "'];}";
                                    handler.post(() -> { if (webView != null && !done) webView.evaluateJavascript(js2, null); });
                                }
                            } catch (Exception e) {
                                if (webView != null && !done) {
                                    String safeCb2 = ShellActivity.escapeJson(cbId).replace("'", "\\'");
                                    handler.post(() -> { if (webView != null && !done) webView.evaluateJavascript("if(window._iappyxCb&&window._iappyxCb['" + safeCb2 + "']){window._iappyxCb['" + safeCb2 + "']({ok:false,error:'" + ShellActivity.escapeJson(e.getMessage()) + "'});delete window._iappyxCb['" + safeCb2 + "'];}", null); });
                                }
                            }
                        }).start();
                    }
                    @JavascriptInterface public void extractAsset(String name, String destName, String cbId) {
                        if (name == null || destName == null || cbId == null) return;
                        new Thread(() -> {
                            try {
                                java.io.File dest = new java.io.File(ctx.getFilesDir(), destName.replace("/","_").replace("\\","_"));
                                java.io.InputStream is2 = ctx.getAssets().open("app/data/" + name.replace("/","_").replace("\\","_"));
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                byte[] buf = new byte[8192]; int n2;
                                while ((n2 = is2.read(buf)) != -1) baos.write(buf, 0, n2);
                                is2.close();
                                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) { fos.write(baos.toByteArray()); }
                                String safeCb2 = ShellActivity.escapeJson(cbId).replace("'", "\\'");
                                String js2 = "if(window._iappyxCb&&window._iappyxCb['" + safeCb2 + "']){window._iappyxCb['" + safeCb2 + "']({ok:true,path:'" + ShellActivity.escapeJson(dest.getAbsolutePath()) + "'});delete window._iappyxCb['" + safeCb2 + "'];}";
                                handler.post(() -> { if (webView != null && !done) webView.evaluateJavascript(js2, null); });
                            } catch (Exception e) {
                                String safeCb2 = ShellActivity.escapeJson(cbId).replace("'", "\\'");
                                handler.post(() -> { if (webView != null && !done) webView.evaluateJavascript("if(window._iappyxCb&&window._iappyxCb['" + safeCb2 + "']){window._iappyxCb['" + safeCb2 + "']({ok:false,error:'" + ShellActivity.escapeJson(e.getMessage()) + "'});delete window._iappyxCb['" + safeCb2 + "'];}", null); });
                            }
                        }).start();
                    }
                }, "iappyxAssets");

                // Intent bridge — launch other apps / URIs from headless callbacks
                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface public boolean launchApp(String pkg) {
                        if (pkg == null || pkg.isEmpty()) return false;
                        try {
                            Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
                            if (i == null) return false;
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            ctx.getApplicationContext().startActivity(i);
                            return true;
                        } catch (Exception e) {
                            Log.w(TAG, "launchApp(" + pkg + "): " + e.getMessage());
                            return false;
                        }
                    }
                    @JavascriptInterface public boolean openUrl(String url) {
                        if (url == null || url.isEmpty()) return false;
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            ctx.getApplicationContext().startActivity(i);
                            return true;
                        } catch (Exception e) { return false; }
                    }
                    @JavascriptInterface public boolean isAppInstalled(String pkg) {
                        if (pkg == null || pkg.isEmpty()) return false;
                        try { ctx.getPackageManager().getPackageInfo(pkg, 0); return true; }
                        catch (Exception e) { return false; }
                    }
                    @JavascriptInterface public boolean hasOverlayPermission() {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
                        return android.provider.Settings.canDrawOverlays(ctx);
                    }
                    @JavascriptInterface public void requestOverlayPermission() {
                        // Can't open Settings from a background service — foreground-only.
                    }
                }, "iappyxIntent");

                // HTTP Client bridge (via BridgeUtils)
                webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void request(String optionsJson, String cbId) {
                        new Thread(() -> BridgeUtils.httpRequest(optionsJson, result -> {
                            if (done) return;
                            handler.post(() -> {
                                if (webView != null && !done) {
                                    String safeCb = ShellActivity.escapeJson(cbId).replace("'", "\\'");
                                    String js = "if(window._iappyxCb&&window._iappyxCb['" + safeCb + "']){" +
                                        "window._iappyxCb['" + safeCb + "'](" + result + ");" +
                                        "delete window._iappyxCb['" + safeCb + "'];}";
                                    webView.evaluateJavascript(js, null);
                                }
                            });
                        })).start();
                    }
                }, "iappyxHttpClient");

                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView v, String url) {
                        // Inject the iappyx wrapper (same structure as ShellActivity)
                        v.evaluateJavascript(
                            "window.iappyx={" +
                            "storage:iappyxStorage," +
                            "widget:iappyxWidget," +
                            "notification:iappyxNotification," +
                            "device:iappyxDevice," +
                            "intent:iappyxIntent," +
                            "assets:iappyxAssets," +
                            "httpClient:iappyxHttpClient," +
                            "save:function(k,v){iappyxStorage.save(k,v)}," +
                            "load:function(k){return iappyxStorage.load(k)}," +
                            "remove:function(k){iappyxStorage.remove(k)}," +
                            "getPackageName:function(){return iappyxDevice.getPackageName()}" +
                            "};window._taskDone=function(){_taskControl.done()};", null);

                        // Wait for bridge, then call the task function
                        final String fn = callbackFn;
                        final String tid = taskId;
                        final int[] attempt = {0};
                        final Runnable[] retry = new Runnable[1];
                        retry[0] = () -> {
                            if (done) return;
                            v.evaluateJavascript("typeof iappyx", result -> {
                                if (!"\"undefined\"".equals(result)) {
                                    // If a payload JSON was supplied (trigger dispatch), pass it verbatim;
                                    // otherwise use the default {taskId,background:true} task args.
                                    String argsJs = (payloadJson != null && !payloadJson.isEmpty())
                                        ? payloadJson
                                        : "{taskId:'" + ShellActivity.escapeJson(tid) + "',background:true}";
                                    v.evaluateJavascript(
                                        "try{" + fn + "(" + argsJs + ")}catch(e){window._taskDone()}", null);
                                } else if (attempt[0]++ < 10) {
                                    handler.postDelayed(retry[0], 300);
                                } else {
                                    Log.w(TAG, "Bridge not ready for task " + tid);
                                    finish();
                                }
                            });
                        };
                        handler.postDelayed(retry[0], 200);
                    }
                });

                webView.loadUrl("file:///android_asset/app/index.html");

                // Timeout
                handler.postDelayed(() -> {
                    if (!done) {
                        Log.w(TAG, "Task " + taskId + " timed out");
                        finish();
                    }
                }, TIMEOUT_MS);

            } catch (Exception e) {
                Log.e(TAG, "Task error: " + e.getMessage());
                finish();
            }
        });

        return START_NOT_STICKY;
    }

    private void finish() {
        if (done) return;
        done = true;
        handler.post(() -> {
            if (webView != null) {
                webView.stopLoading();
                webView.destroy();
                webView = null;
            }
            stopForeground(true);
            stopSelf();
        });
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        done = true;
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
