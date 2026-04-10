package com.iappyx.generated.placeholder;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.biometrics.BiometricPrompt;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
// MediaPlayer replaced by ExoPlayer (Media3)
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShellActivity extends Activity {

    private WebView webView;
    private android.widget.ProgressBar progressBar;
    private android.widget.TextView offlineBanner;
    private String lastFailedUrl;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false; // Bug #6: track TTS init
    private SensorManager sensorManager;
    private final java.util.Map<Integer, SensorEventListener> activeSensors = new java.util.HashMap<>();
    private androidx.media3.exoplayer.ExoPlayer exoPlayer;
    private android.media.audiofx.Visualizer audioVisualizer;
    private PowerManager.WakeLock wakeLock;
    private NfcAdapter nfcAdapter;
    private String pendingNfcCallbackFn;
    private String pendingNfcReadCallbackFn;
    private String pendingStepCallbackFn;
    private String pendingNfcWriteText;
    private String pendingNfcWriteUri;
    private String pendingNfcWriteCbId;
    private String pendingSmsNumber;
    private String[] pendingCalendarEvent; // title, startMs, endMs, description
    private String pendingSmsMessage;
    private volatile boolean activityAlive = true;
    private android.database.sqlite.SQLiteDatabase sqliteDb;

    // NSD bridge state
    private android.net.nsd.NsdManager nsdManager;
    private android.net.nsd.NsdManager.RegistrationListener nsdRegistrationListener;
    private android.net.nsd.NsdManager.DiscoveryListener nsdDiscoveryListener;
    private String nsdDiscoveryCallbackFn;
    private final java.util.LinkedList<Runnable> nsdResolveQueue = new java.util.LinkedList<>();
    private volatile boolean nsdResolving = false;
    private android.net.wifi.WifiManager.MulticastLock multicastLock;

    // HTTP Server bridge state
    private volatile boolean httpServerRunning = false;
    private volatile java.net.ServerSocket httpPlainServerSocket;
    private volatile javax.net.ssl.SSLServerSocket httpSslServerSocket;
    private Thread httpServerThread;
    private java.util.concurrent.ExecutorService httpServerPool;
    private final java.util.concurrent.ExecutorService httpClientPool =
        java.util.concurrent.Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
    private final java.util.concurrent.ConcurrentHashMap<String, HttpPendingRequest> httpPendingRequests = new java.util.concurrent.ConcurrentHashMap<>();
    private String httpRequestCallbackFn;
    private java.security.cert.X509Certificate httpSelfSignedCert;

    // WiFi Direct bridge state
    private android.net.wifi.p2p.WifiP2pManager wifiP2pManager;
    private android.net.wifi.p2p.WifiP2pManager.Channel wifiP2pChannel;
    private BroadcastReceiver wifiP2pReceiver;
    private String wifiP2pPeerCallbackFn;
    private String wifiP2pConnectionCallbackFn;
    private Runnable wifiP2pPendingAction;
    private String wifiP2pPendingCbId;

    private static class HttpPendingRequest {
        final java.net.Socket socket;
        final java.io.OutputStream outputStream;
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        volatile int statusCode;
        volatile String responseHeaders;
        volatile String responseBody;
        volatile String responseFilePath;
        HttpPendingRequest(java.net.Socket s, java.io.OutputStream os) { this.socket = s; this.outputStream = os; }
    }

    private static final int REQ_CAMERA         = 1001;
    private static final int REQ_LOCATION       = 1002;
    private static final int REQ_CONTACTS       = 1003;
    private static final int REQ_SMS            = 1004;
    private static final int REQ_CALENDAR_READ  = 1005;
    private static final int REQ_CALENDAR_WRITE = 1006;
    private static final int REQ_BIOMETRIC      = 1007;
    private static final int REQ_NOTIFICATION   = 1008;
    private static final int REQ_ACTIVITY_RECOG = 1009;
    private static final int REQ_AUDIO_RECORD   = 1010;
    private static final int REQ_CAMERA_PHOTO   = 2001;
    private static final int REQ_FILE_PICKER    = 2002;
    private static final int REQ_CAMERA_VIDEO   = 2005;
    private static final int REQ_QR_SCAN        = 2006;
    private static final int REQ_OCR_SCAN       = 2007;
    private static final int REQ_SPEECH         = 2008;
    private static final int REQ_MEDIA_STREAM   = 2009;
    private static final int REQ_CLASSIFY       = 2010;
    private static final int REQ_SEGMENT        = 2011;
    private static final int REQ_MEDIA_PICK     = 2012;
    private static final int REQ_WIFI_DIRECT    = 1011;
    private static final int REQ_PICK_FILE      = 2013;
    private String pendingPickFileCbId;

    private final Map<Integer, String> pendingCallbacks = new HashMap<>();
    private Runnable pendingCameraAction;
    private String pendingPhotoCallbackId;
    private Uri pendingPhotoUri;
    private String pendingVideoCallbackId;
    private Uri pendingVideoUri;
    private String pendingQrCallbackId;
    private String pendingOcrCallbackId;
    private String pendingSpeechCallbackId;
    private String pendingClassifyCallbackId;
    private String pendingSegmentCallbackId;
    private String pendingMediaCbId;
    private BroadcastReceiver locationUpdateReceiver;
    private BroadcastReceiver mediaButtonReceiver;
    private BroadcastReceiver mediaMetadataReceiver;
    private PermissionRequest pendingWebPermission;
    private android.media.MediaRecorder mediaRecorder;
    private File recordingFile;
    private String recordingCallbackId;
    private String audioCompleteCallbackFn;
    private String audioMetadataCallbackFn;
    private String lastAudioUrl;
    private final java.util.List<androidx.media3.exoplayer.ExoPlayer> soundPlayers = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private LocationListener sharedGeofenceListener;
    private boolean mediaSessionActive = false;
    private String pendingSessionTitle, pendingSessionArtist, pendingSessionAlbum;
    private ClipboardManager.OnPrimaryClipChangedListener activeClipListener;
    private String watchPositionErrorFn; // for location error callback
    private ValueCallback<Uri[]> pendingFileCallback;

    // Bug #15: PendingIntent flags helper
    private static int piFlags(int flags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return flags | PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Status/nav bar styling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xFF0D0D1A);
            getWindow().setNavigationBarColor(0xFF0D0D1A);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Light status bar icons (white icons on dark background)
            getWindow().getDecorView().setSystemUiVisibility(0);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Layout: WebView with ProgressBar + offline banner overlay
        webView = new WebView(this);

        progressBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(android.view.View.GONE);
        progressBar.setIndeterminate(false);
        progressBar.getProgressDrawable().setColorFilter(0xFF4FC3F7, android.graphics.PorterDuff.Mode.SRC_IN);

        offlineBanner = new android.widget.TextView(this);
        offlineBanner.setText("\u26A0 Offline \u2014 tap to retry");
        offlineBanner.setTextColor(0xFFFFFFFF);
        offlineBanner.setBackgroundColor(0xFFFF6B6B);
        offlineBanner.setGravity(android.view.Gravity.CENTER);
        offlineBanner.setPadding(16, 12, 16, 12);
        offlineBanner.setTextSize(13);
        offlineBanner.setVisibility(android.view.View.GONE);
        offlineBanner.setOnClickListener(v -> { offlineBanner.setVisibility(android.view.View.GONE); webView.reload(); });

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.addView(webView, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(progressBar, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, 6,
            android.view.Gravity.TOP));
        root.addView(offlineBanner, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP));

        setContentView(root);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true); // needed for file:///android_asset/ loading

        webView.addJavascriptInterface(new StorageBridge(),      "iappyxStorage");
        webView.addJavascriptInterface(new DeviceBridge(),       "iappyxDevice");
        webView.addJavascriptInterface(new CameraBridge(),       "iappyxCamera");
        webView.addJavascriptInterface(new LocationBridge(),     "iappyxLocation");
        webView.addJavascriptInterface(new NotificationBridge(), "iappyxNotification");
        webView.addJavascriptInterface(new VibrationBridge(),    "iappyxVibration");
        webView.addJavascriptInterface(new ClipboardBridge(),    "iappyxClipboard");
        webView.addJavascriptInterface(new SensorBridge(),       "iappyxSensor");
        webView.addJavascriptInterface(new TtsBridge(),          "iappyxTts");
        webView.addJavascriptInterface(new AlarmBridge(),        "iappyxAlarm");
        webView.addJavascriptInterface(new AudioBridge(),        "iappyxAudio");
        webView.addJavascriptInterface(new ScreenBridge(),       "iappyxScreen");
        webView.addJavascriptInterface(new ContactsBridge(),     "iappyxContacts");
        webView.addJavascriptInterface(new SmsBridge(),          "iappyxSms");
        webView.addJavascriptInterface(new CalendarBridge(),     "iappyxCalendar");
        webView.addJavascriptInterface(new BiometricBridge(),    "iappyxBiometric");
        webView.addJavascriptInterface(new NfcBridge(),          "iappyxNfc");
        webView.addJavascriptInterface(new SqliteBridge(),       "iappyxSqlite");
        webView.addJavascriptInterface(new DownloadBridge(),     "iappyxDownload");
        webView.addJavascriptInterface(new MediaBridge(),       "iappyxMedia");
        webView.addJavascriptInterface(new HttpClientBridge(),   "iappyxHttpClient");
        webView.addJavascriptInterface(new SshBridge(),          "iappyxSsh");
        webView.addJavascriptInterface(new SmbBridge(),          "iappyxSmb");
        webView.addJavascriptInterface(new TcpBridge(),          "iappyxTcp");
        webView.addJavascriptInterface(new UdpBridge(),          "iappyxUdp");
        webView.addJavascriptInterface(new BleBridge(),           "iappyxBle");
        webView.addJavascriptInterface(new PushBridge(),         "iappyxPush");
        webView.addJavascriptInterface(new WifiDirectBridge(),   "iappyxWifiDirect");
        webView.addJavascriptInterface(new NsdBridge(),          "iappyxNsd");
        webView.addJavascriptInterface(new HttpServerBridge(),   "iappyxHttpServer");
        webView.addJavascriptInterface(new CapabilitiesBridge(),"iappyxCapabilities");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest r) {
                // Check which Android runtime permissions are needed
                java.util.List<String> needed = new java.util.ArrayList<>();
                for (String res : r.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) &&
                        ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.CAMERA)
                            != PackageManager.PERMISSION_GRANTED) {
                        needed.add(Manifest.permission.CAMERA);
                    }
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) &&
                        ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                        needed.add(Manifest.permission.RECORD_AUDIO);
                    }
                }
                if (needed.isEmpty()) {
                    r.grant(r.getResources());
                } else {
                    pendingWebPermission = r;
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        needed.toArray(new String[0]), REQ_MEDIA_STREAM);
                }
            }
            @Override public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(android.view.View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(android.view.View.GONE);
    
                }
            }
            @Override public boolean onShowFileChooser(WebView wv,
                    ValueCallback<Uri[]> filePathCallback, FileChooserParams params) {
                if (pendingFileCallback != null) pendingFileCallback.onReceiveValue(null);
                pendingFileCallback = filePathCallback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, REQ_FILE_PICKER);
                } catch (Exception e) {
                    pendingFileCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if (scheme == null) return false;
                if (scheme.equals("http") || scheme.equals("https") || scheme.equals("file")) return false;
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme();
                if (scheme == null) return false;
                if (scheme.equals("http") || scheme.equals("https") || scheme.equals("file")) return false;
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                lastFailedUrl = failingUrl;

                progressBar.setVisibility(android.view.View.GONE);
                String safeDesc = description != null ? description.replace("'", "\\'") : "Unknown error";
                view.loadData(
                    "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
                    "<body style='background:#0d0d1a;color:#eaeaea;font-family:-apple-system,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center;padding:20px;margin:0'>" +
                    "<div>" +
                    "<div style='font-size:48px;margin-bottom:16px'>\uD83D\uDCF6</div>" +
                    "<h2 style='margin:0 0 8px;font-size:18px'>Can't reach this page</h2>" +
                    "<p style='color:rgba(255,255,255,0.4);font-size:13px;margin:0 0 24px'>" + safeDesc + "</p>" +
                    "<button onclick='window.location.href=\"" + escapeJson(failingUrl).replace("'", "\\'") + "\"' " +
                    "style='background:#0f3460;color:#fff;border:none;padding:14px 32px;border-radius:50px;font-size:15px;margin:0 6px;cursor:pointer;min-height:44px'>Retry</button>" +
                    "<button onclick='history.back()' " +
                    "style='background:#1a1a2e;color:#fff;border:none;padding:14px 24px;border-radius:50px;font-size:15px;margin:0 6px;cursor:pointer;min-height:44px'>Back</button>" +
                    "</div></body></html>",
                    "text/html", "UTF-8");
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                offlineBanner.setVisibility(android.view.View.GONE);
                progressBar.setVisibility(android.view.View.VISIBLE);
            }

            @Override public void onPageFinished(WebView v, String url) {

                progressBar.setVisibility(android.view.View.GONE);
                v.evaluateJavascript(
                    "window.iappyx={" +
                    "storage:iappyxStorage,device:iappyxDevice,camera:iappyxCamera," +
                    "location:iappyxLocation,notification:iappyxNotification," +
                    "vibration:iappyxVibration,clipboard:iappyxClipboard," +
                    "sensor:iappyxSensor,tts:iappyxTts," +
                    "alarm:iappyxAlarm,audio:iappyxAudio,screen:iappyxScreen," +
                    "contacts:iappyxContacts,sms:iappyxSms,calendar:iappyxCalendar," +
                    "biometric:iappyxBiometric," +
                    "nfc:iappyxNfc,sqlite:iappyxSqlite,download:iappyxDownload,media:iappyxMedia," +
                    "httpServer:iappyxHttpServer,httpClient:iappyxHttpClient,ssh:iappyxSsh,smb:iappyxSmb,ble:iappyxBle,push:iappyxPush,tcp:iappyxTcp,nsd:iappyxNsd,udp:iappyxUdp,wifiDirect:iappyxWifiDirect," +
                    "save:function(k,v){iappyxStorage.save(k,v)}," +
                    "load:function(k){return iappyxStorage.load(k)}," +
                    "remove:function(k){iappyxStorage.remove(k)}," +
                    "getPackageName:function(){return iappyxDevice.getPackageName()}," +
                    "getAppName:function(){return iappyxDevice.getAppName()}," +
                    "sharePhoto:function(b){iappyxCamera.sharePhoto(b)}," +
                    "shareText:function(t,s){iappyxCamera.shareText(t,s||'')}," +
                    "capabilities:function(){return JSON.parse(iappyxCapabilities.get())}," +
                    "onTextSelected:function(fn){document.addEventListener('selectionchange',function(){" +
                    "var s=window.getSelection();if(s&&s.toString().trim().length>0){fn({text:s.toString().trim()})}" +
                    "})}" +
                    "};", null);
                // Check if launched by alarm (cold start)
                Intent launchIntent = getIntent();
                if (launchIntent != null && launchIntent.getBooleanExtra("alarm_fired", false)) {
                    String cbFn = launchIntent.getStringExtra("callbackFn");
                    if (cbFn != null && isSafeCallbackName(cbFn)) {
                        // Retry up to 3 times in case JS isn't ready yet
                        final String fn = cbFn;
                        final int[] attempt = {0};
                        final Runnable[] retry = new Runnable[1];
                        retry[0] = () -> {
                            if (!activityAlive) return;
                            v.evaluateJavascript("typeof " + fn, result -> {
                                if ("\"function\"".equals(result)) {
                                    v.evaluateJavascript(fn + "({})", null);
                                } else if (attempt[0]++ < 3) {
                                    v.postDelayed(retry[0], 500);
                                }
                            });
                        };
                        v.postDelayed(retry[0], 500);
                        launchIntent.removeExtra("alarm_fired");
                    }
                }
                // Check if launched by shortcut (cold start)
                if (launchIntent != null && launchIntent.hasExtra("shortcut_id")) {
                    String shortcutId = launchIntent.getStringExtra("shortcut_id");
                    String sCb = getSharedPreferences("iappyx_shortcuts", MODE_PRIVATE)
                        .getString("callback_" + shortcutId, "window.onShortcut");
                    v.postDelayed(() -> {
                        if (activityAlive) fireEvent(sCb, "{\"shortcutId\":\"" + escapeJson(shortcutId) + "\"}");
                    }, 500);
                    launchIntent.removeExtra("shortcut_id");
                }
                // Check if launched by share (cold start)
                if (launchIntent != null) handleShareIntent(launchIntent);
            }
        });

        webView.loadUrl("file:///android_asset/app/index.html");

        // Register broadcast receiver for foreground location updates
        locationUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!activityAlive) return;
                android.content.SharedPreferences prefs =
                    getSharedPreferences("iappyx_location", MODE_PRIVATE);
                String json = prefs.getString("latest", null);
                String fn = prefs.getString("callbackFn", null);
                if (json != null && fn != null) {
                    fireEvent(fn, json);
                }
            }
        };
        registerReceiver(locationUpdateReceiver,
            new android.content.IntentFilter("com.iappyx.LOCATION_UPDATE"),
            Context.RECEIVER_NOT_EXPORTED);

        // Register broadcast receiver for media button events from AudioService
        mediaButtonReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!activityAlive) return;
                String action = intent.getStringExtra("action");
                if (action != null) {
                    fireEvent("window.onMediaButton", "{\"action\":\"" + action + "\"}");
                    // Also fire onComplete callback for AudioService completion
                    if ("complete".equals(action) && audioCompleteCallbackFn != null) {
                        fireEvent(audioCompleteCallbackFn, "{\"done\":true}");
                    }
                }
            }
        };
        registerReceiver(mediaButtonReceiver,
            new android.content.IntentFilter("com.iappyx.MEDIA_BUTTON"),
            Context.RECEIVER_NOT_EXPORTED);

        // Register receiver for stream metadata from AudioService
        mediaMetadataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!activityAlive || audioMetadataCallbackFn == null) return;
                String title = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                String album = intent.getStringExtra("album");
                String station = intent.getStringExtra("station");
                String genre = intent.getStringExtra("genre");
                fireEvent(audioMetadataCallbackFn, "{\"title\":\"" + escapeJson(title) +
                    "\",\"artist\":\"" + escapeJson(artist) +
                    "\",\"album\":\"" + escapeJson(album) +
                    "\",\"station\":\"" + escapeJson(station) +
                    "\",\"genre\":\"" + escapeJson(genre) + "\"}");
            }
        };
        registerReceiver(mediaMetadataReceiver,
            new android.content.IntentFilter("com.iappyx.MEDIA_METADATA"),
            Context.RECEIVER_NOT_EXPORTED);

        // Bug #6: track TTS init status
        tts = new TextToSpeech(this, status -> {
            ttsReady = (status == TextToSpeech.SUCCESS);
        });
    }

    private Bitmap fixOrientation(Bitmap bmp, Uri photoUri) {
        try {
            if (photoUri == null) return bmp;
            java.io.InputStream in = getContentResolver().openInputStream(photoUri);
            if (in == null) return bmp;
            androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(in);
            in.close();
            int orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);
            int degrees = 0;
            switch (orientation) {
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90: degrees = 90; break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
            }
            if (degrees == 0) return bmp;
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(degrees);
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
            if (rotated != bmp) bmp.recycle();
            return rotated;
        } catch (Exception e) { return bmp; }
    }

    /** Validates that a callback function name is safe to inject into evaluateJavascript. */
    static boolean isSafeCallbackName(String fn) {
        if (fn == null || fn.isEmpty()) return false;
        return fn.matches("[a-zA-Z_$][a-zA-Z0-9_$.]*");
    }

    static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    void deliverResult(String cbId, String json) {
        if (!activityAlive || cbId == null) return;
        final String js = "if(window._iappyxCb&&window._iappyxCb['" + cbId + "']){" +
            "window._iappyxCb['" + cbId + "'](" + json + ");" +
            "delete window._iappyxCb['" + cbId + "'];}";
        runOnUiThread(() -> {
            if (activityAlive) webView.evaluateJavascript(js, null);
        });
    }

    void fireEvent(String fn, String json) {
        if (!activityAlive) return;
        runOnUiThread(() -> {
            if (activityAlive) webView.evaluateJavascript(
                "if(typeof " + fn + "==='function')" + fn + "(" + json + ");", null);
        });
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        activityAlive = false;
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
        if (audioVisualizer != null) { try { audioVisualizer.setEnabled(false); audioVisualizer.release(); } catch (Exception ignored) {} audioVisualizer = null; }
        // Note: equalizer is inside AudioBridge inner class — released when activity dies
        // Stop any audio playing in WebView (HTML <audio> tags, Web Audio API)
        if (webView != null) {
            webView.loadUrl("about:blank");
        }
        // Stop audio service if still running
        if (mediaSessionActive) {
            stopService(new Intent(this, AudioService.class));
            mediaSessionActive = false;
        }
        // Release overlay sound players
        for (androidx.media3.exoplayer.ExoPlayer sp : new java.util.ArrayList<>(soundPlayers)) {
            try { sp.release(); } catch (Exception ignored) {}
        }
        soundPlayers.clear();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopSensors();
        // Stop location listener
        if (activeLocationListener != null) {
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                lm.removeUpdates(activeLocationListener);
            } catch (Exception ignored) {}
            activeLocationListener = null;
        }
        // Remove geofence listener
        if (sharedGeofenceListener != null) {
            LocationManager geoLm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (geoLm != null) try { geoLm.removeUpdates(sharedGeofenceListener); } catch (Exception ignored) {}
            sharedGeofenceListener = null;
        }
        // Unregister broadcast receivers
        try { if (locationUpdateReceiver != null) unregisterReceiver(locationUpdateReceiver); } catch (Exception ignored) {}
        try { if (mediaButtonReceiver != null) unregisterReceiver(mediaButtonReceiver); } catch (Exception ignored) {}
        try { if (mediaMetadataReceiver != null) unregisterReceiver(mediaMetadataReceiver); } catch (Exception ignored) {}
        // Close SQLite database
        if (sqliteDb != null && sqliteDb.isOpen()) {
            try { sqliteDb.close(); } catch (Exception ignored) {}
        }
        // Stop SSH
        disconnectSsh();
        // Clear push references
        PushService.activeActivity = null;
        // Stop BLE
        disconnectAllBle();
        // Stop SMB
        disconnectSmb();
        // Stop TCP socket
        boolean tcpWasRunning = tcpRunning;
        tcpRunning = false;
        if (tcpSocket != null && !tcpSocket.isClosed()) {
            try { tcpSocket.close(); } catch (Exception ignored) {}
        }
        tcpSocket = null; tcpOut = null;
        if (tcpWasRunning) NetworkService.requestStop(this);
        // Stop HTTP client pool
        httpClientPool.shutdownNow();
        // Stop UDP
        udpRunning = false;
        if (udpSocket != null && !udpSocket.isClosed()) {
            try { udpSocket.close(); } catch (Exception ignored) {}
        }
        udpSocket = null;
        // Stop WiFi Direct
        if (wifiP2pReceiver != null) {
            try { unregisterReceiver(wifiP2pReceiver); } catch (Exception ignored) {}
            wifiP2pReceiver = null;
        }
        if (wifiP2pManager != null && wifiP2pChannel != null) {
            try { wifiP2pManager.removeGroup(wifiP2pChannel, null); } catch (Exception ignored) {}
        }
        wifiP2pManager = null; wifiP2pChannel = null;
        // Stop HTTP server
        if (httpServerRunning) stopHttpServer();
        // Stop NSD
        if (nsdManager != null) {
            if (nsdRegistrationListener != null) {
                try { nsdManager.unregisterService(nsdRegistrationListener); } catch (Exception ignored) {}
                nsdRegistrationListener = null;
            }
            if (nsdDiscoveryListener != null) {
                try { nsdManager.stopServiceDiscovery(nsdDiscoveryListener); } catch (Exception ignored) {}
                nsdDiscoveryListener = null;
            }
        }
        releaseMulticastLock();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Handle push notification tap
        if (intent.hasExtra("push_data") && PushService.foregroundCallbackFn != null) {
            String title = intent.getStringExtra("push_title");
            String body = intent.getStringExtra("push_body");
            String data = intent.getStringExtra("push_data");
            String safeData = "{}";
            if (data != null) { try { new JSONObject(data); safeData = data; } catch (Exception e) { safeData = "\"" + escapeJson(data) + "\""; } }
            fireEvent(PushService.foregroundCallbackFn, "{\"title\":\"" + escapeJson(title) +
                "\",\"body\":\"" + escapeJson(body) + "\",\"data\":" + safeData + "}");
            intent.removeExtra("push_data");
        }
        String action = intent.getAction() != null ? intent.getAction() : "";
        // Handle NFC tag discovered (any NFC action, or check for tag extra)
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag != null || NfcAdapter.ACTION_TAG_DISCOVERED.equals(action) ||
            NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action) ||
            NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            if (tag == null) tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            // Handle pending NFC write
            if (tag != null && (pendingNfcWriteText != null || pendingNfcWriteUri != null)) {
                try {
                    android.nfc.tech.Ndef ndef = android.nfc.tech.Ndef.get(tag);
                    NdefMessage msg;
                    if (pendingNfcWriteText != null) {
                        byte[] langBytes = "en".getBytes("US-ASCII");
                        byte[] textBytes = pendingNfcWriteText.getBytes("UTF-8");
                        byte[] payload = new byte[1 + langBytes.length + textBytes.length];
                        payload[0] = (byte) langBytes.length;
                        System.arraycopy(langBytes, 0, payload, 1, langBytes.length);
                        System.arraycopy(textBytes, 0, payload, 1 + langBytes.length, textBytes.length);
                        msg = new NdefMessage(new NdefRecord[]{
                            new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload)
                        });
                    } else {
                        msg = new NdefMessage(new NdefRecord[]{
                            NdefRecord.createUri(pendingNfcWriteUri)
                        });
                    }
                    if (ndef != null) {
                        ndef.connect();
                        ndef.writeNdefMessage(msg);
                        ndef.close();
                        if (pendingNfcWriteCbId != null)
                            deliverResult(pendingNfcWriteCbId, "{\"ok\":true}");
                    } else {
                        // Try to format
                        android.nfc.tech.NdefFormatable formatable = android.nfc.tech.NdefFormatable.get(tag);
                        if (formatable != null) {
                            formatable.connect();
                            formatable.format(msg);
                            formatable.close();
                            if (pendingNfcWriteCbId != null)
                                deliverResult(pendingNfcWriteCbId, "{\"ok\":true}");
                        } else {
                            if (pendingNfcWriteCbId != null)
                                deliverResult(pendingNfcWriteCbId, "{\"ok\":false,\"error\":\"Tag not NDEF compatible\"}");
                        }
                    }
                } catch (Exception e) {
                    if (pendingNfcWriteCbId != null)
                        deliverResult(pendingNfcWriteCbId, "{\"ok\":false,\"error\":\"Write failed: " + escapeJson(e.getMessage()) + "\"}");
                }
                pendingNfcWriteText = null;
                pendingNfcWriteUri = null;
                pendingNfcWriteCbId = null;
                // Restore read callback if it was active
                if (pendingNfcReadCallbackFn != null) {
                    pendingNfcCallbackFn = pendingNfcReadCallbackFn;
                    pendingNfcReadCallbackFn = null;
                }
                return;
            }
            if (tag != null && pendingNfcCallbackFn != null) {
                try {
                    // Tag ID
                    byte[] id = tag.getId();
                    String hex = "";
                    if (id != null) {
                        StringBuilder sb = new StringBuilder();
                        for (byte b : id) sb.append(String.format("%02X", b));
                        hex = sb.toString();
                    }

                    // Tech list
                    String[] techList = tag.getTechList();
                    JSONArray techArr = new JSONArray();
                    if (techList != null) {
                        for (String t : techList) techArr.put(t.replace("android.nfc.tech.", ""));
                    }

                    // NDEF records
                    JSONArray records = new JSONArray();
                    android.os.Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                    if (rawMsgs != null) {
                        for (android.os.Parcelable rawMsg : rawMsgs) {
                            NdefMessage msg = (NdefMessage) rawMsg;
                            for (NdefRecord rec : msg.getRecords()) {
                                JSONObject r = new JSONObject();
                                r.put("tnf", rec.getTnf());
                                r.put("type", new String(rec.getType(), "UTF-8"));

                                byte[] payload = rec.getPayload();
                                // Decode based on TNF + type
                                if (payload != null && payload.length > 0 && rec.getTnf() == NdefRecord.TNF_WELL_KNOWN) {
                                    if (java.util.Arrays.equals(rec.getType(), NdefRecord.RTD_TEXT) && payload.length >= 3) {
                                        // Text record: first byte = status (bit 7 = encoding, bits 5-0 = lang length)
                                        int langLen = payload[0] & 0x3F;
                                        if (1 + langLen < payload.length) {
                                            String lang = new String(payload, 1, langLen, "US-ASCII");
                                            String text = new String(payload, 1 + langLen, payload.length - 1 - langLen,
                                                (payload[0] & 0x80) == 0 ? "UTF-8" : "UTF-16");
                                            r.put("text", text);
                                            r.put("lang", lang);
                                        }
                                    } else if (java.util.Arrays.equals(rec.getType(), NdefRecord.RTD_URI) && payload.length >= 2) {
                                        // URI record: first byte = prefix code
                                        String[] prefixes = {"", "http://www.", "https://www.", "http://", "https://",
                                            "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://",
                                            "sftp://", "smb://", "nfs://", "ftp://", "dav://", "news:", "telnet://",
                                            "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://",
                                            "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://",
                                            "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:",
                                            "urn:epc:", "urn:nfc:"};
                                        int code = payload[0] & 0xFF;
                                        String prefix = code < prefixes.length ? prefixes[code] : "";
                                        String uri = prefix + new String(payload, 1, payload.length - 1, "UTF-8");
                                        r.put("uri", uri);
                                    }
                                } else if (rec.getTnf() == NdefRecord.TNF_ABSOLUTE_URI) {
                                    r.put("uri", new String(rec.getType(), "UTF-8"));
                                }

                                // Raw payload as hex
                                if (payload == null) payload = new byte[0];
                                StringBuilder payHex = new StringBuilder();
                                for (byte b : payload) payHex.append(String.format("%02X", b));
                                r.put("payloadHex", payHex.toString());

                                records.put(r);
                            }
                        }
                    }

                    JSONObject obj = new JSONObject();
                    obj.put("id", hex);
                    obj.put("tech", techArr);
                    obj.put("records", records);
                    fireEvent(pendingNfcCallbackFn, obj.toString());
                } catch (Exception e) {
                    Log.e("iappyxOS", "NFC json error: " + e.getMessage());
                }
            }
        }
        // Handle alarm callback
        if (intent.getBooleanExtra("alarm_fired", false)) {
            String callbackFn = intent.getStringExtra("callbackFn");
            if (callbackFn != null && isSafeCallbackName(callbackFn)) {
                webView.postDelayed(() -> fireEvent(callbackFn, "{}"), 500);
            }
        }

        // Handle app shortcut
        if (intent.hasExtra("shortcut_id")) {
            String shortcutId = intent.getStringExtra("shortcut_id");
            String rawFn = getSharedPreferences("iappyx_shortcuts", MODE_PRIVATE)
                .getString("callback_" + shortcutId, "window.onShortcut");
            String callbackFn = isSafeCallbackName(rawFn) ? rawFn : "window.onShortcut";
            webView.postDelayed(() -> {
                if (activityAlive) fireEvent(callbackFn, "{\"shortcutId\":\"" + escapeJson(shortcutId) + "\"}");
            }, 500);
        }

        // Handle notification action
        if (intent.getBooleanExtra("notification_action", false)) {
            android.content.SharedPreferences prefs = getSharedPreferences("iappyx_notif_action", MODE_PRIVATE);
            String actionId = prefs.getString("pending_actionId", null);
            String notifId = prefs.getString("pending_notificationId", "");
            String callbackFn = prefs.getString("pending_callbackFn", null);
            if (actionId != null && callbackFn != null) {
                prefs.edit().clear().apply();
                webView.postDelayed(() -> {
                    if (activityAlive) fireEvent(callbackFn,
                        "{\"actionId\":\"" + escapeJson(actionId) + "\",\"notificationId\":\"" + escapeJson(notifId) + "\"}");
                }, 500);
            }
        }

        // Handle share target (text or image shared from another app)
        handleShareIntent(intent);
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String callbackFn = getSharedPreferences("iappyx_share", MODE_PRIVATE)
            .getString("callbackFn", "window.onShareReceived");
        String type = intent.getType();
        if (type == null) return;

        if (type.startsWith("text/")) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                final String safeText = escapeJson(text);
                webView.postDelayed(() -> {
                    if (activityAlive) fireEvent(callbackFn,
                        "{\"type\":\"text\",\"text\":\"" + safeText + "\"}");
                }, 500);
            }
        } else if (type.startsWith("image/")) {
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                new Thread(() -> {
                    try {
                        Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                        if (bmp != null) {
                            int w = bmp.getWidth(), h = bmp.getHeight();
                            if (w > 1200) { h = h * 1200 / w; w = 1200; }
                            Bitmap scaled = Bitmap.createScaledBitmap(bmp, w, h, true);
                            if (scaled != bmp) bmp.recycle();
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                            scaled.recycle();
                            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                            runOnUiThread(() -> webView.postDelayed(() -> {
                                if (activityAlive) fireEvent(callbackFn,
                                    "{\"type\":\"image\",\"dataUrl\":\"data:image/jpeg;base64," + b64 + "\"}");
                            }, 500));
                        }
                    } catch (Exception e) { Log.e("iappyxOS", "handleShareIntent image: " + e.getMessage()); }
                }).start();
            }
        }
    }

    private PendingIntent makeNfcPendingIntent() {
        Intent i = new Intent(NfcAdapter.ACTION_TAG_DISCOVERED);
        i.setComponent(new android.content.ComponentName(
            getPackageName(),
            "com.iappyx.generated.placeholder.ShellActivity"));
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    @Override protected void onResume() {
        super.onResume();
        if (nfcAdapter != null && pendingNfcCallbackFn != null) {
            nfcAdapter.enableForegroundDispatch(this, makeNfcPendingIntent(), null, null);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            try { nfcAdapter.disableForegroundDispatch(this); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_FILE_PICKER) {
            if (pendingFileCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    String dataStr = data.getDataString();
                    if (dataStr != null) results = new Uri[]{Uri.parse(dataStr)};
                    else if (data.getClipData() != null) {
                        ClipData cd = data.getClipData();
                        results = new Uri[cd.getItemCount()];
                        for (int i = 0; i < cd.getItemCount(); i++)
                            results[i] = cd.getItemAt(i).getUri();
                    }
                }
                pendingFileCallback.onReceiveValue(results);
                pendingFileCallback = null;
            }
        }

        if (requestCode == REQ_PICK_FILE) {
            if (pendingPickFileCbId == null) return;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                final String cbId = pendingPickFileCbId;
                pendingPickFileCbId = null;
                final Uri uri = data.getData();
                try {
                    // Take persistable permission so the URI stays valid
                    try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                    String name = "file";
                    long size = 0;
                    String mime = getContentResolver().getType(uri);
                    if (mime == null) mime = "application/octet-stream";
                    try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            int nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            int sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                            if (nameIdx >= 0) name = c.getString(nameIdx);
                            if (sizeIdx >= 0) size = c.getLong(sizeIdx);
                        }
                    }
                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("filePath", uri.toString());
                    result.put("name", name);
                    result.put("size", size);
                    result.put("mimeType", mime);
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            } else {
                deliverResult(pendingPickFileCbId, "{\"ok\":false,\"error\":\"cancelled\"}");
                pendingPickFileCbId = null;
            }
        }

        if (requestCode == REQ_CAMERA_PHOTO) {
            if (resultCode == RESULT_OK) {
                try {
                    Bitmap bmp = null;
                    if (pendingPhotoUri != null)
                        bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), pendingPhotoUri);
                    if (bmp == null && data != null && data.getExtras() != null)
                        bmp = (Bitmap) data.getExtras().get("data");
                    if (bmp == null) {
                        deliverResult(pendingPhotoCallbackId, "{\"ok\":false,\"error\":\"no image\"}");
                        return;
                    }
                    // Apply EXIF rotation
                    bmp = fixOrientation(bmp, pendingPhotoUri);
                    if (bmp == null) { deliverResult(pendingPhotoCallbackId, "{\"ok\":false,\"error\":\"image processing failed\"}"); return; }
                    int w = bmp.getWidth(), h = bmp.getHeight();
                    if (w > 1200) { h = h * 1200 / w; w = 1200; }
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, w, h, true);
                    if (scaled != bmp) bmp.recycle();
                    bmp = scaled;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                    bmp.recycle();
                    String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    deliverResult(pendingPhotoCallbackId,
                        "{\"ok\":true,\"dataUrl\":\"data:image/jpeg;base64," + b64 + "\"}");
                } catch (Exception e) {
                    deliverResult(pendingPhotoCallbackId, "{\"ok\":false,\"error\":\"capture failed\"}");
                }
            } else {
                deliverResult(pendingPhotoCallbackId, "{\"ok\":false,\"error\":\"cancelled\"}");
            }
        }

        // Video recording result
        if (requestCode == REQ_CAMERA_VIDEO) {
            if (resultCode == RESULT_OK && pendingVideoCallbackId != null) {
                try {
                    Uri videoUri = pendingVideoUri != null ? pendingVideoUri : (data != null ? data.getData() : null);
                    if (videoUri != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        java.io.InputStream is = getContentResolver().openInputStream(videoUri);
                        if (is == null) {
                            deliverResult(pendingVideoCallbackId, "{\"ok\":false,\"error\":\"cannot read video\"}");
                            return;
                        }
                        try {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = is.read(buf)) != -1) {
                                baos.write(buf, 0, len);
                                if (baos.size() > 50 * 1024 * 1024) {
                                    deliverResult(pendingVideoCallbackId, "{\"ok\":false,\"error\":\"video too large (max 50MB)\"}");
                                    return;
                                }
                            }
                        } finally { is.close(); }
                        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                        deliverResult(pendingVideoCallbackId,
                            "{\"ok\":true,\"dataUrl\":\"data:video/mp4;base64," + b64 + "\"}");
                    } else {
                        deliverResult(pendingVideoCallbackId, "{\"ok\":false,\"error\":\"no video\"}");
                    }
                } catch (Exception e) {
                    deliverResult(pendingVideoCallbackId, "{\"ok\":false,\"error\":\"video capture failed\"}");
                }
            } else if (pendingVideoCallbackId != null) {
                deliverResult(pendingVideoCallbackId, "{\"ok\":false,\"error\":\"cancelled\"}");
            }
        }

        // QR scan result (photo taken, decode barcode)
        if (requestCode == REQ_QR_SCAN) {
            if (resultCode == RESULT_OK && pendingQrCallbackId != null) {
                try {
                    Bitmap bmp = null;
                    if (pendingPhotoUri != null)
                        bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), pendingPhotoUri);
                    if (bmp == null && data != null && data.getExtras() != null)
                        bmp = (Bitmap) data.getExtras().get("data");
                    if (bmp != null) {
                        bmp = fixOrientation(bmp, pendingPhotoUri);
                        final Bitmap qrBmp = bmp;
                        com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
                            .process(com.google.mlkit.vision.common.InputImage.fromBitmap(qrBmp, 0))
                            .addOnSuccessListener(barcodes -> {
                                qrBmp.recycle();
                                if (!barcodes.isEmpty()) {
                                    com.google.mlkit.vision.barcode.common.Barcode bc = barcodes.get(0);
                                    try {
                                        JSONObject r = new JSONObject();
                                        r.put("ok", true);
                                        r.put("text", bc.getRawValue());
                                        r.put("format", bc.getFormat());
                                        deliverResult(pendingQrCallbackId, r.toString());
                                    } catch (Exception e) {
                                        deliverResult(pendingQrCallbackId, "{\"ok\":false,\"error\":\"parse error\"}");
                                    }
                                } else {
                                    deliverResult(pendingQrCallbackId, "{\"ok\":false,\"error\":\"no barcode found in image\"}");
                                }
                            })
                            .addOnFailureListener(e -> {
                                qrBmp.recycle();
                                deliverResult(pendingQrCallbackId, "{\"ok\":false,\"error\":\"scan failed\"}");
                            });
                    } else {
                        deliverResult(pendingQrCallbackId, "{\"ok\":false,\"error\":\"no image\"}");
                    }
                } catch (Exception e) {
                    deliverResult(pendingQrCallbackId, "{\"ok\":false,\"error\":\"qr scan failed\"}");
                }
            } else if (pendingQrCallbackId != null) {
                deliverResult(pendingQrCallbackId, "{\"ok\":false,\"error\":\"cancelled\"}");
            }
        }

        // OCR text recognition result
        if (requestCode == REQ_OCR_SCAN) {
            if (resultCode == RESULT_OK && pendingOcrCallbackId != null) {
                Bitmap bmp = null;
                try {
                    if (pendingPhotoUri != null)
                        bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), pendingPhotoUri);
                    if (bmp == null && data != null && data.getExtras() != null)
                        bmp = (Bitmap) data.getExtras().get("data");
                    if (bmp != null) {
                        bmp = fixOrientation(bmp, pendingPhotoUri);
                        final Bitmap ocrBmp = bmp;
                        com.google.mlkit.vision.text.TextRecognition.getClient(
                            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                            .process(com.google.mlkit.vision.common.InputImage.fromBitmap(ocrBmp, 0))
                            .addOnSuccessListener(result -> {
                                ocrBmp.recycle();
                                try {
                                    JSONObject r = new JSONObject();
                                    r.put("ok", true);
                                    r.put("text", result.getText());
                                    JSONArray blocks = new JSONArray();
                                    for (com.google.mlkit.vision.text.Text.TextBlock block : result.getTextBlocks()) {
                                        JSONObject b = new JSONObject();
                                        b.put("text", block.getText());
                                        JSONArray lines = new JSONArray();
                                        for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                                            lines.put(line.getText());
                                        }
                                        b.put("lines", lines);
                                        blocks.put(b);
                                    }
                                    r.put("blocks", blocks);
                                    deliverResult(pendingOcrCallbackId, r.toString());
                                } catch (Exception e) {
                                    deliverResult(pendingOcrCallbackId, "{\"ok\":false,\"error\":\"parse error\"}");
                                }
                            })
                            .addOnFailureListener(e -> {
                                ocrBmp.recycle();
                                deliverResult(pendingOcrCallbackId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                            });
                    } else {
                        deliverResult(pendingOcrCallbackId, "{\"ok\":false,\"error\":\"no image\"}");
                    }
                } catch (Exception e) {
                    if (bmp != null) bmp.recycle();
                    deliverResult(pendingOcrCallbackId, "{\"ok\":false,\"error\":\"ocr failed\"}");
                }
            } else if (pendingOcrCallbackId != null) {
                deliverResult(pendingOcrCallbackId, "{\"ok\":false,\"error\":\"cancelled\"}");
            }
        }

        // Speech-to-text result
        if (requestCode == REQ_SPEECH) {
            if (resultCode == RESULT_OK && pendingSpeechCallbackId != null && data != null) {
                try {
                    java.util.ArrayList<String> results = data.getStringArrayListExtra(
                        android.speech.RecognizerIntent.EXTRA_RESULTS);
                    if (results != null && !results.isEmpty()) {
                        JSONObject r = new JSONObject();
                        r.put("ok", true);
                        r.put("text", results.get(0));
                        JSONArray alts = new JSONArray();
                        for (String s : results) alts.put(s);
                        r.put("alternatives", alts);
                        deliverResult(pendingSpeechCallbackId, r.toString());
                    } else {
                        deliverResult(pendingSpeechCallbackId, "{\"ok\":false,\"error\":\"no speech detected\"}");
                    }
                } catch (Exception e) {
                    deliverResult(pendingSpeechCallbackId, "{\"ok\":false,\"error\":\"speech failed\"}");
                }
            } else if (pendingSpeechCallbackId != null) {
                deliverResult(pendingSpeechCallbackId, "{\"ok\":false,\"error\":\"cancelled\"}");
            }
        }

        // Image classification result
        if (requestCode == REQ_CLASSIFY) {
            if (resultCode == RESULT_OK && pendingClassifyCallbackId != null) {
                Bitmap bmp = null;
                try {
                    if (pendingPhotoUri != null)
                        bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), pendingPhotoUri);
                    if (bmp == null && data != null && data.getExtras() != null)
                        bmp = (Bitmap) data.getExtras().get("data");
                    if (bmp != null) {
                        bmp = fixOrientation(bmp, pendingPhotoUri);
                        final Bitmap clsBmp = bmp;
                        com.google.mlkit.vision.label.ImageLabeling.getClient(
                            com.google.mlkit.vision.label.defaults.ImageLabelerOptions.DEFAULT_OPTIONS)
                            .process(com.google.mlkit.vision.common.InputImage.fromBitmap(clsBmp, 0))
                            .addOnSuccessListener(labels -> {
                                clsBmp.recycle();
                                try {
                                    JSONObject r = new JSONObject();
                                    r.put("ok", true);
                                    JSONArray arr = new JSONArray();
                                    for (com.google.mlkit.vision.label.ImageLabel label : labels) {
                                        JSONObject l = new JSONObject();
                                        l.put("label", label.getText());
                                        l.put("confidence", Math.round(label.getConfidence() * 100));
                                        arr.put(l);
                                    }
                                    r.put("labels", arr);
                                    deliverResult(pendingClassifyCallbackId, r.toString());
                                } catch (Exception e) {
                                    deliverResult(pendingClassifyCallbackId, "{\"ok\":false,\"error\":\"parse error\"}");
                                }
                            })
                            .addOnFailureListener(e -> {
                                clsBmp.recycle();
                                deliverResult(pendingClassifyCallbackId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                            });
                    } else {
                        deliverResult(pendingClassifyCallbackId, "{\"ok\":false,\"error\":\"no image\"}");
                    }
                } catch (Exception e) {
                    if (bmp != null) bmp.recycle();
                    deliverResult(pendingClassifyCallbackId, "{\"ok\":false,\"error\":\"classify failed\"}");
                }
            } else if (pendingClassifyCallbackId != null) {
                deliverResult(pendingClassifyCallbackId, "{\"ok\":false,\"error\":\"cancelled\"}");
            }
        }

        // Image segmentation (background removal) result
        if (requestCode == REQ_SEGMENT) {
            if (resultCode == RESULT_OK && pendingSegmentCallbackId != null) {
                Bitmap bmp = null;
                try {
                    if (pendingPhotoUri != null)
                        bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), pendingPhotoUri);
                    if (bmp == null && data != null && data.getExtras() != null)
                        bmp = (Bitmap) data.getExtras().get("data");
                    if (bmp != null) {
                        bmp = fixOrientation(bmp, pendingPhotoUri);
                        // Resize for performance
                        int w = bmp.getWidth(), h = bmp.getHeight();
                        if (w > 1024) { h = h * 1024 / w; w = 1024; }
                        Bitmap scaled = Bitmap.createScaledBitmap(bmp, w, h, true);
                        if (scaled != bmp) bmp.recycle();
                        final Bitmap segBmp = scaled;
                        final int imgW = w, imgH = h;

                        com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions options =
                            new com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions.Builder()
                                .setDetectorMode(com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                                .build();
                        com.google.mlkit.vision.segmentation.Segmentation.getClient(options)
                            .process(com.google.mlkit.vision.common.InputImage.fromBitmap(segBmp, 0))
                            .addOnSuccessListener(mask -> {
                                try {
                                    // Create output bitmap with transparent background
                                    java.nio.ByteBuffer buffer = mask.getBuffer();
                                    int maskW = mask.getWidth(), maskH = mask.getHeight();
                                    Bitmap output = Bitmap.createBitmap(imgW, imgH, Bitmap.Config.ARGB_8888);
                                    int[] srcPixels = new int[imgW * imgH];
                                    segBmp.getPixels(srcPixels, 0, imgW, 0, 0, imgW, imgH);
                                    int[] outPixels = new int[imgW * imgH];
                                    for (int y = 0; y < imgH; y++) {
                                        for (int x = 0; x < imgW; x++) {
                                            int mx = x * maskW / imgW;
                                            int my = y * maskH / imgH;
                                            float confidence = buffer.getFloat((my * maskW + mx) * 4);
                                            if (confidence > 0.5f) {
                                                outPixels[y * imgW + x] = srcPixels[y * imgW + x];
                                            } else {
                                                outPixels[y * imgW + x] = 0x00000000; // transparent
                                            }
                                        }
                                    }
                                    output.setPixels(outPixels, 0, imgW, 0, 0, imgW, imgH);
                                    segBmp.recycle();

                                    // Encode as PNG (supports transparency)
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    output.compress(Bitmap.CompressFormat.PNG, 100, baos);
                                    output.recycle();
                                    String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                                    deliverResult(pendingSegmentCallbackId,
                                        "{\"ok\":true,\"dataUrl\":\"data:image/png;base64," + b64 + "\"}");
                                } catch (Exception e) {
                                    segBmp.recycle();
                                    deliverResult(pendingSegmentCallbackId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                                }
                            })
                            .addOnFailureListener(e -> {
                                segBmp.recycle();
                                deliverResult(pendingSegmentCallbackId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                            });
                    } else {
                        deliverResult(pendingSegmentCallbackId, "{\"ok\":false,\"error\":\"no image\"}");
                    }
                } catch (Exception e) {
                    if (bmp != null) bmp.recycle();
                    deliverResult(pendingSegmentCallbackId, "{\"ok\":false,\"error\":\"segmentation failed\"}");
                }
            } else if (pendingSegmentCallbackId != null) {
                deliverResult(pendingSegmentCallbackId, "{\"ok\":false,\"error\":\"cancelled\"}");
            }
        }

        if (requestCode == REQ_MEDIA_PICK) {
            if (resultCode == RESULT_OK && data != null && pendingMediaCbId != null) {
                final Uri uri = data.getData();
                final String cbId = pendingMediaCbId;
                pendingMediaCbId = null;
                if (uri == null) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"no image selected\"}");
                    return;
                }
                new Thread(() -> {
                    try {
                        java.io.InputStream is = getContentResolver().openInputStream(uri);
                        if (is == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"cannot read image\"}"); return; }
                        Bitmap original = android.graphics.BitmapFactory.decodeStream(is);
                        is.close();
                        if (original == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"decode failed\"}"); return; }
                        Bitmap bmp = original;
                        if (original.getWidth() > 1200) {
                            float scale = 1200f / original.getWidth();
                            bmp = Bitmap.createScaledBitmap(original, 1200, (int)(original.getHeight() * scale), true);
                            original.recycle();
                        }
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                        bmp.recycle();
                        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                        deliverResult(cbId, "{\"ok\":true,\"dataUrl\":\"data:image/jpeg;base64," + b64 + "\"}");
                    } catch (Exception e) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                    }
                }).start();
            } else if (pendingMediaCbId != null) {
                deliverResult(pendingMediaCbId, "{\"ok\":false,\"error\":\"cancelled\"}");
                pendingMediaCbId = null;
            }
        }
    }

    // Bug #5: handle ALL permission request results
    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        String cbId = pendingCallbacks.remove(req);
        boolean ok = grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED;
        if (ok) {
            if (req == REQ_CAMERA) { if (pendingCameraAction != null) { Runnable a = pendingCameraAction; pendingCameraAction = null; a.run(); } }
            else if (req == REQ_LOCATION) new LocationBridge().getLocation(cbId);
            else if (req == REQ_CONTACTS) new ContactsBridge().getContacts(cbId);
            else if (req == REQ_CALENDAR_READ) {
                if (cbId != null) new CalendarBridge().getEvents(cbId, null, null);
            }
            else if (req == REQ_SMS) {
                if (pendingSmsNumber != null && pendingSmsMessage != null) {
                    new SmsBridge().send(pendingSmsNumber, pendingSmsMessage, cbId);
                    pendingSmsNumber = null;
                    pendingSmsMessage = null;
                } else if (cbId != null) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"permission granted, please retry\"}");
                }
            }
            else if (req == REQ_CALENDAR_WRITE) {
                if (pendingCalendarEvent != null) {
                    new CalendarBridge().addEvent(cbId, pendingCalendarEvent[0], pendingCalendarEvent[1], pendingCalendarEvent[2], pendingCalendarEvent[3]);
                    pendingCalendarEvent = null;
                }
            }
            else if (req == REQ_AUDIO_RECORD) {
                if (cbId != null) new AudioBridge().doStartRecording(cbId);
            }
            else if (req == REQ_NOTIFICATION) {
                // Notification permission granted, nothing to callback
            }
            else if (req == REQ_ACTIVITY_RECOG) {
                if (pendingStepCallbackFn != null) {
                    new SensorBridge().doStartStepCounter(pendingStepCallbackFn);
                    pendingStepCallbackFn = null;
                }
            }
            else if (req == REQ_WIFI_DIRECT) {
                if (wifiP2pPendingAction != null) {
                    Runnable action = wifiP2pPendingAction;
                    wifiP2pPendingAction = null;
                    wifiP2pPendingCbId = null;
                    action.run();
                }
            }
            else if (req == REQ_BLE) {
                if (blePendingAction != null) {
                    Runnable action = blePendingAction;
                    blePendingAction = null;
                    action.run();
                }
            }
        } else {
            if (req == REQ_WIFI_DIRECT) {
                if (wifiP2pPendingCbId != null) {
                    deliverResult(wifiP2pPendingCbId, "{\"ok\":false,\"error\":\"permission denied\"}");
                } else if (wifiP2pPeerCallbackFn != null) {
                    fireEvent(wifiP2pPeerCallbackFn, "{\"event\":\"error\",\"error\":\"permission denied\"}");
                }
                wifiP2pPendingAction = null;
                wifiP2pPendingCbId = null;
            }
            if (req == REQ_BLE) {
                if (bleScanCallbackFn != null) {
                    fireEvent(bleScanCallbackFn, "{\"event\":\"error\",\"error\":\"permission denied\"}");
                }
                blePendingAction = null;
            }
            if (cbId != null) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"permission denied\"}");
            }
        }
        // getUserMedia permissions — grant only the resources whose permissions were actually granted
        if (req == REQ_MEDIA_STREAM && pendingWebPermission != null) {
            java.util.List<String> granted = new java.util.ArrayList<>();
            for (int i = 0; i < perms.length; i++) {
                if (grants[i] == PackageManager.PERMISSION_GRANTED) {
                    if (Manifest.permission.CAMERA.equals(perms[i])) granted.add("android.webkit.resource.VIDEO_CAPTURE");
                    if (Manifest.permission.RECORD_AUDIO.equals(perms[i])) granted.add("android.webkit.resource.AUDIO_CAPTURE");
                }
            }
            if (!granted.isEmpty()) {
                pendingWebPermission.grant(granted.toArray(new String[0]));
            } else {
                pendingWebPermission.deny();
            }
            pendingWebPermission = null;
        }
    }

    private void stopSensor(int type) {
        SensorEventListener l = activeSensors.remove(type);
        if (l != null && sensorManager != null) sensorManager.unregisterListener(l);
    }

    private void stopSensors() {
        for (SensorEventListener l : activeSensors.values()) {
            if (sensorManager != null) sensorManager.unregisterListener(l);
        }
        activeSensors.clear();
    }

    // ═══════════════════════════════════════════════════════════
    // BRIDGES
    // ═══════════════════════════════════════════════════════════

    // ── Storage ──
    class StorageBridge {
        SharedPreferences p = getSharedPreferences("iappyx_store", MODE_PRIVATE);
        @JavascriptInterface public void save(String k, String v) { p.edit().putString(k,v).apply(); }
        @JavascriptInterface public String load(String k) { return p.getString(k,null); }
        @JavascriptInterface public void remove(String k) { p.edit().remove(k).apply(); }
        @JavascriptInterface public void clear() { p.edit().clear().apply(); }

        private String safeFilename(String filename) {
            // Keep alphanumeric, dots, hyphens, underscores. Replace path separators.
            String safe = filename.replace('/', '_').replace('\\', '_').replace('\0', '_');
            // If the result would be empty or all dots, use a hash
            if (safe.isEmpty() || safe.matches("^\\.+$")) safe = "file_" + Math.abs(filename.hashCode());
            return safe;
        }

        @JavascriptInterface
        public void saveFile(String filename, String content) {
            if (filename == null || filename.isEmpty() || content == null) return;
            try {
                File f = new File(getFilesDir(), safeFilename(filename));
                try (java.io.FileWriter w = new java.io.FileWriter(f)) {
                    w.write(content);
                }
            } catch (Exception e) { Log.e("iappyxOS", "saveFile: " + e.getMessage()); }
        }

        @JavascriptInterface
        public String loadFile(String filename) {
            try {
                File f = new File(getFilesDir(), safeFilename(filename));
                if (!f.exists()) return null;
                byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                return new String(bytes, "UTF-8");
            } catch (Exception e) { return null; }
        }

        @JavascriptInterface
        public void deleteFile(String filename) {
            try {
                File f = new File(getFilesDir(), safeFilename(filename));
                f.delete();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public boolean saveToDownloads(final String filename, final String base64, final String mimeType) {
            if (filename == null || base64 == null || base64.isEmpty()) return false;
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                String mime = mimeType != null && !mimeType.isEmpty() ? mimeType : "application/octet-stream";
                String safeName = safeFilename(filename);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ — use MediaStore
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safeName);
                    values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mime);
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
                    Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return false;
                    try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) os.write(bytes);
                    }
                    values.clear();
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                } else {
                    // Android 9 and below — write directly
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, safeName);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(bytes);
                    }
                }
                return true;
            } catch (Exception e) {
                Log.e("iappyxOS", "saveToDownloads: " + e.getMessage());
                return false;
            }
        }

        @JavascriptInterface
        public boolean moveFile(String srcPath, String destPath) {
            if (srcPath == null || destPath == null) return false;
            try {
                if (isContentUri(srcPath)) {
                    // Content URIs: copy (can't move/delete)
                    try (java.io.InputStream is = openInput(srcPath);
                         FileOutputStream fos = new FileOutputStream(resolveFilePath(destPath))) {
                        if (is == null) return false;
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                    }
                    return true;
                }
                File src = new File(resolveFilePath(srcPath));
                File dest = new File(resolveFilePath(destPath));
                if (!src.exists()) return false;
                if (src.renameTo(dest)) return true;
                try (java.io.FileInputStream fis = new java.io.FileInputStream(src);
                     FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = fis.read(buf)) != -1) fos.write(buf, 0, r);
                }
                src.delete();
                return true;
            } catch (Exception e) { Log.e("iappyxOS", "moveFile: " + e.getMessage()); return false; }
        }

        @JavascriptInterface
        public boolean copyFileToDownloads(String srcPath, String filename, String mimeType) {
            if (srcPath == null || filename == null) return false;
            try {
                String mime = mimeType != null && !mimeType.isEmpty() ? mimeType : "application/octet-stream";
                String safeName = safeFilename(filename);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safeName);
                    values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mime);
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
                    Uri destUri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (destUri == null) return false;
                    try (java.io.InputStream is = openInput(srcPath);
                         java.io.OutputStream os = getContentResolver().openOutputStream(destUri)) {
                        if (is != null && os != null) {
                            byte[] buf = new byte[65536];
                            int r;
                            while ((r = is.read(buf)) != -1) os.write(buf, 0, r);
                        }
                    }
                    values.clear();
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(destUri, values, null, null);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File dest = new File(dir, safeName);
                    try (java.io.InputStream is = openInput(srcPath);
                         FileOutputStream fos = new FileOutputStream(dest)) {
                        if (is != null) {
                            byte[] buf = new byte[65536];
                            int r;
                            while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                        }
                    }
                }
                return true;
            } catch (Exception e) { Log.e("iappyxOS", "copyFileToDownloads: " + e.getMessage()); return false; }
        }

        @JavascriptInterface
        public void shareFile(final String filename, final String base64, final String mimeType) {
            if (filename == null || base64 == null || base64.isEmpty()) return;
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    File dir = new File(getCacheDir(), "share");
                    dir.mkdirs();
                    String safeName = safeFilename(filename);
                    File file = new File(dir, safeName);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(bytes);
                    }
                    Uri uri = FileProvider.getUriForFile(ShellActivity.this, getPackageName() + ".provider", file);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(mimeType != null && !mimeType.isEmpty() ? mimeType : "application/octet-stream");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Share"));
                } catch (Exception e) { Log.e("iappyxOS", "shareFile: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void pickFile(String cbId) {
            pendingPickFileCbId = cbId;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try {
                startActivityForResult(intent, REQ_PICK_FILE);
            } catch (Exception e) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                pendingPickFileCbId = null;
            }
        }

        @JavascriptInterface
        public String getFileInfo(String path) {
            try {
                if (isContentUri(path)) {
                    Uri uri = Uri.parse(path);
                    String name = "file";
                    long size = 0;
                    String mime = getContentResolver().getType(uri);
                    try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                        if (c != null && c.moveToFirst()) {
                            int nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            int sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                            if (nameIdx >= 0) name = c.getString(nameIdx);
                            if (sizeIdx >= 0) size = c.getLong(sizeIdx);
                        }
                    }
                    JSONObject r = new JSONObject();
                    r.put("exists", true);
                    r.put("size", size);
                    r.put("name", name);
                    r.put("mimeType", mime != null ? mime : "application/octet-stream");
                    return r.toString();
                }
                File f = new File(resolveFilePath(path));
                if (!f.exists()) return "{\"exists\":false}";
                String mime = null;
                try { mime = java.net.URLConnection.guessContentTypeFromName(f.getName()); } catch (Exception ignored) {}
                JSONObject r = new JSONObject();
                r.put("exists", true);
                r.put("size", f.length());
                r.put("name", f.getName());
                r.put("mimeType", mime != null ? mime : "application/octet-stream");
                r.put("modified", f.lastModified());
                return r.toString();
            } catch (Exception e) { return "{\"exists\":false}"; }
        }

        @JavascriptInterface
        public String listFiles() {
            try {
                File dir = getFilesDir();
                File[] files = dir.listFiles();
                JSONArray arr = new JSONArray();
                if (files != null) {
                    for (File f : files) {
                        if (!f.isFile()) continue;
                        JSONObject o = new JSONObject();
                        o.put("name", f.getName());
                        o.put("size", f.length());
                        o.put("modified", f.lastModified());
                        arr.put(o);
                    }
                }
                return arr.toString();
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public String readFileBase64(String path) {
            try {
                long size = getContentSize(path);
                if (size > 50 * 1024 * 1024) return null; // 50MB limit
                try (java.io.InputStream is = openInput(path)) {
                    if (is == null) return null;
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
                    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                }
            } catch (Exception e) { return null; }
        }
    }

    // ── Device ──
    class DeviceBridge {
        @JavascriptInterface public String getPackageName() { return ShellActivity.this.getPackageName(); }
        @JavascriptInterface public String getAppName() {
            try {
                return getApplicationInfo().loadLabel(getPackageManager()).toString();
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public String getDeviceInfo() {
            try {
                BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
                int bat = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
                boolean charging = bm != null && bm.isCharging();
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                JSONObject obj = new JSONObject();
                obj.put("brand", Build.BRAND);
                obj.put("model", Build.MODEL);
                obj.put("sdk", Build.VERSION.SDK_INT);
                obj.put("battery", bat);
                obj.put("charging", charging);
                obj.put("screenWidth", dm.widthPixels);
                obj.put("screenHeight", dm.heightPixels);
                obj.put("density", dm.density);
                obj.put("language", java.util.Locale.getDefault().toLanguageTag());
                return obj.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public String getThemeColors() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("isDark", isDarkMode());
                if (Build.VERSION.SDK_INT >= 31) {
                    // Android 12+ Material You dynamic colors
                    android.content.res.Resources res = getResources();
                    obj.put("primary", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_accent1_500, getTheme())));
                    obj.put("primaryLight", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_accent1_200, getTheme())));
                    obj.put("primaryDark", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_accent1_700, getTheme())));
                    obj.put("secondary", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_accent2_500, getTheme())));
                    obj.put("tertiary", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_accent3_500, getTheme())));
                    obj.put("neutral", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_neutral1_500, getTheme())));
                    obj.put("neutralLight", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_neutral1_100, getTheme())));
                    obj.put("neutralDark", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_neutral1_900, getTheme())));
                    obj.put("background", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_neutral1_900, getTheme())));
                    obj.put("surface", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_neutral1_800, getTheme())));
                    obj.put("onPrimary", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_accent1_0, getTheme())));
                    obj.put("onSurface", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_neutral1_50, getTheme())));
                    obj.put("onBackground", String.format("#%06X", 0xFFFFFF & res.getColor(android.R.color.system_neutral1_50, getTheme())));
                    obj.put("dynamic", true);
                } else {
                    // Pre-Android 12: return default dark theme colors
                    obj.put("primary", "#4FC3F7");
                    obj.put("primaryLight", "#80D8FF");
                    obj.put("primaryDark", "#0F3460");
                    obj.put("secondary", "#69F0AE");
                    obj.put("tertiary", "#FF6B6B");
                    obj.put("neutral", "#888888");
                    obj.put("neutralLight", "#EAEAEA");
                    obj.put("neutralDark", "#1A1A2E");
                    obj.put("background", "#0D0D1A");
                    obj.put("surface", "#1A1A2E");
                    obj.put("onPrimary", "#FFFFFF");
                    obj.put("onSurface", "#EAEAEA");
                    obj.put("onBackground", "#EAEAEA");
                    obj.put("dynamic", false);
                }
                return obj.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public String getConnectivity() {
            try {
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
                JSONObject obj = new JSONObject();
                if (cm == null) {
                    obj.put("connected", false);
                    obj.put("type", "none");
                    obj.put("metered", false);
                    return obj.toString();
                }
                android.net.Network net = cm.getActiveNetwork();
                if (net == null) {
                    obj.put("connected", false);
                    obj.put("type", "none");
                    obj.put("metered", false);
                    return obj.toString();
                }
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                boolean connected = caps != null;
                String type = "none";
                if (caps != null) {
                    if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) type = "wifi";
                    else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) type = "cellular";
                    else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) type = "ethernet";
                }
                obj.put("connected", connected);
                obj.put("type", type);
                obj.put("metered", cm.isActiveNetworkMetered());
                return obj.toString();
            } catch (Exception e) { return "{\"connected\":false,\"type\":\"none\",\"metered\":false}"; }
        }

        @JavascriptInterface
        public boolean isDarkMode() {
            int mode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }

        @JavascriptInterface
        public void viewPdf(String path) {
            try {
                File f;
                if (isContentUri(path)) {
                    // Content URI — open directly
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(path), "application/pdf");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                    return;
                }
                f = new File(resolveFilePath(path));
                if (!f.exists()) return;
                Uri uri = FileProvider.getUriForFile(ShellActivity.this, getPackageName() + ".provider", f);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e) { Log.e("iappyxOS", "viewPdf: " + e.getMessage()); }
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> {
                try {
                    android.print.PrintManager pm = (android.print.PrintManager)
                        getSystemService(Context.PRINT_SERVICE);
                    if (pm == null) return;
                    android.print.PrintDocumentAdapter adapter =
                        webView.createPrintDocumentAdapter(getAppName() != null ? getAppName() : "iappyxOS");
                    pm.print(getAppName() != null ? getAppName() : "Print", adapter,
                        new android.print.PrintAttributes.Builder().build());
                } catch (Exception e) { Log.e("iappyxOS", "print: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void ping(String host, String timeoutMs, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    int timeout = Math.max(1, Math.min(Integer.parseInt(timeoutMs), 10000));
                    long start = System.currentTimeMillis();
                    Process p = Runtime.getRuntime().exec(new String[]{
                        "/system/bin/ping", "-c", "1", "-W", String.valueOf(timeout / 1000 + 1), host});
                    boolean finished = p.waitFor(timeout + 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
                    long elapsed = System.currentTimeMillis() - start;
                    if (!finished) { p.destroyForcibly(); deliverResult(cbId, "{\"ok\":true,\"reachable\":false,\"ms\":" + elapsed + ",\"host\":\"" + escapeJson(host) + "\",\"error\":\"timeout\"}"); return; }
                    int exit = p.exitValue();
                    boolean reachable = (exit == 0);
                    // Parse ms from output if reachable
                    double rtt = -1;
                    if (reachable) {
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                // "time=12.3 ms"
                                int idx = line.indexOf("time=");
                                if (idx >= 0) {
                                    String val = line.substring(idx + 5).split(" ")[0];
                                    rtt = Double.parseDouble(val);
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    deliverResult(cbId, "{\"ok\":true,\"reachable\":" + reachable +
                        (rtt >= 0 ? ",\"ms\":" + rtt : ",\"ms\":" + elapsed) +
                        ",\"host\":\"" + escapeJson(host) + "\"}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void setTorch(final boolean on) {
            try {
                android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager) getSystemService(Context.CAMERA_SERVICE);
                if (cm == null) return;
                String cameraId = cm.getCameraIdList()[0];
                cm.setTorchMode(cameraId, on);
            } catch (Exception e) { Log.e("iappyxOS", "setTorch: " + e.getMessage()); }
        }

        @JavascriptInterface
        public void setShortcuts(String shortcutsJson) {
            if (shortcutsJson == null) return;
            runOnUiThread(() -> {
                try {
                    JSONArray arr = new JSONArray(shortcutsJson);
                    java.util.List<androidx.core.content.pm.ShortcutInfoCompat> shortcuts = new java.util.ArrayList<>();
                    for (int i = 0; i < arr.length() && i < 4; i++) {
                        JSONObject s = arr.getJSONObject(i);
                        String id = s.getString("id");
                        String label = s.getString("label");
                        String callbackFn = s.optString("callback", "window.onShortcut");
                        // Store callback for this shortcut
                        getSharedPreferences("iappyx_shortcuts", MODE_PRIVATE).edit()
                            .putString("callback_" + id, callbackFn).apply();
                        Intent intent = new Intent(ShellActivity.this, ShellActivity.class);
                        intent.setAction("shortcut_" + id);
                        intent.putExtra("shortcut_id", id);
                        shortcuts.add(new androidx.core.content.pm.ShortcutInfoCompat.Builder(ShellActivity.this, id)
                            .setShortLabel(label)
                            .setIntent(intent)
                            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(
                                ShellActivity.this, android.R.drawable.ic_menu_compass))
                            .build());
                    }
                    androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(ShellActivity.this, shortcuts);
                } catch (Exception e) { Log.e("iappyxOS", "setShortcuts: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void setShareCallback(String callbackFn) {
            if (callbackFn == null) return;
            getSharedPreferences("iappyx_share", MODE_PRIVATE).edit()
                .putString("callbackFn", callbackFn).apply();
        }

        @JavascriptInterface
        public void setDndMode(boolean enabled) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (!nm.isNotificationPolicyAccessGranted()) {
                // Request DND access — opens system settings
                startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                return;
            }
            nm.setInterruptionFilter(enabled
                ? NotificationManager.INTERRUPTION_FILTER_ALARMS
                : NotificationManager.INTERRUPTION_FILTER_ALL);
        }

        @JavascriptInterface
        public boolean isDndActive() {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return false;
            return nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
        }

        @JavascriptInterface
        public void onClipboardChange(final String callbackFn) {
            if (callbackFn == null) return;
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                // Remove previous listener to prevent accumulation
                if (activeClipListener != null) cm.removePrimaryClipChangedListener(activeClipListener);
                activeClipListener = () -> {
                    if (!activityAlive) return;
                    try {
                        ClipData clip = cm.getPrimaryClip();
                        if (clip != null && clip.getItemCount() > 0) {
                            CharSequence text = clip.getItemAt(0).getText();
                            if (text != null) {
                                fireEvent(callbackFn, "{\"text\":\"" + escapeJson(text.toString()) + "\"}");
                            }
                        }
                    } catch (Exception ignored) {}
                };
                cm.addPrimaryClipChangedListener(activeClipListener);
            });
        }

        @JavascriptInterface
        public String readFromDownloads(String filename) {
            if (filename == null || filename.isEmpty()) return null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ — use MediaStore
                    String[] projection = {android.provider.MediaStore.Downloads._ID};
                    String selection = android.provider.MediaStore.Downloads.DISPLAY_NAME + "=?";
                    android.database.Cursor cursor = getContentResolver().query(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        projection, selection, new String[]{filename}, null);
                    try {
                        if (cursor != null && cursor.moveToFirst()) {
                            long id = cursor.getLong(0);
                            Uri uri = android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                            try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                                if (is != null) {
                                    byte[] bytes = readAllBytes(is);
                                    return new String(bytes, "UTF-8");
                                }
                            }
                        }
                    } finally { if (cursor != null) cursor.close(); }
                } else {
                    String safeName = filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
                    File f = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), safeName);
                    if (f.exists()) {
                        return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
                    }
                }
            } catch (Exception e) { Log.e("iappyxOS", "readFromDownloads: " + e.getMessage()); }
            return null;
        }

        private byte[] readAllBytes(java.io.InputStream is) throws java.io.IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
            return baos.toByteArray();
        }

        /** Set wallpaper. target: "both" (default), "home", "lock" */
        @JavascriptInterface
        public void setWallpaper(String base64) { setWallpaperTarget(base64, "both"); }

        @JavascriptInterface
        public void setWallpaperTarget(String base64, String target) {
            if (base64 == null || base64.isEmpty()) return;
            new Thread(() -> {
                try {
                    String clean = base64;
                    if (clean.contains(",")) clean = clean.substring(clean.indexOf(",") + 1);
                    byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
                    Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp == null) { Log.e("iappyxOS", "setWallpaper: failed to decode image"); return; }
                    android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(ShellActivity.this);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        int flag = 0;
                        if ("home".equals(target)) flag = android.app.WallpaperManager.FLAG_SYSTEM;
                        else if ("lock".equals(target)) flag = android.app.WallpaperManager.FLAG_LOCK;
                        else flag = android.app.WallpaperManager.FLAG_SYSTEM | android.app.WallpaperManager.FLAG_LOCK;
                        wm.setBitmap(bmp, null, true, flag);
                    } else {
                        wm.setBitmap(bmp);
                    }
                    bmp.recycle();
                    Log.i("iappyxOS", "Wallpaper set (" + target + ")");
                } catch (Exception e) { Log.e("iappyxOS", "setWallpaper: " + e.getMessage()); }
            }).start();
        }
    }

    // ── Camera + Share ──
    class CameraBridge {
        private com.google.mlkit.vision.barcode.BarcodeScanner cachedBarcodeScanner;
        private com.google.mlkit.vision.barcode.BarcodeScanner getBarcodeScanner() {
            if (cachedBarcodeScanner == null) cachedBarcodeScanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient();
            return cachedBarcodeScanner;
        }

        @JavascriptInterface
        public void takePhoto(String cbId) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_CAMERA, cbId);
                final String cb = cbId;
                pendingCameraAction = () -> new CameraBridge().takePhoto(cb);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                return;
            }
            pendingPhotoCallbackId = cbId;
            pendingPhotoUri = null;
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            boolean hasCam = !getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
            if (!hasCam) { deliverResult(cbId, "{\"ok\":false,\"error\":\"no camera app\"}"); return; }
            try {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File f = File.createTempFile("IMG_"+ts, ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));
                pendingPhotoUri = FileProvider.getUriForFile(ShellActivity.this, getPackageName()+".provider", f);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) { pendingPhotoUri = null; }
            startActivityForResult(intent, REQ_CAMERA_PHOTO);
        }

        // Bug #3: use try-with-resources for FileOutputStream
        @JavascriptInterface
        public void sharePhoto(final String base64Jpeg) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64Jpeg, Base64.DEFAULT);
                    File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    File file = new File(dir, "share_" + System.currentTimeMillis() + ".jpg");
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(bytes);
                    }
                    Uri uri = FileProvider.getUriForFile(ShellActivity.this, getPackageName()+".provider", file);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("image/jpeg");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Share photo"));
                } catch (Exception e) { Log.e("iappyxOS", "sharePhoto: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void shareText(final String text, final String subject) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, text);
                    if (subject != null && !subject.isEmpty()) intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                    startActivity(Intent.createChooser(intent, "Share"));
                } catch (Exception e) { Log.e("iappyxOS", "shareText: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void takeVideo(String cbId) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_CAMERA, cbId);
                final String cb = cbId;
                pendingCameraAction = () -> new CameraBridge().takeVideo(cb);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                return;
            }
            pendingVideoCallbackId = cbId;
            pendingVideoUri = null;
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0); // low quality for size
            intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30); // max 30 seconds
            boolean hasCam = !getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
            if (!hasCam) { deliverResult(cbId, "{\"ok\":false,\"error\":\"no camera app\"}"); return; }
            try {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                File f = File.createTempFile("VID_"+ts, ".mp4", getExternalFilesDir(Environment.DIRECTORY_MOVIES));
                pendingVideoUri = FileProvider.getUriForFile(ShellActivity.this, getPackageName()+".provider", f);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingVideoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) { pendingVideoUri = null; }
            startActivityForResult(intent, REQ_CAMERA_VIDEO);
        }

        @JavascriptInterface
        public void scanQR(String cbId) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_CAMERA, cbId);
                final String cb = cbId;
                pendingCameraAction = () -> new CameraBridge().scanQR(cb);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                return;
            }
            // Take a photo, then scan it for QR/barcode
            pendingQrCallbackId = cbId;
            pendingPhotoUri = null;
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            boolean hasCam = !getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
            if (!hasCam) { deliverResult(cbId, "{\"ok\":false,\"error\":\"no camera\"}"); return; }
            try {
                File f = File.createTempFile("QR_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));
                pendingPhotoUri = FileProvider.getUriForFile(ShellActivity.this, getPackageName()+".provider", f);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) { pendingPhotoUri = null; }
            startActivityForResult(intent, REQ_QR_SCAN);
        }

        @JavascriptInterface
        public void scanText(String cbId) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_CAMERA, cbId);
                final String cb = cbId;
                pendingCameraAction = () -> new CameraBridge().scanText(cb);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                return;
            }
            pendingOcrCallbackId = cbId;
            pendingPhotoUri = null;
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            boolean hasCam = !getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
            if (!hasCam) { deliverResult(cbId, "{\"ok\":false,\"error\":\"no camera\"}"); return; }
            try {
                File f = File.createTempFile("OCR_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));
                pendingPhotoUri = FileProvider.getUriForFile(ShellActivity.this, getPackageName()+".provider", f);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) { pendingPhotoUri = null; }
            startActivityForResult(intent, REQ_OCR_SCAN);
        }

        @JavascriptInterface
        public void classify(String cbId) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_CAMERA, cbId);
                final String cb = cbId;
                pendingCameraAction = () -> new CameraBridge().classify(cb);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                return;
            }
            pendingClassifyCallbackId = cbId;
            pendingPhotoUri = null;
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            boolean hasCam = !getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
            if (!hasCam) { deliverResult(cbId, "{\"ok\":false,\"error\":\"no camera\"}"); return; }
            try {
                File f = File.createTempFile("CLS_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));
                pendingPhotoUri = FileProvider.getUriForFile(ShellActivity.this, getPackageName()+".provider", f);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) { pendingPhotoUri = null; }
            startActivityForResult(intent, REQ_CLASSIFY);
        }

        @JavascriptInterface
        public void removeBackground(String cbId) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_CAMERA, cbId);
                final String cb = cbId;
                pendingCameraAction = () -> new CameraBridge().removeBackground(cb);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                return;
            }
            pendingSegmentCallbackId = cbId;
            pendingPhotoUri = null;
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            boolean hasCam = !getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty();
            if (!hasCam) { deliverResult(cbId, "{\"ok\":false,\"error\":\"no camera\"}"); return; }
            try {
                File f = File.createTempFile("SEG_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES));
                pendingPhotoUri = FileProvider.getUriForFile(ShellActivity.this, getPackageName()+".provider", f);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) { pendingPhotoUri = null; }
            startActivityForResult(intent, REQ_SEGMENT);
        }

        @JavascriptInterface
        public void getExif(String pathOrDataUrl, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    java.io.InputStream in;
                    if (pathOrDataUrl.startsWith("data:")) {
                        String b64 = pathOrDataUrl.substring(pathOrDataUrl.indexOf(",") + 1);
                        byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                        in = new java.io.ByteArrayInputStream(bytes);
                    } else {
                        in = openInput(pathOrDataUrl);
                    }
                    if (in == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"cannot read file\"}"); return; }
                    androidx.exifinterface.media.ExifInterface exif;
                    try { exif = new androidx.exifinterface.media.ExifInterface(in); } finally { in.close(); }
                    JSONObject r = new JSONObject();
                    r.put("ok", true);
                    double[] latLon = exif.getLatLong();
                    if (latLon != null) { r.put("lat", latLon[0]); r.put("lon", latLon[1]); }
                    String dt = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME);
                    if (dt != null) r.put("datetime", dt);
                    String make = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE);
                    if (make != null) r.put("make", make);
                    String model = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL);
                    if (model != null) r.put("model", model);
                    int w = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, 0);
                    int h = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, 0);
                    if (w > 0) r.put("width", w);
                    if (h > 0) r.put("height", h);
                    String iso = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY);
                    if (iso != null) r.put("iso", iso);
                    String aperture = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER);
                    if (aperture != null) r.put("aperture", aperture);
                    String exposure = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME);
                    if (exposure != null) r.put("exposureTime", exposure);
                    String focal = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH);
                    if (focal != null) r.put("focalLength", focal);
                    int flash = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_FLASH, -1);
                    if (flash >= 0) r.put("flash", flash != 0);
                    int orient = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 0);
                    r.put("orientation", orient);
                    deliverResult(cbId, r.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public String scanFrameQRSync(String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) return "{\"ok\":false,\"error\":\"invalid image\"}";
                com.google.mlkit.vision.common.InputImage img = com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0);
                java.util.List<com.google.mlkit.vision.barcode.common.Barcode> barcodes =
                    com.google.android.gms.tasks.Tasks.await(
                        getBarcodeScanner().process(img));
                JSONArray arr = new JSONArray();
                for (com.google.mlkit.vision.barcode.common.Barcode b : barcodes) {
                    JSONObject o = new JSONObject();
                    o.put("text", b.getRawValue());
                    o.put("format", b.getFormat());
                    arr.put(o);
                }
                bmp.recycle();
                return "{\"ok\":true,\"results\":" + arr.toString() + "}";
            } catch (Exception e) {
                return "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
            }
        }

        @JavascriptInterface
        public void scanFrameQR(String base64, String cbId) {
            httpClientPool.submit(() -> deliverResult(cbId, scanFrameQRSync(base64)));
        }

        @JavascriptInterface
        public String scanFrameTextSync(String base64) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) return "{\"ok\":false,\"error\":\"invalid image\"}";
                com.google.mlkit.vision.common.InputImage img = com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0);
                com.google.mlkit.vision.text.Text text = com.google.android.gms.tasks.Tasks.await(
                    com.google.mlkit.vision.text.TextRecognition.getClient(
                        com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS).process(img));
                JSONObject r = new JSONObject();
                r.put("ok", true);
                r.put("text", text.getText());
                JSONArray blocks = new JSONArray();
                for (com.google.mlkit.vision.text.Text.TextBlock block : text.getTextBlocks()) {
                    JSONObject bo = new JSONObject();
                    bo.put("text", block.getText());
                    JSONArray lines = new JSONArray();
                    for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) lines.put(line.getText());
                    bo.put("lines", lines);
                    blocks.put(bo);
                }
                r.put("blocks", blocks);
                bmp.recycle();
                return r.toString();
            } catch (Exception e) {
                return "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
            }
        }

        @JavascriptInterface
        public void scanFrameText(String base64, String cbId) {
            httpClientPool.submit(() -> deliverResult(cbId, scanFrameTextSync(base64)));
        }
    }

    // ── Location ──
    private LocationListener activeLocationListener;

    class LocationBridge {
        private String locationJson(Location l) {
            return "{\"ok\":true,\"lat\":" + l.getLatitude() +
                ",\"lon\":" + l.getLongitude() + ",\"accuracy\":" + l.getAccuracy() +
                ",\"altitude\":" + l.getAltitude() + ",\"speed\":" + l.getSpeed() +
                ",\"bearing\":" + l.getBearing() + "}";
        }

        @JavascriptInterface
        public void getLocation(String cbId) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_LOCATION, cbId);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                return;
            }
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"location service unavailable\"}"); return; }
            Location last = null;
            try { last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) {}
            if (last == null) try { last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
            if (last != null) {
                deliverResult(cbId, locationJson(last));
            } else {
                try {
                    final boolean[] delivered = {false};
                    LocationListener listener = new LocationListener() {
                        @Override public void onLocationChanged(Location l) { if (!delivered[0]) { delivered[0] = true; deliverResult(cbId, locationJson(l)); } }
                        @Override public void onStatusChanged(String p, int s, Bundle e) {}
                        @Override public void onProviderEnabled(String p) {}
                        @Override public void onProviderDisabled(String p) { if (!delivered[0]) { delivered[0] = true; deliverResult(cbId, "{\"ok\":false,\"error\":\"location provider disabled\"}"); } }
                    };
                    lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null);
                    // Timeout after 15 seconds
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (!delivered[0]) { delivered[0] = true; lm.removeUpdates(listener); deliverResult(cbId, "{\"ok\":false,\"error\":\"location timeout\"}"); }
                    }, 15000);
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"location unavailable\"}");
                }
            }
        }

        @JavascriptInterface
        public void watchPosition(final String callbackFn) {
            watchPositionWithError(callbackFn, null);
        }

        @JavascriptInterface
        public void watchPositionWithError(final String callbackFn, final String errorCallbackFn) {
            // Stop foreground tracking service to avoid double GPS drain
            stopTracking();
            watchPositionErrorFn = errorCallbackFn;
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                if (errorCallbackFn != null) fireEvent(errorCallbackFn, "{\"error\":\"location permission denied\"}");
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                 Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                return;
            }
            stopWatching();
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) { if (errorCallbackFn != null) fireEvent(errorCallbackFn, "{\"error\":\"location service unavailable\"}"); return; }
            activeLocationListener = new LocationListener() {
                @Override public void onLocationChanged(Location l) {
                    if (!activityAlive) return;
                    fireEvent(callbackFn, locationJson(l));
                }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {
                    if (watchPositionErrorFn != null)
                        fireEvent(watchPositionErrorFn, "{\"error\":\"location provider disabled\"}");
                }
            };
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1, activeLocationListener);
            } catch (Exception e) {
                if (errorCallbackFn != null) fireEvent(errorCallbackFn, "{\"error\":\"location unavailable\"}");
            }
        }

        @JavascriptInterface
        public void stopWatching() {
            if (activeLocationListener != null) {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) lm.removeUpdates(activeLocationListener);
                activeLocationListener = null;
            }
        }

        // ── Foreground location tracking (survives backgrounding/screen off) ──

        @JavascriptInterface
        public void startTracking(final String callbackFn) {
            startTrackingWithOptions(callbackFn, 2000, 1, "Tracking location");
        }

        @JavascriptInterface
        public void startTrackingWithOptions(final String callbackFn, final double intervalMs,
                final double minDistanceM, final String notificationTitle) {
            if (callbackFn == null || callbackFn.isEmpty()) return;
            // Stop regular watchPosition to avoid double GPS drain
            stopWatching();
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                fireEvent(callbackFn, "{\"error\":\"location permission denied\"}");
                return;
            }
            Intent intent = new Intent(ShellActivity.this, LocationService.class);
            intent.setAction(LocationService.ACTION_START);
            intent.putExtra("callbackFn", callbackFn);
            intent.putExtra("interval", (long) intervalMs);
            intent.putExtra("minDistance", (float) minDistanceM);
            intent.putExtra("title", notificationTitle != null ? notificationTitle : "Tracking location");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        @JavascriptInterface
        public void stopTracking() {
            Intent intent = new Intent(ShellActivity.this, LocationService.class);
            intent.setAction(LocationService.ACTION_STOP);
            startService(intent);
        }

        // ── Geofencing (single shared GPS listener for all fences) ──

        private class GeoFence {
            String id, callbackFn;
            double lat, lon, radius;
            boolean inside = false;
            GeoFence(String id, double lat, double lon, double radius, String callbackFn) {
                this.id = id; this.lat = lat; this.lon = lon; this.radius = radius; this.callbackFn = callbackFn;
            }
        }
        private final java.util.Map<String, GeoFence> fences = new java.util.HashMap<>();

        private void startGeofenceListener() {
            if (sharedGeofenceListener != null) return; // already running
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            sharedGeofenceListener = new LocationListener() {
                @Override public void onLocationChanged(Location l) {
                    if (!activityAlive) return;
                    for (GeoFence f : new java.util.ArrayList<>(fences.values())) {
                        float[] results = new float[1];
                        Location.distanceBetween(l.getLatitude(), l.getLongitude(), f.lat, f.lon, results);
                        boolean nowInside = results[0] <= f.radius;
                        if (nowInside && !f.inside) {
                            f.inside = true;
                            fireEvent(f.callbackFn, "{\"id\":\"" + f.id + "\",\"transition\":\"enter\"" +
                                ",\"lat\":" + l.getLatitude() + ",\"lon\":" + l.getLongitude() + "}");
                        } else if (!nowInside && f.inside) {
                            f.inside = false;
                            fireEvent(f.callbackFn, "{\"id\":\"" + f.id + "\",\"transition\":\"exit\"" +
                                ",\"lat\":" + l.getLatitude() + ",\"lon\":" + l.getLongitude() + "}");
                        }
                    }
                }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 5, sharedGeofenceListener);
            } catch (SecurityException e) {
                sharedGeofenceListener = null;
                // Notify all pending fences that geofencing failed
                for (GeoFence f : fences.values()) {
                    fireEvent(f.callbackFn, "{\"id\":\"" + f.id + "\",\"transition\":\"error\",\"error\":\"location permission denied\"}");
                }
                fences.clear();
                Log.e("iappyxOS", "Geofence listener failed: " + e.getMessage());
            }
        }

        private void stopGeofenceListener() {
            if (sharedGeofenceListener != null) {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) lm.removeUpdates(sharedGeofenceListener);
                sharedGeofenceListener = null;
            }
        }

        @JavascriptInterface
        public void addGeofence(final String fenceId, final double lat, final double lon,
                final double radiusM, final String callbackFn) {
            if (fenceId == null || callbackFn == null) return;
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                fireEvent(callbackFn, "{\"error\":\"location permission denied\"}");
                return;
            }
            fences.put(fenceId, new GeoFence(fenceId, lat, lon, radiusM, callbackFn));
            startGeofenceListener();
        }

        @JavascriptInterface
        public void removeGeofence(final String fenceId) {
            if (fenceId == null) return;
            fences.remove(fenceId);
            if (fences.isEmpty()) stopGeofenceListener();
        }

        @JavascriptInterface
        public void removeAllGeofences() {
            fences.clear();
            stopGeofenceListener();
        }
    }

    // ── Notifications ──
    class NotificationBridge {
        private static final String CH = "iappyx";
        NotificationBridge() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(CH, "iappyxOS", NotificationManager.IMPORTANCE_DEFAULT);
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.createNotificationChannel(ch);
            }
        }
        @JavascriptInterface
        public void send(String title, String body) {
            // Request POST_NOTIFICATIONS on Android 13+ (non-blocking — send anyway, OS will drop if denied)
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
                }
            }
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            int id = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
            try {
                nm.notify(id,
                    new NotificationCompat.Builder(ShellActivity.this, CH)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title).setContentText(body).setAutoCancel(true).build());
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void sendWithId(String idStr, String title, String body) {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
                }
            }
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            int id;
            try { id = Integer.parseInt(idStr); } catch (Exception e) { id = idStr.hashCode(); }
            try {
                nm.notify(id,
                    new NotificationCompat.Builder(ShellActivity.this, CH)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title).setContentText(body).setAutoCancel(true).build());
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void cancel(String idStr) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            int id;
            try { id = Integer.parseInt(idStr); } catch (Exception e) { id = idStr.hashCode(); }
            nm.cancel(id);
        }

        @JavascriptInterface
        public void cancelAll() {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancelAll();
        }

        @JavascriptInterface
        public void sendWithActions(String idStr, String title, String body,
                String actionsJson, String callbackFn) {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
                }
            }
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            int id;
            try { id = Integer.parseInt(idStr); } catch (Exception e) { id = idStr.hashCode(); }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(ShellActivity.this, CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(body).setAutoCancel(true);
            try {
                JSONArray actions = new JSONArray(actionsJson);
                for (int i = 0; i < actions.length() && i < 3; i++) {
                    JSONObject action = actions.getJSONObject(i);
                    String actionId = action.getString("id");
                    String actionLabel = action.getString("label");
                    Intent intent = new Intent(ShellActivity.this, NotificationActionReceiver.class);
                    intent.putExtra("actionId", actionId);
                    intent.putExtra("notificationId", idStr);
                    intent.putExtra("callbackFn", callbackFn);
                    intent.setAction("action_" + actionId + "_" + System.currentTimeMillis());
                    PendingIntent pi = PendingIntent.getBroadcast(ShellActivity.this,
                        (actionId + idStr).hashCode(), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    builder.addAction(0, actionLabel, pi);
                }
            } catch (Exception e) { Log.e("iappyxOS", "sendWithActions: " + e.getMessage()); }
            nm.notify(id, builder.build());
        }

        @JavascriptInterface
        public void schedule(String idStr, String title, String body, double timestampMs) {
            // Scheduled notification fires from ScheduledNotificationReceiver which can't request permission
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
                }
            }
            try {
                int id;
                try { id = Integer.parseInt(idStr); } catch (Exception e) { id = idStr.hashCode(); }
                Intent intent = new Intent(ShellActivity.this, ScheduledNotificationReceiver.class);
                intent.putExtra("title", title);
                intent.putExtra("body", body);
                intent.putExtra("notifId", id);
                PendingIntent pi = PendingIntent.getBroadcast(ShellActivity.this, id, intent,
                    piFlags(PendingIntent.FLAG_UPDATE_CURRENT));
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (am == null) return;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.set(AlarmManager.RTC_WAKEUP, (long) timestampMs, pi);
                    Log.w("iappyxOS", "Using inexact alarm (exact alarm permission not granted)");
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, (long) timestampMs, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, (long) timestampMs, pi);
                }
                Log.i("iappyxOS", "Scheduled notification '" + title + "' for " + new java.util.Date((long) timestampMs));
            } catch (Exception e) { Log.e("iappyxOS", "schedule: " + e.getMessage()); }
        }

        @JavascriptInterface
        public void cancelScheduled(String idStr) {
            int id;
            try { id = Integer.parseInt(idStr); } catch (Exception e) { id = idStr.hashCode(); }
            Intent intent = new Intent(ShellActivity.this, ScheduledNotificationReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(ShellActivity.this, id, intent,
                piFlags(PendingIntent.FLAG_UPDATE_CURRENT));
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pi);
        }

        @JavascriptInterface
        public void setBadge(double count) {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
                }
            }
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm == null) return;
                if ((int) count <= 0) { nm.cancel(99999); return; }
                String chId = "iappyx_badge";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel ch = new NotificationChannel(chId, "App Badge", NotificationManager.IMPORTANCE_DEFAULT);
                    ch.setShowBadge(true);
                    ch.setSound(null, null);
                    ch.enableVibration(false);
                    nm.createNotificationChannel(ch);
                }
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                PendingIntent contentPi = PendingIntent.getActivity(ShellActivity.this, 0,
                    launchIntent != null ? launchIntent : new Intent(),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                nm.notify(99999, new NotificationCompat.Builder(ShellActivity.this, chId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(getApplicationInfo().loadLabel(getPackageManager()))
                    .setContentText((int) count + " pending")
                    .setContentIntent(contentPi)
                    .setNumber((int) count)
                    .setAutoCancel(true)
                    .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                    .build());
                Log.i("iappyxOS", "Badge notification posted with count=" + (int) count);
            } catch (Exception e) { Log.e("iappyxOS", "setBadge: " + e.getMessage()); }
        }
    }

    // ── Vibration ──
    class VibrationBridge {
        private Vibrator getVibrator() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.os.VibratorManager vm = (android.os.VibratorManager)
                    ShellActivity.this.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return vm != null ? vm.getDefaultVibrator() : null;
            }
            return (Vibrator) ShellActivity.this.getSystemService(Context.VIBRATOR_SERVICE);
        }
        @JavascriptInterface public void vibrate(final String msStr) { pattern("0," + msStr); }
        @JavascriptInterface
        public void pattern(final String pat) {
            runOnUiThread(() -> {
                try {
                    String[] parts = pat.split(",");
                    if (parts.length > 100) { Log.w("iappyxOS", "Vibration pattern too long (max 100)"); return; }
                    long[] t = new long[parts.length];
                    for (int i = 0; i < parts.length; i++) t[i] = Long.parseLong(parts[i].trim());
                    Vibrator v = getVibrator();
                    if (v == null) return;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        v.vibrate(VibrationEffect.createWaveform(t, -1));
                    else v.vibrate(t, -1);
                } catch (Exception e) { Log.e("iappyxOS", "vibrate: " + e.getMessage()); }
            });
        }
        @JavascriptInterface
        public void click() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Vibrator v = getVibrator();
                if (v != null) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            } else { pattern("0,30"); }
        }
        @JavascriptInterface
        public void tick() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Vibrator v = getVibrator();
                if (v != null) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            } else { pattern("0,10"); }
        }
        @JavascriptInterface
        public void heavyClick() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Vibrator v = getVibrator();
                if (v != null) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
            } else { pattern("0,80"); }
        }
    }

    // ── Clipboard ──
    class ClipboardBridge {
        @JavascriptInterface
        public void write(String text) {
            runOnUiThread(() -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("iappyx", text));
            });
        }
        @JavascriptInterface
        public String read() {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            try {
                ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
                return item != null && item.getText() != null ? item.getText().toString() : null;
            } catch (Exception e) { return null; }
        }
    }

    // ── Sensors ──
    class SensorBridge {
        private void startSensor(int type, String callbackFn) {
            if (callbackFn == null || callbackFn.isEmpty()) return;
            runOnUiThread(() -> {
                Sensor sensor = sensorManager.getDefaultSensor(type);
                if (sensor == null) { fireEvent(callbackFn, "{\"error\":\"sensor not available\"}"); return; }
                stopSensor(type);
                SensorEventListener listener = new SensorEventListener() {
                    @Override public void onSensorChanged(SensorEvent e) {
                        if (!activityAlive) return;
                        final String js = callbackFn + "({x:" + e.values[0] +
                            ",y:" + e.values[1] + ",z:" + e.values[2] +
                            ",t:" + (e.timestamp / 1000000L) + "})";
                        runOnUiThread(() -> { if (activityAlive) webView.evaluateJavascript(js, null); });
                    }
                    @Override public void onAccuracyChanged(Sensor s, int a) {}
                };
                activeSensors.put(type, listener);
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME);
            });
        }
        @JavascriptInterface public void startAccelerometer(String fn) { startSensor(Sensor.TYPE_ACCELEROMETER, fn); }
        @JavascriptInterface public void startGyroscope(String fn) { startSensor(Sensor.TYPE_GYROSCOPE, fn); }
        @JavascriptInterface public void startMagnetometer(String fn) { startSensor(Sensor.TYPE_MAGNETIC_FIELD, fn); }

        @JavascriptInterface
        public void startProximity(String callbackFn) {
            if (callbackFn == null || callbackFn.isEmpty()) return;
            runOnUiThread(() -> {
                Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
                if (sensor == null) { fireEvent(callbackFn, "{\"error\":\"sensor not available\"}"); return; }
                stopSensor(Sensor.TYPE_PROXIMITY);
                SensorEventListener listener = new SensorEventListener() {
                    @Override public void onSensorChanged(SensorEvent e) {
                        if (!activityAlive) return;
                        final String js = callbackFn + "({distance:" + e.values[0] +
                            ",near:" + (e.values[0] < e.sensor.getMaximumRange()) +
                            ",t:" + (e.timestamp / 1000000L) + "})";
                        runOnUiThread(() -> { if (activityAlive) webView.evaluateJavascript(js, null); });
                    }
                    @Override public void onAccuracyChanged(Sensor s, int a) {}
                };
                activeSensors.put(Sensor.TYPE_PROXIMITY, listener);
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            });
        }

        @JavascriptInterface
        public void startLight(String callbackFn) {
            if (callbackFn == null || callbackFn.isEmpty()) return;
            runOnUiThread(() -> {
                Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
                if (sensor == null) { fireEvent(callbackFn, "{\"error\":\"sensor not available\"}"); return; }
                stopSensor(Sensor.TYPE_LIGHT);
                SensorEventListener listener = new SensorEventListener() {
                    @Override public void onSensorChanged(SensorEvent e) {
                        if (!activityAlive) return;
                        final String js = callbackFn + "({lux:" + e.values[0] + ",t:" + (e.timestamp / 1000000L) + "})";
                        runOnUiThread(() -> { if (activityAlive) webView.evaluateJavascript(js, null); });
                    }
                    @Override public void onAccuracyChanged(Sensor s, int a) {}
                };
                activeSensors.put(Sensor.TYPE_LIGHT, listener);
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            });
        }

        @JavascriptInterface
        public void startPressure(String callbackFn) {
            if (callbackFn == null || callbackFn.isEmpty()) return;
            runOnUiThread(() -> {
                Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
                if (sensor == null) { fireEvent(callbackFn, "{\"error\":\"sensor not available\"}"); return; }
                stopSensor(Sensor.TYPE_PRESSURE);
                SensorEventListener listener = new SensorEventListener() {
                    @Override public void onSensorChanged(SensorEvent e) {
                        if (!activityAlive) return;
                        final String js = callbackFn + "({hPa:" + e.values[0] + ",t:" + (e.timestamp / 1000000L) + "})";
                        runOnUiThread(() -> { if (activityAlive) webView.evaluateJavascript(js, null); });
                    }
                    @Override public void onAccuracyChanged(Sensor s, int a) {}
                };
                activeSensors.put(Sensor.TYPE_PRESSURE, listener);
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            });
        }
        @JavascriptInterface
        public void startStepCounter(String callbackFn) {
            if (callbackFn == null || callbackFn.isEmpty()) return;
            // Android 10+ requires ACTIVITY_RECOGNITION runtime permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this,
                        Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    pendingStepCallbackFn = callbackFn;
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQ_ACTIVITY_RECOG);
                    return;
                }
            }
            doStartStepCounter(callbackFn);
        }

        private void doStartStepCounter(String callbackFn) {
            runOnUiThread(() -> {
                Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (sensor == null) { fireEvent(callbackFn, "{\"error\":\"sensor not available\"}"); return; }
                stopSensor(Sensor.TYPE_STEP_COUNTER);
                SensorEventListener listener = new SensorEventListener() {
                    @Override public void onSensorChanged(SensorEvent e) {
                        if (!activityAlive) return;
                        final String js = callbackFn + "({steps:" + (int)e.values[0] + ",t:" + (e.timestamp / 1000000L) + "})";
                        runOnUiThread(() -> { if (activityAlive) webView.evaluateJavascript(js, null); });
                    }
                    @Override public void onAccuracyChanged(Sensor s, int a) {}
                };
                activeSensors.put(Sensor.TYPE_STEP_COUNTER, listener);
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
            });
        }
        @JavascriptInterface
        public void startCompass(String callbackFn) {
            if (callbackFn == null || callbackFn.isEmpty()) return;
            runOnUiThread(() -> {
                // Use rotation vector for stable, fused compass heading
                Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                if (sensor == null) {
                    // Fallback to magnetic field + accelerometer combo
                    sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                    if (sensor == null) { fireEvent(callbackFn, "{\"error\":\"compass not available\"}"); return; }
                    startCompassFallback(callbackFn);
                    return;
                }
                // Use sensor type 100 as our slot for compass (won't collide with real type IDs)
                int COMPASS_SLOT = 100;
                stopSensor(COMPASS_SLOT);
                final float[] rotMatrix = new float[9];
                final float[] orientation = new float[3];
                SensorEventListener listener = new SensorEventListener() {
                    @Override public void onSensorChanged(SensorEvent e) {
                        if (!activityAlive) return;
                        SensorManager.getRotationMatrixFromVector(rotMatrix, e.values);
                        SensorManager.getOrientation(rotMatrix, orientation);
                        float heading = (float) Math.toDegrees(orientation[0]);
                        if (heading < 0) heading += 360;
                        final String js = callbackFn + "({heading:" + heading +
                            ",accuracy:" + e.accuracy +
                            ",t:" + (e.timestamp / 1000000L) + "})";
                        runOnUiThread(() -> { if (activityAlive) webView.evaluateJavascript(js, null); });
                    }
                    @Override public void onAccuracyChanged(Sensor s, int a) {}
                };
                activeSensors.put(COMPASS_SLOT, listener);
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI);
            });
        }

        private void startCompassFallback(String callbackFn) {
            // Fallback: combine accelerometer + magnetometer for heading
            int COMPASS_SLOT = 100;
            stopSensor(COMPASS_SLOT);
            final float[] gravity = new float[3];
            final float[] geomagnetic = new float[3];
            final float[] rotMatrix = new float[9];
            final float[] orientation = new float[3];
            final boolean[] hasGravity = {false};
            final boolean[] hasMag = {false};
            SensorEventListener listener = new SensorEventListener() {
                @Override public void onSensorChanged(SensorEvent e) {
                    if (!activityAlive) return;
                    if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(e.values, 0, gravity, 0, 3);
                        hasGravity[0] = true;
                    } else if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(e.values, 0, geomagnetic, 0, 3);
                        hasMag[0] = true;
                    }
                    if (hasGravity[0] && hasMag[0]) {
                        if (SensorManager.getRotationMatrix(rotMatrix, null, gravity, geomagnetic)) {
                            SensorManager.getOrientation(rotMatrix, orientation);
                            float heading = (float) Math.toDegrees(orientation[0]);
                            if (heading < 0) heading += 360;
                            final String js = callbackFn + "({heading:" + heading +
                                ",accuracy:" + e.accuracy +
                                ",t:" + (e.timestamp / 1000000L) + "})";
                            runOnUiThread(() -> { if (activityAlive) webView.evaluateJavascript(js, null); });
                        }
                    }
                }
                @Override public void onAccuracyChanged(Sensor s, int a) {}
            };
            activeSensors.put(COMPASS_SLOT, listener);
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (accel != null) sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI);
            if (mag != null) sensorManager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI);
        }

        @JavascriptInterface public void stop() { runOnUiThread(() -> stopSensors()); }
    }

    // ── TTS ──
    // Bug #6: wrap TTS calls in runOnUiThread for thread safety, check ttsReady
    class TtsBridge {
        @JavascriptInterface public void speak(String text) {
            if (text == null || text.isEmpty()) return;
            runOnUiThread(() -> {
                if (tts != null && ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "iappyx_tts");
            });
        }
        @JavascriptInterface public void speakWithCallback(String text, String callbackFn) {
            runOnUiThread(() -> {
                if (tts != null && ttsReady) {
                    tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                        @Override public void onStart(String id) {}
                        @Override public void onDone(String id) { fireEvent(callbackFn, "{\"done\":true}"); }
                        @Override public void onError(String id) { fireEvent(callbackFn, "{\"done\":false,\"error\":\"tts error\"}"); }
                    });
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "iappyx_tts");
                }
            });
        }
        @JavascriptInterface public void stop() {
            runOnUiThread(() -> { if (tts != null && ttsReady) tts.stop(); });
        }
        @JavascriptInterface public void setLanguage(String lang) {
            if (lang == null || lang.isEmpty()) return;
            runOnUiThread(() -> { if (tts != null && ttsReady) tts.setLanguage(new Locale(lang)); });
        }
        @JavascriptInterface public void setPitch(String pitch) {
            runOnUiThread(() -> {
                if (tts != null && ttsReady) {
                    try { tts.setPitch(Float.parseFloat(pitch)); } catch (Exception ignored) {}
                }
            });
        }
        @JavascriptInterface public void setRate(String rate) {
            runOnUiThread(() -> {
                if (tts != null && ttsReady) {
                    try { tts.setSpeechRate(Float.parseFloat(rate)); } catch (Exception ignored) {}
                }
            });
        }
    }

    // ── Screen ──
    class ScreenBridge {
        @JavascriptInterface
        public void keepOn(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void setBrightness(final double level) {
            runOnUiThread(() -> {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = (float) level;
                getWindow().setAttributes(lp);
            });
        }

        // Bug #12: add durationMs param, default to 10 min
        @JavascriptInterface
        public void wakeLock(final boolean acquire) {
            runOnUiThread(() -> {
                try {
                    if (acquire) {
                        if (wakeLock == null) {
                            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                            wakeLock = pm.newWakeLock(
                                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                "iappyxOS::wakelock");
                        }
                        if (!wakeLock.isHeld()) wakeLock.acquire(10 * 60 * 1000L);
                    } else {
                        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                    }
                } catch (Exception e) {
                    Log.e("iappyxOS", "wakeLock: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public boolean isScreenOn() {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        }
    }

    // ── AlarmManager ──
    class AlarmBridge {
        private static final String PREF_ALARM = "iappyx_alarm_ts";

        // Use runtime package name + original class name so the intent works after manifest patching
        private Intent makeReceiverIntent() {
            Intent intent = new Intent();
            intent.setComponent(new android.content.ComponentName(
                getPackageName(),
                "com.iappyx.generated.placeholder.AlarmReceiver"));
            return intent;
        }

        private int alarmRequestCode(String alarmId) {
            return alarmId != null ? alarmId.hashCode() & 0x7FFFFFFF : 0;
        }

        @JavascriptInterface
        public void set(final double timestampMs, final String callbackFn) {
            setWithId(null, timestampMs, callbackFn);
        }

        @JavascriptInterface
        public void setWithId(final String alarmId, final double timestampMs, final String callbackFn) {
            // Pre-request notification permission since AlarmReceiver posts a notification
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
                }
            }

            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            String id = alarmId != null ? alarmId : "default";
            getSharedPreferences("iappyx_alarm", MODE_PRIVATE).edit()
                .putString("callbackFn_" + id, callbackFn)
                .putLong("ts_" + id, (long) timestampMs)
                .apply();

            Intent intent = makeReceiverIntent();
            intent.putExtra("callbackFn", callbackFn);
            intent.putExtra("alarmId", id);
            int rc = alarmRequestCode(id);
            PendingIntent pi = PendingIntent.getBroadcast(ShellActivity.this, rc, intent,
                piFlags(PendingIntent.FLAG_UPDATE_CURRENT));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // Fallback to inexact alarm
                am.set(AlarmManager.RTC_WAKEUP, (long) timestampMs, pi);
                Log.w("iappyxOS", "Alarm '" + id + "' set as inexact (exact alarm permission not granted)");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, (long) timestampMs, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, (long) timestampMs, pi);
            }
            Log.i("iappyxOS", "Alarm '" + id + "' set for " + new Date((long) timestampMs));
        }

        @JavascriptInterface
        public void cancel() { cancelById(null); }

        @JavascriptInterface
        public void cancelById(final String alarmId) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            String id = alarmId != null ? alarmId : "default";
            Intent intent = makeReceiverIntent();
            int rc = alarmRequestCode(id);
            PendingIntent pi = PendingIntent.getBroadcast(ShellActivity.this, rc, intent,
                piFlags(PendingIntent.FLAG_UPDATE_CURRENT));
            am.cancel(pi);
            getSharedPreferences("iappyx_alarm", MODE_PRIVATE).edit()
                .remove("callbackFn_" + id).remove("ts_" + id).apply();
        }

        @JavascriptInterface
        public String getScheduled() {
            long ts = getSharedPreferences("iappyx_alarm", MODE_PRIVATE).getLong("ts_default", 0);
            return ts > 0 ? String.valueOf(ts) : null;
        }

        @JavascriptInterface
        public void setRepeating(final String alarmId, final double intervalMs, final String callbackFn) {
            if (callbackFn == null) return;
            // AlarmReceiver posts a notification — need permission on Android 13+
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFICATION);
                }
            }
            String id = alarmId != null ? alarmId : "default";
            getSharedPreferences("iappyx_alarm", MODE_PRIVATE).edit()
                .putString("callbackFn_" + id, callbackFn).apply();
            Intent intent = makeReceiverIntent();
            intent.putExtra("callbackFn", callbackFn);
            intent.putExtra("alarmId", id);
            int rc = alarmRequestCode(id);
            PendingIntent pi = PendingIntent.getBroadcast(ShellActivity.this, rc, intent,
                piFlags(PendingIntent.FLAG_UPDATE_CURRENT));
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + (long) intervalMs,
                (long) intervalMs, pi);
        }
    }

    // ── Audio ──
    // Bug #9: add error listener before prepareAsync, protect state
    class AudioBridge {
        /** Play a one-shot sound effect that overlays the main audio track. */
        @JavascriptInterface
        public void playSound(final String url) {
            runOnUiThread(() -> {
                try {
                    androidx.media3.exoplayer.ExoPlayer sp = new androidx.media3.exoplayer.ExoPlayer.Builder(ShellActivity.this).build();
                    sp.setMediaItem(androidx.media3.common.MediaItem.fromUri(resolveDataUrl(url)));
                    sp.addListener(new androidx.media3.common.Player.Listener() {
                        @Override public void onPlaybackStateChanged(int state) {
                            if (state == androidx.media3.common.Player.STATE_ENDED) {
                                runOnUiThread(() -> { sp.release(); soundPlayers.remove(sp); });
                            }
                        }
                        @Override public void onPlayerError(androidx.media3.common.PlaybackException e) {
                            runOnUiThread(() -> { sp.release(); soundPlayers.remove(sp); });
                        }
                    });
                    soundPlayers.add(sp);
                    sp.prepare();
                    sp.play();
                } catch (Exception e) {
                    Log.e("iappyxOS", "playSound error: " + e.getMessage());
                }
            });
        }

        /** Stop all overlay sound effects. */
        @JavascriptInterface
        public void stopSounds() {
            runOnUiThread(() -> {
                for (androidx.media3.exoplayer.ExoPlayer sp : new java.util.ArrayList<>(soundPlayers)) {
                    try { sp.release(); } catch (Exception ignored) {}
                }
                soundPlayers.clear();
            });
        }

        private String resolveDataUrl(String url) {
            if (url != null && url.startsWith("data:")) {
                try {
                    int commaIdx = url.indexOf(',');
                    if (commaIdx < 0) return url;
                    String b64 = url.substring(commaIdx + 1);
                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                    String ext = url.contains("audio/mp4") ? ".m4a" : url.contains("audio/wav") ? ".wav" : ".tmp";
                    File f = File.createTempFile("audio_", ext, getCacheDir());
                    f.deleteOnExit();
                    try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(bytes); }
                    return f.getAbsolutePath();
                } catch (Exception e) { return url; }
            }
            return url;
        }

        @JavascriptInterface
        public void play(final String url) {
            final String resolved = resolveDataUrl(url);
            lastAudioUrl = resolved;
            if (mediaSessionActive) {
                Intent intent = new Intent(ShellActivity.this, AudioService.class);
                intent.setAction(AudioService.ACTION_PLAY);
                intent.putExtra("url", resolved);
                if (pendingSessionTitle != null) intent.putExtra("title", pendingSessionTitle);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
                else startService(intent);
                return;
            }
            runOnUiThread(() -> {
                try {
                    // Release visualizer/equalizer before destroying player — they hold the audio session ID
                    if (audioVisualizer != null) { try { audioVisualizer.setEnabled(false); audioVisualizer.release(); } catch (Exception ignored) {} audioVisualizer = null; }
                    if (equalizer != null) { try { equalizer.setEnabled(false); equalizer.release(); } catch (Exception ignored) {} equalizer = null; }
                    if (exoPlayer != null) { try { exoPlayer.pause(); exoPlayer.stop(); exoPlayer.release(); } catch (Exception ignored) {} }
                    exoPlayer = new androidx.media3.exoplayer.ExoPlayer.Builder(ShellActivity.this).build();
                    exoPlayer.addListener(new androidx.media3.common.Player.Listener() {
                        @Override public void onPlaybackStateChanged(int state) {
                            if (state == androidx.media3.common.Player.STATE_ENDED && activityAlive && audioCompleteCallbackFn != null)
                                fireEvent(audioCompleteCallbackFn, "{\"done\":true}");
                        }
                        @Override public void onMediaMetadataChanged(androidx.media3.common.MediaMetadata metadata) {
                            if (!activityAlive || audioMetadataCallbackFn == null) return;
                            String title = metadata.title != null ? metadata.title.toString() : "";
                            String artist = metadata.artist != null ? metadata.artist.toString() : "";
                            String album = metadata.albumTitle != null ? metadata.albumTitle.toString() : "";
                            String station = metadata.station != null ? metadata.station.toString() : "";
                            String genre = metadata.genre != null ? metadata.genre.toString() : "";
                            fireEvent(audioMetadataCallbackFn, "{\"title\":\"" + escapeJson(title) +
                                "\",\"artist\":\"" + escapeJson(artist) +
                                "\",\"album\":\"" + escapeJson(album) +
                                "\",\"station\":\"" + escapeJson(station) +
                                "\",\"genre\":\"" + escapeJson(genre) + "\"}");
                        }
                        @Override public void onPlayerError(androidx.media3.common.PlaybackException e) {
                            Log.e("iappyxOS", "ExoPlayer error: " + e.getMessage());
                            if (audioCompleteCallbackFn != null) fireEvent(audioCompleteCallbackFn, "{\"done\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                        }
                    });
                    exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(resolved));
                    exoPlayer.prepare();
                    exoPlayer.play();
                } catch (Exception e) {
                    Log.e("iappyxOS", "Audio play error: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void stop() {
            lastAudioUrl = null;
            if (mediaSessionActive) {
                mediaSessionActive = false;
                pendingSessionTitle = null;
                pendingSessionArtist = null;
                pendingSessionAlbum = null;
                try {
                    Intent intent = new Intent(ShellActivity.this, AudioService.class);
                    intent.setAction(AudioService.ACTION_STOP);
                    startService(intent);
                } catch (Exception ignored) {}
                return;
            }
            runOnUiThread(() -> {
                if (exoPlayer != null) {
                    try { exoPlayer.pause(); exoPlayer.stop(); exoPlayer.clearMediaItems(); exoPlayer.release(); } catch (Exception ignored) {}
                    exoPlayer = null;
                }
            });
        }

        private androidx.media3.exoplayer.ExoPlayer activePlayer() {
            if (mediaSessionActive && AudioService.player != null) return AudioService.player;
            return exoPlayer;
        }

        @JavascriptInterface
        public void setVolume(final double level) {
            runOnUiThread(() -> {
                androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                if (p != null) { try { p.setVolume((float)level); } catch (Exception ignored) {} }
            });
        }

        @JavascriptInterface
        public void setLooping(final boolean loop) {
            runOnUiThread(() -> {
                androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                if (p != null) { try { p.setRepeatMode(loop ? androidx.media3.common.Player.REPEAT_MODE_ONE : androidx.media3.common.Player.REPEAT_MODE_OFF); } catch (Exception ignored) {} }
            });
        }

        @JavascriptInterface
        public void setSpeed(final String speedStr) {
            runOnUiThread(() -> {
                try {
                    float speed = Float.parseFloat(speedStr);
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    if (p != null) p.setPlaybackSpeed(speed);
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void addToQueue(final String url) {
            final String resolved = resolveDataUrl(url);
            runOnUiThread(() -> {
                try {
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    if (p != null) p.addMediaItem(androidx.media3.common.MediaItem.fromUri(resolved));
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void clearQueue() {
            runOnUiThread(() -> {
                try {
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    if (p != null) { p.clearMediaItems(); }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void skipToNext() {
            runOnUiThread(() -> {
                try {
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    if (p != null && p.hasNextMediaItem()) p.seekToNextMediaItem();
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void skipToPrevious() {
            runOnUiThread(() -> {
                try {
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    if (p != null && p.hasPreviousMediaItem()) p.seekToPreviousMediaItem();
                } catch (Exception ignored) {}
            });
        }

        // ── Equalizer ──
        private android.media.audiofx.Equalizer equalizer;

        @JavascriptInterface
        public String getEqualizerPresets() {
            try {
                java.util.concurrent.FutureTask<String> task = new java.util.concurrent.FutureTask<>(() -> {
                    try {
                        androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                        if (p == null) return "[]";
                        if (equalizer == null) equalizer = new android.media.audiofx.Equalizer(0, p.getAudioSessionId());
                        JSONArray arr = new JSONArray();
                        for (short i = 0; i < equalizer.getNumberOfPresets(); i++) {
                            arr.put(equalizer.getPresetName(i));
                        }
                        return arr.toString();
                    } catch (Exception e) { return "[]"; }
                });
                runOnUiThread(task);
                return task.get(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public void setEqualizerPreset(String indexStr) {
            runOnUiThread(() -> {
                try {
                    short idx = Short.parseShort(indexStr);
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    if (p == null) return;
                    if (equalizer == null) equalizer = new android.media.audiofx.Equalizer(0, p.getAudioSessionId());
                    equalizer.setEnabled(true);
                    equalizer.usePreset(idx);
                } catch (Exception e) { Log.e("iappyxOS", "setEqualizerPreset: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void setEqualizerBand(String bandStr, String levelStr) {
            runOnUiThread(() -> {
                try {
                    short band = Short.parseShort(bandStr);
                    short level = Short.parseShort(levelStr);
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    if (p == null) return;
                    if (equalizer == null) equalizer = new android.media.audiofx.Equalizer(0, p.getAudioSessionId());
                    equalizer.setEnabled(true);
                    equalizer.setBandLevel(band, level);
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public String getEqualizerBands() {
            try {
                java.util.concurrent.FutureTask<String> task = new java.util.concurrent.FutureTask<>(() -> {
                    try {
                        androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                        if (p == null) return "{}";
                        if (equalizer == null) equalizer = new android.media.audiofx.Equalizer(0, p.getAudioSessionId());
                        JSONObject r = new JSONObject();
                        short bands = equalizer.getNumberOfBands();
                        r.put("bands", bands);
                        r.put("minLevel", equalizer.getBandLevelRange()[0]);
                        r.put("maxLevel", equalizer.getBandLevelRange()[1]);
                        JSONArray arr = new JSONArray();
                        for (short i = 0; i < bands; i++) {
                            JSONObject b = new JSONObject();
                            b.put("band", i);
                            b.put("centerFreq", equalizer.getCenterFreq(i) / 1000); // Hz
                            b.put("level", equalizer.getBandLevel(i));
                            arr.put(b);
                        }
                        r.put("bandInfo", arr);
                        return r.toString();
                    } catch (Exception e) { return "{}"; }
                });
                runOnUiThread(task);
                return task.get(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void disableEqualizer() {
            runOnUiThread(() -> {
                if (equalizer != null) { try { equalizer.setEnabled(false); equalizer.release(); } catch (Exception ignored) {} equalizer = null; }
            });
        }

        @JavascriptInterface
        public boolean isPlaying() {
            try {
                java.util.concurrent.FutureTask<Boolean> task = new java.util.concurrent.FutureTask<>(() -> {
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    return p != null && p.isPlaying();
                });
                runOnUiThread(task);
                return task.get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface
        public void setSystemVolume(final double level) {
            setStreamVolume("alarm", level);
        }

        @JavascriptInterface
        public void setStreamVolume(final String stream, final double level) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            int streamType;
            switch (stream != null ? stream : "alarm") {
                case "music": streamType = AudioManager.STREAM_MUSIC; break;
                case "ring": streamType = AudioManager.STREAM_RING; break;
                case "notification": streamType = AudioManager.STREAM_NOTIFICATION; break;
                case "system": streamType = AudioManager.STREAM_SYSTEM; break;
                case "voice": streamType = AudioManager.STREAM_VOICE_CALL; break;
                default: streamType = AudioManager.STREAM_ALARM; break;
            }
            int max = am.getStreamMaxVolume(streamType);
            am.setStreamVolume(streamType, (int)(level * max), 0);
        }

        @JavascriptInterface
        public void setMediaSession(String infoJson) {
            try {
                JSONObject info = new JSONObject(infoJson);
                String title = info.optString("title", null);
                String artist = info.optString("artist", null);
                String album = info.optString("album", null);

                // Store metadata for when play() is called later
                pendingSessionTitle = title;
                pendingSessionArtist = artist;
                pendingSessionAlbum = album;

                // Kill local player — AudioService takes over
                // Release equalizer and visualizer bound to old audio session
                if (equalizer != null) { try { equalizer.setEnabled(false); equalizer.release(); } catch (Exception ignored) {} equalizer = null; }
                if (audioVisualizer != null) { runOnUiThread(() -> { try { audioVisualizer.setEnabled(false); audioVisualizer.release(); } catch (Exception ignored) {} audioVisualizer = null; }); }
                if (exoPlayer != null) {
                    final androidx.media3.exoplayer.ExoPlayer old = exoPlayer;
                    exoPlayer = null;
                    runOnUiThread(() -> { try { old.pause(); old.stop(); old.release(); } catch (Exception ignored) {} });
                    if (lastAudioUrl != null) {
                        Intent playIntent = new Intent(ShellActivity.this, AudioService.class);
                        playIntent.setAction(AudioService.ACTION_PLAY);
                        playIntent.putExtra("url", lastAudioUrl);
                        if (title != null) playIntent.putExtra("title", title);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(playIntent);
                        else startService(playIntent);
                    }
                }

                if (mediaSessionActive || lastAudioUrl != null) {
                    Intent intent = new Intent(ShellActivity.this, AudioService.class);
                    intent.setAction(AudioService.ACTION_SET_SESSION);
                    if (title != null) intent.putExtra("title", title);
                    if (artist != null) intent.putExtra("artist", artist);
                    if (album != null) intent.putExtra("album", album);
                    if (mediaSessionActive) startService(intent);
                    else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
                        else startService(intent);
                    }
                }
                mediaSessionActive = true;
            } catch (Exception e) { Log.e("iappyxOS", "setMediaSession: " + e.getMessage()); }
        }

        @JavascriptInterface
        public void pause() {
            if (mediaSessionActive) {
                Intent intent = new Intent(ShellActivity.this, AudioService.class);
                intent.setAction(AudioService.ACTION_PAUSE);
                startService(intent);
                return;
            }
            runOnUiThread(() -> {
                if (exoPlayer != null) { try { exoPlayer.pause(); } catch (Exception ignored) {} }
            });
        }

        @JavascriptInterface
        public void resume() {
            if (mediaSessionActive) {
                Intent intent = new Intent(ShellActivity.this, AudioService.class);
                intent.setAction(AudioService.ACTION_RESUME);
                startService(intent);
                return;
            }
            runOnUiThread(() -> {
                if (exoPlayer != null) { try { exoPlayer.play(); } catch (Exception ignored) {} }
            });
        }

        @JavascriptInterface
        public void seekTo(final double positionMs) {
            runOnUiThread(() -> {
                androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                if (p != null) { try { p.seekTo((long) positionMs); } catch (Exception ignored) {} }
            });
        }

        @JavascriptInterface
        public double getDuration() {
            try {
                java.util.concurrent.FutureTask<Long> task = new java.util.concurrent.FutureTask<>(() -> {
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    return p != null ? p.getDuration() : 0L;
                });
                runOnUiThread(task);
                return task.get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { return 0; }
        }

        @JavascriptInterface
        public double getCurrentPosition() {
            try {
                java.util.concurrent.FutureTask<Long> task = new java.util.concurrent.FutureTask<>(() -> {
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    return p != null ? p.getCurrentPosition() : 0L;
                });
                runOnUiThread(task);
                return task.get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { return 0; }
        }

        @JavascriptInterface
        public void onComplete(String callbackFn) {
            audioCompleteCallbackFn = callbackFn;
        }

        @JavascriptInterface
        public void onMetadata(String callbackFn) {
            audioMetadataCallbackFn = callbackFn;
        }

        // ── Audio Focus ──
        private android.media.AudioFocusRequest audioFocusRequest;

        @JavascriptInterface
        public void requestFocus(String callbackFn) {
            if (callbackFn == null) return;
            runOnUiThread(() -> {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am == null) return;
                android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
                audioFocusRequest = new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        if (!activityAlive) return;
                        String type;
                        switch (focusChange) {
                            case AudioManager.AUDIOFOCUS_GAIN: type = "gain"; break;
                            case AudioManager.AUDIOFOCUS_LOSS: type = "loss"; break;
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: type = "lossTransient"; break;
                            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: type = "duck"; break;
                            default: type = "unknown"; break;
                        }
                        fireEvent(callbackFn, "{\"type\":\"" + type + "\"}");
                    })
                    .build();
                int result = am.requestAudioFocus(audioFocusRequest);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    fireEvent(callbackFn, "{\"type\":\"gain\"}");
                }
            });
        }

        @JavascriptInterface
        public void abandonFocus() {
            runOnUiThread(() -> {
                if (audioFocusRequest != null) {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) am.abandonAudioFocusRequest(audioFocusRequest);
                    audioFocusRequest = null;
                }
            });
        }

        // ── Visualizer ──
        @JavascriptInterface
        public void startVisualizer(String callbackFn) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_AUDIO_RECORD, null);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_RECORD);
                return;
            }
            runOnUiThread(() -> {
                try {
                    if (audioVisualizer != null) { try { audioVisualizer.release(); } catch (Exception ignored) {} }
                    androidx.media3.exoplayer.ExoPlayer p = activePlayer();
                    int sessionId = p != null ? p.getAudioSessionId() : 0;
                    audioVisualizer = new android.media.audiofx.Visualizer(sessionId);
                    audioVisualizer.setCaptureSize(128); // small size = less overhead
                    audioVisualizer.setDataCaptureListener(new android.media.audiofx.Visualizer.OnDataCaptureListener() {
                        @Override
                        public void onWaveFormDataCapture(android.media.audiofx.Visualizer v, byte[] waveform, int samplingRate) {
                            if (!activityAlive) return;
                            // Also grab FFT in the same callback to avoid double events
                            byte[] fft = new byte[128];
                            try { v.getFft(fft); } catch (Exception ignored) {}
                            StringBuilder wf = new StringBuilder("[");
                            StringBuilder ff = new StringBuilder("[");
                            for (int i = 0; i < waveform.length; i++) {
                                if (i > 0) { wf.append(','); ff.append(','); }
                                wf.append(waveform[i] & 0xFF);
                                ff.append(i < fft.length ? (fft[i] & 0xFF) : 0);
                            }
                            wf.append(']'); ff.append(']');
                            fireEvent(callbackFn, "{\"waveform\":" + wf + ",\"fft\":" + ff + "}");
                        }
                        @Override
                        public void onFftDataCapture(android.media.audiofx.Visualizer v, byte[] fft, int samplingRate) {
                            // Handled in waveform callback above
                        }
                    }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 4, true, false); // lower rate, waveform only
                    audioVisualizer.setEnabled(true);
                } catch (Exception e) {
                    Log.e("iappyxOS", "startVisualizer: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void stopVisualizer() {
            runOnUiThread(() -> {
                if (audioVisualizer != null) {
                    try { audioVisualizer.setEnabled(false); audioVisualizer.release(); } catch (Exception ignored) {}
                    audioVisualizer = null;
                }
            });
        }

        // ── Recording ──
        @JavascriptInterface
        public void startRecording(String cbId) {
            Log.i("iappyxOS", "startRecording called, cbId=" + cbId);
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i("iappyxOS", "RECORD_AUDIO permission not granted, requesting...");
                pendingCallbacks.put(REQ_AUDIO_RECORD, cbId);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_RECORD);
                return;
            }
            Log.i("iappyxOS", "RECORD_AUDIO permission OK, starting...");
            doStartRecording(cbId);
        }

        private void doStartRecording(String cbId) {
            try {
                recordingCallbackId = cbId;
                File dir = getCacheDir();
                recordingFile = new File(dir, "rec_" + System.currentTimeMillis() + ".m4a");
                if (mediaRecorder != null) {
                    try { mediaRecorder.release(); } catch (Exception ignored) {}
                }
                mediaRecorder = new android.media.MediaRecorder();
                mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
                mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
                mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioEncodingBitRate(128000);
                mediaRecorder.setAudioSamplingRate(44100);
                mediaRecorder.setOutputFile(recordingFile.getAbsolutePath());
                mediaRecorder.prepare();
                mediaRecorder.start();
                Log.i("iappyxOS", "Recording started: " + recordingFile.getAbsolutePath());
                deliverResult(cbId, "{\"ok\":true,\"recording\":true}");
            } catch (Exception e) {
                Log.e("iappyxOS", "Recording failed: " + e.getMessage(), e);
                deliverResult(cbId, "{\"ok\":false,\"error\":\"recording failed\"}");
            }
        }

        @JavascriptInterface
        public void stopRecording(String cbId) {
            Log.i("iappyxOS", "stopRecording called, mediaRecorder=" + (mediaRecorder != null));
            try {
                if (mediaRecorder != null) {
                    try { mediaRecorder.stop(); } catch (Exception e) { Log.w("iappyxOS", "Recorder stop error (may not have been recording): " + e.getMessage()); }
                    try { mediaRecorder.release(); } catch (Exception ignored) {}
                    mediaRecorder = null;
                    Log.i("iappyxOS", "Recorder stopped, file=" + recordingFile + " size=" + (recordingFile != null ? recordingFile.length() : -1));
                }
                if (recordingFile != null && recordingFile.exists() && recordingFile.length() > 0) {
                    byte[] bytes = java.nio.file.Files.readAllBytes(recordingFile.toPath());
                    String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    Log.i("iappyxOS", "Recording base64 length: " + b64.length());
                    deliverResult(cbId, "{\"ok\":true,\"dataUrl\":\"data:audio/mp4;base64," + b64 + "\"}");
                    recordingFile.delete();
                } else {
                    Log.w("iappyxOS", "No recording file or empty");
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"no recording data\"}");
                }
            } catch (Exception e) {
                Log.e("iappyxOS", "Stop recording failed: " + e.getMessage(), e);
                deliverResult(cbId, "{\"ok\":false,\"error\":\"stop failed\"}");
                if (mediaRecorder != null) { try { mediaRecorder.release(); } catch (Exception ignored) {} mediaRecorder = null; }
            }
        }

        @JavascriptInterface
        public boolean isRecording() {
            return mediaRecorder != null;
        }

        @JavascriptInterface
        public void speechToText(final String cbId, final String lang) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_AUDIO_RECORD, cbId);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_RECORD);
                return;
            }
            pendingSpeechCallbackId = cbId;
            Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            if (lang != null && !lang.isEmpty()) {
                intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, lang);
            }
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            try {
                startActivityForResult(intent, REQ_SPEECH);
            } catch (Exception e) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"Speech recognition not available\"}");
            }
        }
    }

    // ── Contacts ──
    // Bug #1: use try-finally for cursor cleanup
    class ContactsBridge {
        @JavascriptInterface
        public void getContacts(String cbId) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_CONTACTS, cbId);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
                return;
            }
            new Thread(() -> {
                try {
                    JSONArray contacts = new JSONArray();
                    ContentResolver cr = getContentResolver();
                    Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI,
                        new String[]{ContactsContract.Contacts._ID,
                                     ContactsContract.Contacts.DISPLAY_NAME_PRIMARY},
                        null, null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC");
                    if (cursor != null) {
                        try {
                            while (cursor.moveToNext()) {
                                String id = cursor.getString(0);
                                String name = cursor.getString(1);
                                if (name == null) continue;
                                JSONArray phones = new JSONArray();
                                Cursor phoneCursor = cr.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                                    new String[]{id}, null);
                                if (phoneCursor != null) {
                                    try {
                                        while (phoneCursor.moveToNext()) {
                                            phones.put(phoneCursor.getString(0));
                                        }
                                    } finally { phoneCursor.close(); }
                                }
                                JSONArray emails = new JSONArray();
                                Cursor emailCursor = cr.query(
                                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                    new String[]{ContactsContract.CommonDataKinds.Email.ADDRESS},
                                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?",
                                    new String[]{id}, null);
                                if (emailCursor != null) {
                                    try {
                                        while (emailCursor.moveToNext()) {
                                            emails.put(emailCursor.getString(0));
                                        }
                                    } finally { emailCursor.close(); }
                                }
                                JSONObject contact = new JSONObject();
                                contact.put("name", name);
                                contact.put("phones", phones);
                                contact.put("emails", emails);
                                contacts.put(contact);
                            }
                        } finally { cursor.close(); }
                    }
                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("contacts", contacts);
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"contacts error\"}");
                }
            }).start();
        }
    }

    // ── SMS ──
    // Bug #5: store SMS params for retry after permission grant
    class SmsBridge {
        @JavascriptInterface
        public void send(final String number, final String message, final String cbId) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                if (cbId != null) pendingCallbacks.put(REQ_SMS, cbId);
                pendingSmsNumber = number;
                pendingSmsMessage = message;
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.SEND_SMS}, REQ_SMS);
                return;
            }
            try {
                SmsManager sm = SmsManager.getDefault();
                ArrayList<String> parts = sm.divideMessage(message);
                sm.sendMultipartTextMessage(number, null, parts, null, null);
                if (cbId != null) deliverResult(cbId, "{\"ok\":true}");
            } catch (Exception e) {
                if (cbId != null) deliverResult(cbId, "{\"ok\":false,\"error\":\"sms failed\"}");
            }
        }
    }

    // ── Calendar ──
    // Bug #1: use try-finally for cursor; Bug #5: fix addEvent permission handling
    class CalendarBridge {
        @JavascriptInterface
        public void getEvents(String cbId, String startMs, String endMs) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.READ_CALENDAR)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingCallbacks.put(REQ_CALENDAR_READ, cbId);
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.READ_CALENDAR}, REQ_CALENDAR_READ);
                return;
            }
            new Thread(() -> {
                try {
                    JSONArray events = new JSONArray();
                    Uri uri = android.provider.CalendarContract.Events.CONTENT_URI;
                    String sel = null;
                    String[] selArgs = null;
                    if (startMs != null && endMs != null) {
                        sel = android.provider.CalendarContract.Events.DTSTART + ">=? AND " +
                              android.provider.CalendarContract.Events.DTEND + "<=?";
                        selArgs = new String[]{startMs, endMs};
                    }
                    Cursor c = getContentResolver().query(uri,
                        new String[]{"_id", "title", "dtstart", "dtend", "description", "allDay"},
                        sel, selArgs, "dtstart ASC");
                    if (c != null) {
                        try {
                            while (c.moveToNext()) {
                                JSONObject ev = new JSONObject();
                                ev.put("id", c.getString(0));
                                ev.put("title", c.getString(1) != null ? c.getString(1) : "");
                                ev.put("start", c.getLong(2));
                                ev.put("end", c.getLong(3));
                                ev.put("allDay", c.getInt(5) == 1);
                                events.put(ev);
                            }
                        } finally { c.close(); }
                    }
                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("events", events);
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"calendar error\"}");
                }
            }).start();
        }

        // Bug #5: store callback for permission result
        @JavascriptInterface
        public void addEvent(String cbId, String title, String startMs, String endMs, String description) {
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.WRITE_CALENDAR)
                    != PackageManager.PERMISSION_GRANTED) {
                if (cbId != null) pendingCallbacks.put(REQ_CALENDAR_WRITE, cbId);
                pendingCalendarEvent = new String[]{title, startMs, endMs, description};
                ActivityCompat.requestPermissions(ShellActivity.this,
                    new String[]{Manifest.permission.WRITE_CALENDAR,
                                 Manifest.permission.READ_CALENDAR}, REQ_CALENDAR_WRITE);
                return;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, Long.parseLong(startMs))
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, Long.parseLong(endMs))
                    .putExtra(android.provider.CalendarContract.Events.TITLE, title)
                    .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, description != null ? description : "");
                startActivity(intent);
                deliverResult(cbId, "{\"ok\":true}");
            } catch (Exception e) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // ── Biometric ──
    class BiometricBridge {
        @JavascriptInterface
        public void authenticate(final String title, final String subtitle, final String cbId) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    CancellationSignal cancel = new CancellationSignal();
                    BiometricPrompt prompt = new BiometricPrompt.Builder(ShellActivity.this)
                        .setTitle(title != null ? title : "Authenticate")
                        .setSubtitle(subtitle != null ? subtitle : "")
                        .setNegativeButton("Cancel",
                            getMainExecutor(),
                            (dialog, which) -> deliverResult(cbId, "{\"ok\":false,\"error\":\"cancelled\"}"))
                        .build();
                    prompt.authenticate(cancel, getMainExecutor(),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                                deliverResult(cbId, "{\"ok\":true}");
                            }
                            @Override public void onAuthenticationFailed() {
                                // Don't deliver yet — user can retry
                            }
                            @Override public void onAuthenticationError(int code, CharSequence msg) {
                                deliverResult(cbId, "{\"ok\":false,\"error\":\"auth error\"}");
                            }
                        });
                } else {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"requires Android 9+\"}");
                }
            });
        }
    }


    // ── NFC ──
    class NfcBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            return nfcAdapter != null && nfcAdapter.isEnabled();
        }

        @JavascriptInterface
        public void startReading(final String callbackFn) {
            if (nfcAdapter == null) {
                fireEvent(callbackFn, "{\"error\":\"NFC not available on this device\"}");
                return;
            }
            if (!nfcAdapter.isEnabled()) {
                fireEvent(callbackFn, "{\"error\":\"NFC is disabled. Enable it in Settings.\"}");
                return;
            }
            pendingNfcCallbackFn = callbackFn;
            runOnUiThread(() -> {
                try { nfcAdapter.enableForegroundDispatch(ShellActivity.this, makeNfcPendingIntent(), null, null); }
                catch (Exception e) {
                    fireEvent(callbackFn, "{\"error\":\"NFC dispatch failed\"}");
                }
            });
        }

        @JavascriptInterface
        public void stopReading() {
            pendingNfcCallbackFn = null;
            runOnUiThread(() -> {
                if (nfcAdapter != null) {
                    try { nfcAdapter.disableForegroundDispatch(ShellActivity.this); }
                    catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public void writeText(final String text, final String cbId) {
            if (nfcAdapter == null) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"NFC not available on this device\"}");
                return;
            }
            pendingNfcReadCallbackFn = pendingNfcCallbackFn; // save read callback
            pendingNfcCallbackFn = "window._iappyxNfcWriteCb_" + cbId;
            runOnUiThread(() -> {
                pendingNfcWriteText = text;
                pendingNfcWriteCbId = cbId;
                try { nfcAdapter.enableForegroundDispatch(ShellActivity.this, makeNfcPendingIntent(), null, null); }
                catch (Exception e) {
                    pendingNfcWriteText = null; pendingNfcWriteCbId = null;
                    // Restore read callback on failure
                    if (pendingNfcReadCallbackFn != null) { pendingNfcCallbackFn = pendingNfcReadCallbackFn; pendingNfcReadCallbackFn = null; }
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"NFC dispatch failed\"}");
                }
            });
        }

        @JavascriptInterface
        public void writeUri(final String uri, final String cbId) {
            if (nfcAdapter == null) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"NFC not available on this device\"}");
                return;
            }
            pendingNfcReadCallbackFn = pendingNfcCallbackFn; // save read callback
            pendingNfcCallbackFn = "window._iappyxNfcWriteCb_" + cbId;
            runOnUiThread(() -> {
                pendingNfcWriteUri = uri;
                pendingNfcWriteCbId = cbId;
                try { nfcAdapter.enableForegroundDispatch(ShellActivity.this, makeNfcPendingIntent(), null, null); }
                catch (Exception e) {
                    pendingNfcWriteUri = null; pendingNfcWriteCbId = null;
                    if (pendingNfcReadCallbackFn != null) { pendingNfcCallbackFn = pendingNfcReadCallbackFn; pendingNfcReadCallbackFn = null; }
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"NFC dispatch failed\"}");
                }
            });
        }
    }

    // ── SQLite ──
    class SqliteBridge {
        private final java.util.concurrent.locks.ReentrantLock txLock = new java.util.concurrent.locks.ReentrantLock();
        private volatile Thread txOwner;

        private synchronized android.database.sqlite.SQLiteDatabase getDb() {
            if (sqliteDb == null || !sqliteDb.isOpen()) {
                sqliteDb = ShellActivity.this.openOrCreateDatabase("iappyx_app.db",
                    MODE_PRIVATE, null);
            }
            return sqliteDb;
        }

        @JavascriptInterface
        public synchronized String exec(String sql, String paramsJson) {
            if (sql == null || sql.isEmpty()) return "{\"ok\":false,\"error\":\"empty SQL\"}";
            try {
                android.database.sqlite.SQLiteDatabase d = getDb();
                if (paramsJson != null && !paramsJson.isEmpty()) {
                    JSONArray arr = new JSONArray(paramsJson);
                    String[] params = new String[arr.length()];
                    for (int i = 0; i < arr.length(); i++) params[i] = arr.getString(i);
                    d.execSQL(sql, params);
                } else {
                    d.execSQL(sql);
                }
                return "{\"ok\":true}";
            } catch (Exception e) {
                try {
                    JSONObject err = new JSONObject();
                    err.put("ok", false);
                    err.put("error", e.getMessage());
                    return err.toString();
                } catch (Exception e2) { return "{\"ok\":false,\"error\":\"unknown\"}"; }
            }
        }

        @JavascriptInterface
        public synchronized String query(String sql, String paramsJson) {
            if (sql == null || sql.isEmpty()) return "{\"ok\":false,\"error\":\"empty SQL\"}";
            Cursor c = null;
            try {
                android.database.sqlite.SQLiteDatabase d = getDb();
                String[] params = null;
                if (paramsJson != null && !paramsJson.isEmpty()) {
                    JSONArray arr = new JSONArray(paramsJson);
                    params = new String[arr.length()];
                    for (int i = 0; i < arr.length(); i++) params[i] = arr.getString(i);
                }
                c = d.rawQuery(sql, params);
                JSONArray rows = new JSONArray();
                String[] cols = c.getColumnNames();
                int count = 0;
                boolean truncated = false;
                while (c.moveToNext()) {
                    if (count >= 5000) { truncated = true; break; }
                    JSONObject row = new JSONObject();
                    for (int i = 0; i < cols.length; i++) {
                        int type = c.getType(i);
                        if (type == Cursor.FIELD_TYPE_NULL) row.put(cols[i], JSONObject.NULL);
                        else if (type == Cursor.FIELD_TYPE_INTEGER) row.put(cols[i], c.getLong(i));
                        else if (type == Cursor.FIELD_TYPE_FLOAT) row.put(cols[i], c.getDouble(i));
                        else row.put(cols[i], c.getString(i));
                    }
                    rows.put(row);
                    count++;
                }
                JSONObject result = new JSONObject();
                result.put("ok", true);
                result.put("rows", rows);
                if (truncated) result.put("truncated", true);
                return result.toString();
            } catch (Exception e) {
                try {
                    JSONObject err = new JSONObject();
                    err.put("ok", false);
                    err.put("error", e.getMessage());
                    return err.toString();
                } catch (Exception e2) { return "{\"ok\":false,\"error\":\"unknown\"}"; }
            } finally {
                if (c != null) c.close();
            }
        }
        @JavascriptInterface
        public String beginTransaction() {
            txLock.lock();
            txOwner = Thread.currentThread();
            try {
                getDb().beginTransaction();
                return "{\"ok\":true}";
            } catch (Exception e) { txOwner = null; txLock.unlock(); return "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}"; }
        }

        @JavascriptInterface
        public String commit() {
            try {
                getDb().setTransactionSuccessful();
                getDb().endTransaction();
                return "{\"ok\":true}";
            } catch (Exception e) { return "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}"; }
            finally { txOwner = null; if (txLock.isHeldByCurrentThread()) txLock.unlock(); }
        }

        @JavascriptInterface
        public String rollback() {
            try {
                getDb().endTransaction();
                return "{\"ok\":true}";
            } catch (Exception e) { return "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}"; }
            finally { txOwner = null; if (txLock.isHeldByCurrentThread()) txLock.unlock(); }
        }
    }

    // ── Capabilities ──
    // ── Download Manager ──
    class DownloadBridge {
        @JavascriptInterface
        public void enqueue(String url, String filename, String callbackFn) {
            if (url == null || url.isEmpty()) return;
            try {
                android.app.DownloadManager dm = (android.app.DownloadManager)
                    getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm == null) {
                    if (callbackFn != null) fireEvent(callbackFn, "{\"error\":\"DownloadManager not available\"}");
                    return;
                }
                String fnameRaw = (filename != null && !filename.isEmpty()) ? filename :
                    url.substring(url.lastIndexOf('/') + 1).replaceAll("[?#].*", "");
                final String fname = fnameRaw.isEmpty() ? "download_" + System.currentTimeMillis() : fnameRaw;

                android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(Uri.parse(url));
                req.setTitle(fname);
                req.setDescription("Downloading...");
                req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fname);
                long downloadId = dm.enqueue(req);

                if (callbackFn != null) {
                    // Monitor progress in background thread
                    final long dlId = downloadId;
                    final android.app.DownloadManager fdm = dm;
                    new Thread(() -> {
                        boolean stop = false;
                        while (!stop && activityAlive) {
                            android.database.Cursor c = fdm.query(
                                new android.app.DownloadManager.Query().setFilterById(dlId));
                            if (c != null && c.moveToFirst()) {
                                int status = c.getInt(c.getColumnIndexOrThrow(
                                    android.app.DownloadManager.COLUMN_STATUS));
                                long total = c.getLong(c.getColumnIndexOrThrow(
                                    android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                                long downloaded = c.getLong(c.getColumnIndexOrThrow(
                                    android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));

                                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                                    c.close();
                                    fireEvent(callbackFn, "{\"ok\":true,\"id\":" + dlId +
                                        ",\"filename\":\"" + fname.replace("\"", "\\\"") +
                                        "\",\"status\":\"complete\",\"progress\":100}");
                                    stop = true;
                                } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                                    int reason = c.getInt(c.getColumnIndexOrThrow(
                                        android.app.DownloadManager.COLUMN_REASON));
                                    c.close();
                                    fireEvent(callbackFn, "{\"ok\":false,\"id\":" + dlId +
                                        ",\"status\":\"failed\",\"error\":\"reason " + reason + "\"}");
                                    stop = true;
                                } else {
                                    c.close();
                                    if (total > 0) {
                                        int pct = (int) (downloaded * 100 / total);
                                        fireEvent(callbackFn, "{\"ok\":true,\"id\":" + dlId +
                                            ",\"status\":\"downloading\",\"progress\":" + pct +
                                            ",\"downloaded\":" + downloaded + ",\"total\":" + total + "}");
                                    }
                                }
                            } else {
                                if (c != null) c.close();
                                stop = true;
                            }
                            if (!stop) {
                                try { Thread.sleep(500); } catch (InterruptedException ignored) { break; }
                            }
                        }
                    }).start();
                }
                Log.i("iappyxOS", "Download queued: " + url + " → " + fname);
            } catch (Exception e) {
                Log.e("iappyxOS", "download: " + e.getMessage());
                if (callbackFn != null) fireEvent(callbackFn, "{\"ok\":false,\"error\":\"" +
                    escapeJson(e.getMessage()) + "\"}");
            }
        }

        @JavascriptInterface
        public void cancel(String idStr) {
            try {
                long id = Long.parseLong(idStr);
                android.app.DownloadManager dm = (android.app.DownloadManager)
                    getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) dm.remove(id);
            } catch (Exception e) { Log.e("iappyxOS", "download.cancel: " + e.getMessage()); }
        }
    }

    // ── Media Scanner ──
    class MediaBridge {
        private static final int REQ_MEDIA = 1020;

        private void requestMediaPermission() {
            if (Build.VERSION.SDK_INT >= 33) {
                java.util.List<String> needed = new java.util.ArrayList<>();
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.READ_MEDIA_IMAGES")
                        != PackageManager.PERMISSION_GRANTED) needed.add("android.permission.READ_MEDIA_IMAGES");
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.READ_MEDIA_VIDEO")
                        != PackageManager.PERMISSION_GRANTED) needed.add("android.permission.READ_MEDIA_VIDEO");
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.READ_MEDIA_AUDIO")
                        != PackageManager.PERMISSION_GRANTED) needed.add("android.permission.READ_MEDIA_AUDIO");
                if (!needed.isEmpty()) {
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        needed.toArray(new String[0]), REQ_MEDIA);
                }
            } else {
                if (ContextCompat.checkSelfPermission(ShellActivity.this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(ShellActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA);
                }
            }
        }

        /** Pick an image from the gallery. Returns dataUrl via callback. */
        @JavascriptInterface
        public void pickImage(String cbId) {
            if (cbId == null) return;
            requestMediaPermission();
            new Thread(() -> {
                try {
                    runOnUiThread(() -> {
                        // Cancel previous pending pick if any
                        if (pendingMediaCbId != null) {
                            deliverResult(pendingMediaCbId, "{\"ok\":false,\"error\":\"cancelled\"}");
                        }
                        Intent intent = new Intent(Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        pendingMediaCbId = cbId;
                        startActivityForResult(intent, REQ_MEDIA_PICK);
                    });
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }).start();
        }

        /** Get recent images from the device gallery. */
        @JavascriptInterface
        public void getImages(String cbId, double limitD) {
            if (cbId == null) return;
            requestMediaPermission();
            int limit = (int) limitD;
            if (limit <= 0) limit = 20;
            final int maxItems = Math.min(limit, 100);
            new Thread(() -> {
                try {
                    ContentResolver cr = getContentResolver();
                    String[] proj = {
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.SIZE,
                        MediaStore.Images.Media.WIDTH,
                        MediaStore.Images.Media.HEIGHT,
                        MediaStore.Images.Media.MIME_TYPE
                    };
                    Cursor c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                        null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
                    JSONArray images = new JSONArray();
                    try {
                        if (c != null) {
                            int count = 0;
                            while (c.moveToNext() && count < maxItems) {
                                long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                                String name = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                                long date = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED));
                                long size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE));
                                int width = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH));
                                int height = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT));
                                String mime = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE));
                                JSONObject img = new JSONObject();
                                img.put("id", id);
                                img.put("name", name != null ? name : "");
                                img.put("date", date * 1000);
                                img.put("size", size);
                                img.put("width", width);
                                img.put("height", height);
                                img.put("mime", mime != null ? mime : "");
                                images.put(img);
                                count++;
                            }
                        }
                    } finally { if (c != null) c.close(); }
                    deliverResult(cbId, "{\"ok\":true,\"images\":" + images.toString() + "}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }).start();
        }

        /** Load a specific image by ID as base64 dataUrl (thumbnail). */
        @JavascriptInterface
        public void loadThumbnail(String cbId, double idD) {
            if (cbId == null) return;
            long imageId = (long) idD;
            new Thread(() -> {
                try {
                    Uri uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
                    Bitmap bmp;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        bmp = getContentResolver().loadThumbnail(uri,
                            new android.util.Size(320, 320), null);
                    } else {
                        bmp = MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(),
                            imageId, MediaStore.Images.Thumbnails.MINI_KIND, null);
                    }
                    if (bmp == null) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"thumbnail not available\"}");
                        return;
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    bmp.recycle();
                    String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    deliverResult(cbId, "{\"ok\":true,\"dataUrl\":\"data:image/jpeg;base64," + b64 + "\"}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }).start();
        }

        /** Load full-resolution image by ID as base64 dataUrl (max 1200px wide). */
        @JavascriptInterface
        public void loadImage(String cbId, double idD) {
            if (cbId == null) return;
            long imageId = (long) idD;
            new Thread(() -> {
                try {
                    Uri uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageId);
                    java.io.InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"image not found\"}");
                        return;
                    }
                    Bitmap original;
                    try { original = android.graphics.BitmapFactory.decodeStream(is); }
                    finally { is.close(); }
                    if (original == null) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"decode failed\"}");
                        return;
                    }
                    // Scale down if wider than 1200px
                    Bitmap bmp = original;
                    if (original.getWidth() > 1200) {
                        float scale = 1200f / original.getWidth();
                        bmp = Bitmap.createScaledBitmap(original,
                            1200, (int)(original.getHeight() * scale), true);
                        original.recycle();
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                    bmp.recycle();
                    String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    deliverResult(cbId, "{\"ok\":true,\"dataUrl\":\"data:image/jpeg;base64," + b64 + "\"}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }).start();
        }

        /** Get recent videos metadata. */
        @JavascriptInterface
        public void getVideos(String cbId, double limitD) {
            if (cbId == null) return;
            requestMediaPermission();
            int limit = (int) limitD;
            if (limit <= 0) limit = 20;
            final int maxItems = Math.min(limit, 100);
            new Thread(() -> {
                try {
                    String[] proj = {
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.DATE_ADDED,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DURATION,
                        MediaStore.Video.Media.WIDTH,
                        MediaStore.Video.Media.HEIGHT,
                        MediaStore.Video.Media.MIME_TYPE
                    };
                    Cursor c = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj,
                        null, null, MediaStore.Video.Media.DATE_ADDED + " DESC");
                    JSONArray videos = new JSONArray();
                    try {
                        if (c != null) {
                            int count = 0;
                            while (c.moveToNext() && count < maxItems) {
                                JSONObject vid = new JSONObject();
                                vid.put("id", c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)));
                                vid.put("name", c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)));
                                vid.put("date", c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)) * 1000);
                                vid.put("size", c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)));
                                vid.put("duration", c.getLong(c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)));
                                vid.put("width", c.getInt(c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)));
                                vid.put("height", c.getInt(c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)));
                                vid.put("mime", c.getString(c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)));
                                videos.put(vid);
                                count++;
                            }
                        }
                    } finally { if (c != null) c.close(); }
                    deliverResult(cbId, "{\"ok\":true,\"videos\":" + videos.toString() + "}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }).start();
        }

        /** Get audio files metadata. */
        @JavascriptInterface
        public void getAudio(String cbId, double limitD) {
            if (cbId == null) return;
            requestMediaPermission();
            int limit = (int) limitD;
            if (limit <= 0) limit = 20;
            final int maxItems = Math.min(limit, 100);
            new Thread(() -> {
                try {
                    String[] proj = {
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DATE_ADDED,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.MIME_TYPE
                    };
                    Cursor c = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                        null, null, MediaStore.Audio.Media.DATE_ADDED + " DESC");
                    JSONArray audios = new JSONArray();
                    try {
                        if (c != null) {
                            int count = 0;
                            while (c.moveToNext() && count < maxItems) {
                                JSONObject aud = new JSONObject();
                                aud.put("id", c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)));
                                aud.put("name", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)));
                                aud.put("title", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)));
                                aud.put("artist", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)));
                                aud.put("album", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)));
                                aud.put("date", c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)) * 1000);
                                aud.put("size", c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)));
                                aud.put("duration", c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)));
                                aud.put("mime", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)));
                                audios.put(aud);
                                count++;
                            }
                        }
                    } finally { if (c != null) c.close(); }
                    deliverResult(cbId, "{\"ok\":true,\"audio\":" + audios.toString() + "}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }).start();
        }

        /** Play audio by MediaStore ID using the audio bridge. */
        @JavascriptInterface
        public void playAudio(double idD) {
            long audioId = (long) idD;
            Uri uri = android.content.ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId);
            String uriStr = uri.toString();
            if (mediaSessionActive) {
                // Delegate to AudioService
                Intent intent = new Intent(ShellActivity.this, AudioService.class);
                intent.setAction(AudioService.ACTION_PLAY);
                intent.putExtra("url", uriStr);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
                else startService(intent);
                return;
            }
            runOnUiThread(() -> {
                try {
                    // Release visualizer bound to old audio session (equalizer is on AudioBridge, released via its own methods)
                    if (audioVisualizer != null) { try { audioVisualizer.setEnabled(false); audioVisualizer.release(); } catch (Exception ignored) {} audioVisualizer = null; }
                    if (exoPlayer != null) { try { exoPlayer.pause(); exoPlayer.stop(); exoPlayer.release(); } catch (Exception ignored) {} }
                    exoPlayer = new androidx.media3.exoplayer.ExoPlayer.Builder(ShellActivity.this).build();
                    exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri));
                    exoPlayer.prepare();
                    exoPlayer.play();
                } catch (Exception e) { Log.e("iappyxOS", "media.playAudio: " + e.getMessage()); }
            });
        }

        /** Save a base64 image to the device photo gallery. Returns {ok,uri} or {ok:false,error}. */
        @JavascriptInterface
        public void saveToGallery(String cbId, String base64, String filename) {
            if (cbId == null) return;
            new Thread(() -> {
                try {
                    String clean = base64;
                    String mimeType = "image/jpeg";
                    if (clean.contains(",")) {
                        String header = clean.substring(0, clean.indexOf(","));
                        if (header.contains("png")) mimeType = "image/png";
                        else if (header.contains("webp")) mimeType = "image/webp";
                        clean = clean.substring(clean.indexOf(",") + 1);
                    }
                    byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
                    String ext = mimeType.equals("image/png") ? ".png" : mimeType.equals("image/webp") ? ".webp" : ".jpg";
                    String name = (filename != null && !filename.isEmpty()) ? filename :
                        "iappyx_" + System.currentTimeMillis() + ext;

                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/iappyxOS");
                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"failed to create gallery entry\"}");
                        return;
                    }
                    try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) {
                            deliverResult(cbId, "{\"ok\":false,\"error\":\"failed to open output stream\"}");
                            return;
                        }
                        os.write(bytes);
                    }
                    deliverResult(cbId, "{\"ok\":true,\"uri\":\"" + uri.toString() + "\"}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }).start();
        }

        /** Get metadata for a media file by MediaStore ID. type = "image", "video", or "audio". */
        @JavascriptInterface
        public void getMetadata(String cbId, double idD, String type) {
            if (cbId == null) return;
            long id = (long) idD;
            new Thread(() -> {
                try {
                    Uri uri;
                    if ("video".equals(type)) {
                        uri = android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    } else if ("audio".equals(type)) {
                        uri = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    } else {
                        uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    }
                    android.media.MediaMetadataRetriever mmr = new android.media.MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(ShellActivity.this, uri);
                        JSONObject meta = new JSONObject();
                        meta.put("ok", true);
                        String duration = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (duration != null) meta.put("duration", Long.parseLong(duration));
                        String bitrate = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE);
                        if (bitrate != null) meta.put("bitrate", Long.parseLong(bitrate));
                        String width = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                        if (width != null) meta.put("width", Integer.parseInt(width));
                        String height = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                        if (height != null) meta.put("height", Integer.parseInt(height));
                        String title = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE);
                        if (title != null) meta.put("title", title);
                        String artist = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        if (artist != null) meta.put("artist", artist);
                        String album = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM);
                        if (album != null) meta.put("album", album);
                        String genre = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE);
                        if (genre != null) meta.put("genre", genre);
                        String date = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE);
                        if (date != null) meta.put("date", date);
                        String mime = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                        if (mime != null) meta.put("mimeType", mime);
                        String rotation = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                        if (rotation != null) meta.put("rotation", Integer.parseInt(rotation));
                        deliverResult(cbId, meta.toString());
                    } finally {
                        mmr.release();
                    }
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }).start();
        }
    }

    // ── WiFi Direct ──
    class WifiDirectBridge {

        @SuppressWarnings("MissingPermission")
        private void ensureP2pInit() {
            if (wifiP2pManager != null) return;
            wifiP2pManager = (android.net.wifi.p2p.WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
            wifiP2pChannel = wifiP2pManager.initialize(ShellActivity.this, getMainLooper(), null);
            // Register receiver
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction(android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            filter.addAction(android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            wifiP2pReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (!activityAlive) return;
                    String action = intent.getAction();
                    if (android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                        if (wifiP2pManager != null && wifiP2pChannel != null) {
                            try {
                                wifiP2pManager.requestPeers(wifiP2pChannel, peerList -> {
                                    if (wifiP2pPeerCallbackFn == null) return;
                                    try {
                                        JSONArray arr = new JSONArray();
                                        for (android.net.wifi.p2p.WifiP2pDevice d : peerList.getDeviceList()) {
                                            JSONObject p = new JSONObject();
                                            p.put("name", d.deviceName);
                                            p.put("address", d.deviceAddress);
                                            String status;
                                            switch (d.status) {
                                                case android.net.wifi.p2p.WifiP2pDevice.CONNECTED: status = "connected"; break;
                                                case android.net.wifi.p2p.WifiP2pDevice.INVITED: status = "invited"; break;
                                                case android.net.wifi.p2p.WifiP2pDevice.AVAILABLE: status = "available"; break;
                                                default: status = "unavailable"; break;
                                            }
                                            p.put("status", status);
                                            arr.put(p);
                                        }
                                        JSONObject evt = new JSONObject();
                                        evt.put("event", "peers");
                                        evt.put("peers", arr);
                                        fireEvent(wifiP2pPeerCallbackFn, evt.toString());
                                    } catch (Exception ignored) {}
                                });
                            } catch (SecurityException ignored) {}
                        }
                    } else if (android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                        if (wifiP2pManager != null && wifiP2pChannel != null) {
                            wifiP2pManager.requestConnectionInfo(wifiP2pChannel, info -> {
                                if (wifiP2pConnectionCallbackFn == null) return;
                                try {
                                    JSONObject evt = new JSONObject();
                                    if (info != null && info.groupFormed) {
                                        evt.put("connected", true);
                                        evt.put("isGroupOwner", info.isGroupOwner);
                                        evt.put("groupOwnerAddress", info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : "");
                                    } else {
                                        evt.put("connected", false);
                                    }
                                    fireEvent(wifiP2pConnectionCallbackFn, evt.toString());
                                } catch (Exception ignored) {}
                            });
                        }
                    }
                }
            };
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(wifiP2pReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(wifiP2pReceiver, filter);
            }
        }

        private boolean ensureWifiDirectPermissions(Runnable action, String cbId) {
            java.util.List<String> needed = new java.util.ArrayList<>();
            if (ContextCompat.checkSelfPermission(ShellActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.NEARBY_WIFI_DEVICES") != PackageManager.PERMISSION_GRANTED) {
                    needed.add("android.permission.NEARBY_WIFI_DEVICES");
                }
            }
            if (needed.isEmpty()) return true;
            wifiP2pPendingAction = action;
            wifiP2pPendingCbId = cbId;
            ActivityCompat.requestPermissions(ShellActivity.this, needed.toArray(new String[0]), REQ_WIFI_DIRECT);
            return false;
        }

        @JavascriptInterface
        public void createGroup(String cbId) {
            runOnUiThread(() -> {
                ensureP2pInit();
                if (!ensureWifiDirectPermissions(() -> createGroup(cbId), cbId)) return;
                try {
                    wifiP2pManager.createGroup(wifiP2pChannel, new android.net.wifi.p2p.WifiP2pManager.ActionListener() {
                        @Override public void onSuccess() { deliverResult(cbId, "{\"ok\":true}"); }
                        @Override public void onFailure(int reason) { deliverResult(cbId, "{\"ok\":false,\"error\":\"createGroup failed: " + reason + "\"}"); }
                    });
                } catch (SecurityException e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"permission denied\"}");
                }
            });
        }

        @JavascriptInterface
        public void removeGroup() {
            runOnUiThread(() -> {
                if (wifiP2pManager != null && wifiP2pChannel != null) {
                    try { wifiP2pManager.removeGroup(wifiP2pChannel, null); } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public void discoverPeers(String callbackFn) {
            runOnUiThread(() -> {
                ensureP2pInit();
                wifiP2pPeerCallbackFn = callbackFn;
                if (!ensureWifiDirectPermissions(() -> discoverPeers(callbackFn), null)) return;
                try {
                    wifiP2pManager.discoverPeers(wifiP2pChannel, new android.net.wifi.p2p.WifiP2pManager.ActionListener() {
                        @Override public void onSuccess() {}
                        @Override public void onFailure(int reason) {
                            fireEvent(callbackFn, "{\"event\":\"error\",\"error\":\"discovery failed: " + reason + "\"}");
                        }
                    });
                } catch (SecurityException e) {
                    fireEvent(callbackFn, "{\"event\":\"error\",\"error\":\"permission denied\"}");
                }
            });
        }

        @JavascriptInterface
        public void stopDiscovery() {
            runOnUiThread(() -> {
                if (wifiP2pManager != null && wifiP2pChannel != null) {
                    try { wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, null); } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public void connect(String address, String cbId) {
            runOnUiThread(() -> {
                ensureP2pInit();
                if (!ensureWifiDirectPermissions(() -> connect(address, cbId), cbId)) return;
                try {
                    android.net.wifi.p2p.WifiP2pConfig config = new android.net.wifi.p2p.WifiP2pConfig();
                    config.deviceAddress = address;
                    wifiP2pManager.connect(wifiP2pChannel, config, new android.net.wifi.p2p.WifiP2pManager.ActionListener() {
                        @Override public void onSuccess() { deliverResult(cbId, "{\"ok\":true}"); }
                        @Override public void onFailure(int reason) { deliverResult(cbId, "{\"ok\":false,\"error\":\"connect failed: " + reason + "\"}"); }
                    });
                } catch (SecurityException e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"permission denied\"}");
                }
            });
        }

        @JavascriptInterface
        public void disconnect() {
            runOnUiThread(() -> {
                if (wifiP2pManager != null && wifiP2pChannel != null) {
                    try { wifiP2pManager.stopPeerDiscovery(wifiP2pChannel, null); } catch (Exception ignored) {}
                    try { wifiP2pManager.removeGroup(wifiP2pChannel, null); } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public void getConnectionInfo(String cbId) {
            runOnUiThread(() -> {
                ensureP2pInit();
                wifiP2pManager.requestConnectionInfo(wifiP2pChannel, info -> {
                    try {
                        JSONObject r = new JSONObject();
                        if (info != null && info.groupFormed) {
                            r.put("connected", true);
                            r.put("isGroupOwner", info.isGroupOwner);
                            r.put("groupOwnerAddress", info.groupOwnerAddress != null ? info.groupOwnerAddress.getHostAddress() : "");
                        } else {
                            r.put("connected", false);
                        }
                        deliverResult(cbId, r.toString());
                    } catch (Exception e) {
                        deliverResult(cbId, "{\"connected\":false}");
                    }
                });
            });
        }

        @JavascriptInterface
        public void onConnectionChanged(String callbackFn) {
            wifiP2pConnectionCallbackFn = callbackFn;
            runOnUiThread(() -> ensureP2pInit());
        }
    }

    // ── HTTP Client (OkHttp, supports self-signed certs) ──
    class HttpClientBridge {
        private okhttp3.OkHttpClient defaultClient;
        private okhttp3.OkHttpClient trustAllClient;
        private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<okhttp3.Cookie>> cookieStore = new java.util.concurrent.ConcurrentHashMap<>();
        private final Object cookieLock = new Object();
        private final okhttp3.CookieJar cookieJar = new okhttp3.CookieJar() {
            @Override public void saveFromResponse(okhttp3.HttpUrl url, java.util.List<okhttp3.Cookie> cookies) {
                synchronized (cookieLock) {
                    for (okhttp3.Cookie c : cookies) {
                        String domain = c.domain();
                        java.util.List<okhttp3.Cookie> existing = cookieStore.get(domain);
                        if (existing == null) { existing = new java.util.ArrayList<>(); cookieStore.put(domain, existing); }
                        existing.removeIf(e -> e.name().equals(c.name()) && e.path().equals(c.path()));
                        existing.add(c);
                    }
                }
            }
            @Override public java.util.List<okhttp3.Cookie> loadForRequest(okhttp3.HttpUrl url) {
                synchronized (cookieLock) {
                    java.util.List<okhttp3.Cookie> result = new java.util.ArrayList<>();
                    long now = System.currentTimeMillis();
                    for (java.util.Map.Entry<String, java.util.List<okhttp3.Cookie>> entry : cookieStore.entrySet()) {
                        String domain = entry.getKey();
                        if (!url.host().equals(domain) && !url.host().endsWith("." + domain)) continue;
                        java.util.Iterator<okhttp3.Cookie> it = entry.getValue().iterator();
                        while (it.hasNext()) {
                            okhttp3.Cookie c = it.next();
                            if (c.expiresAt() < now) { it.remove(); continue; }
                            if (!url.encodedPath().startsWith(c.path())) continue;
                            result.add(c);
                        }
                    }
                    return result;
                }
            }
        };

        private okhttp3.OkHttpClient getClient(JSONObject opts) throws Exception {
            int timeout = opts.optInt("timeout", 15000);
            String pin = opts.optString("pinFingerprint", "");
            boolean trustAll = opts.optBoolean("trustAllCerts", false);

            if (!pin.isEmpty()) {
                return buildClient(timeout, pinTrustManager(pin));
            } else if (trustAll) {
                return buildClient(timeout, trustAllTrustManager());
            } else {
                return new okhttp3.OkHttpClient.Builder()
                    .cookieJar(cookieJar)
                    .connectTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .writeTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .followRedirects(true).followSslRedirects(true).build();
            }
        }

        private okhttp3.OkHttpClient buildClient(int timeout, javax.net.ssl.X509TrustManager tm) throws Exception {
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{tm}, new java.security.SecureRandom());
            return new okhttp3.OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .sslSocketFactory(ctx.getSocketFactory(), tm)
                .hostnameVerifier((h, s) -> true)
                .connectTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                .followRedirects(true).followSslRedirects(true).build();
        }

        private javax.net.ssl.X509TrustManager trustAllTrustManager() {
            return new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String t) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String t) {}
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            };
        }

        private javax.net.ssl.X509TrustManager pinTrustManager(String fingerprint) {
            return new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String t) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String t) throws java.security.cert.CertificateException {
                    if (c == null || c.length == 0) throw new java.security.cert.CertificateException("No cert");
                    try {
                        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(c[0].getEncoded());
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < hash.length; i++) { if (i > 0) sb.append(':'); sb.append(String.format("%02X", hash[i])); }
                        if (!sb.toString().equalsIgnoreCase(fingerprint)) throw new java.security.cert.CertificateException("Fingerprint mismatch");
                    } catch (java.security.cert.CertificateException ce) { throw ce; }
                    catch (Exception e) { throw new java.security.cert.CertificateException(e.getMessage()); }
                }
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            };
        }

        private okhttp3.Request.Builder buildRequest(JSONObject opts) throws Exception {
            okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(opts.getString("url"));
            JSONObject headers = opts.optJSONObject("headers");
            if (headers != null) {
                java.util.Iterator<String> keys = headers.keys();
                while (keys.hasNext()) { String k = keys.next(); rb.header(k, headers.getString(k)); }
            }
            return rb;
        }

        private JSONObject headersToJson(okhttp3.Headers h) throws Exception {
            JSONObject obj = new JSONObject();
            for (int i = 0; i < h.size(); i++) obj.put(h.name(i), h.value(i));
            return obj;
        }

        @JavascriptInterface
        public void request(String optionsJson, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    JSONObject opts = new JSONObject(optionsJson);
                    okhttp3.OkHttpClient client = getClient(opts);
                    okhttp3.Request.Builder rb = buildRequest(opts);
                    String method = opts.optString("method", "GET");
                    String body = opts.optString("body", "");
                    String ct = "application/json";
                    JSONObject h = opts.optJSONObject("headers");
                    if (h != null && h.has("Content-Type")) ct = h.getString("Content-Type");
                    if (!body.isEmpty()) {
                        rb.method(method, okhttp3.RequestBody.create(body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            okhttp3.MediaType.parse(ct)));
                    } else if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                        rb.method(method, okhttp3.RequestBody.create(new byte[0], null));
                    } else {
                        rb.method(method, null);
                    }
                    try (okhttp3.Response resp = client.newCall(rb.build()).execute()) {
                        String respBody = "";
                        if (resp.body() != null) {
                            long cl = resp.body().contentLength();
                            if (cl > 16 * 1024 * 1024) throw new Exception("Response too large (>16MB), use requestFile instead");
                            okio.BufferedSource src = resp.body().source();
                            if (!src.request(16 * 1024 * 1024 + 1)) {
                                // fits in 16MB
                                respBody = resp.body().string();
                            } else {
                                resp.body().close();
                                throw new Exception("Response too large (>16MB), use requestFile instead");
                            }
                        }
                        JSONObject result = new JSONObject();
                        result.put("ok", true);
                        result.put("status", resp.code());
                        result.put("headers", headersToJson(resp.headers()));
                        result.put("body", respBody);
                        deliverResult(cbId, result.toString());
                    }
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void requestFile(String optionsJson, String destPath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    JSONObject opts = new JSONObject(optionsJson);
                    okhttp3.OkHttpClient client = getClient(opts);
                    okhttp3.Request.Builder rb = buildRequest(opts);
                    rb.method(opts.optString("method", "GET"), null);
                    String resolved = resolveFilePath(destPath);
                    try (okhttp3.Response resp = client.newCall(rb.build()).execute()) {
                        long size = 0;
                        if (resp.body() != null) {
                            try (java.io.InputStream is = resp.body().byteStream();
                                 java.io.FileOutputStream fos = new java.io.FileOutputStream(resolved)) {
                                byte[] buf = new byte[65536];
                                int r;
                                while ((r = is.read(buf)) != -1) { fos.write(buf, 0, r); size += r; }
                            }
                        }
                        JSONObject result = new JSONObject();
                        result.put("ok", true);
                        result.put("status", resp.code());
                        result.put("headers", headersToJson(resp.headers()));
                        result.put("filePath", resolved);
                        result.put("size", size);
                        deliverResult(cbId, result.toString());
                    }
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void uploadFile(String optionsJson, String filePath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    JSONObject opts = new JSONObject(optionsJson);
                    okhttp3.OkHttpClient client = getClient(opts);
                    okhttp3.Request.Builder rb = buildRequest(opts);
                    String ct = "application/octet-stream";
                    JSONObject h = opts.optJSONObject("headers");
                    if (h != null && h.has("Content-Type")) ct = h.getString("Content-Type");
                    final okhttp3.MediaType mediaType = okhttp3.MediaType.parse(ct);
                    final long fileSize = getContentSize(filePath);
                    okhttp3.RequestBody fileBody;
                    if (isContentUri(filePath)) {
                        // Stream from content URI
                        final String fp = filePath;
                        fileBody = new okhttp3.RequestBody() {
                            @Override public okhttp3.MediaType contentType() { return mediaType; }
                            @Override public long contentLength() { return fileSize; }
                            @Override public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
                                try (java.io.InputStream is = openInput(fp)) {
                                    if (is == null) return;
                                    byte[] buf = new byte[65536]; int r; long transferred = 0; long lastProgress = 0;
                                    while ((r = is.read(buf)) != -1) {
                                        sink.write(buf, 0, r); transferred += r;
                                        if (transferred - lastProgress >= 131072 || transferred == fileSize) {
                                            fireEvent("window.onTransferProgress", "{\"transferred\":" + transferred + ",\"total\":" + fileSize + "}");
                                            lastProgress = transferred;
                                        }
                                    }
                                } catch (Exception e) { throw new java.io.IOException(e); }
                            }
                        };
                    } else {
                        final File file = new File(resolveFilePath(filePath));
                        final long fSize = file.length();
                        fileBody = new okhttp3.RequestBody() {
                            @Override public okhttp3.MediaType contentType() { return mediaType; }
                            @Override public long contentLength() { return fSize; }
                            @Override public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
                                try (java.io.InputStream is = new java.io.FileInputStream(file)) {
                                    byte[] buf = new byte[65536]; int r; long transferred = 0;
                                    while ((r = is.read(buf)) != -1) {
                                        sink.write(buf, 0, r); transferred += r;
                                        fireEvent("window.onTransferProgress", "{\"transferred\":" + transferred + ",\"total\":" + fSize + "}");
                                    }
                                }
                            }
                        };
                    }
                    rb.method(opts.optString("method", "POST"), fileBody);
                    try (okhttp3.Response resp = client.newCall(rb.build()).execute()) {
                        String respBody = "";
                        if (resp.body() != null) {
                            byte[] bytes = resp.body().bytes();
                            if (bytes.length > 16 * 1024 * 1024) throw new Exception("Response too large (>16MB)");
                            respBody = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        }
                        JSONObject result = new JSONObject();
                        result.put("ok", true);
                        result.put("status", resp.code());
                        result.put("headers", headersToJson(resp.headers()));
                        result.put("body", respBody);
                        deliverResult(cbId, result.toString());
                    }
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void uploadMultipart(String optionsJson, String partsJson, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    JSONObject opts = new JSONObject(optionsJson);
                    okhttp3.OkHttpClient client = getClient(opts);
                    okhttp3.Request.Builder rb = buildRequest(opts);
                    JSONArray parts = new JSONArray(partsJson);
                    okhttp3.MultipartBody.Builder mb = new okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM);
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.getJSONObject(i);
                        String name = part.getString("name");
                        if (part.has("filePath")) {
                            String fp = part.getString("filePath");
                            String filename = part.optString("filename", "file");
                            String ct = part.optString("contentType", "application/octet-stream");
                            final long fileSize = getContentSize(fp);
                            final okhttp3.MediaType mt = okhttp3.MediaType.parse(ct);
                            if (isContentUri(fp)) {
                                final String ufp = fp;
                                okhttp3.RequestBody body = new okhttp3.RequestBody() {
                                    @Override public okhttp3.MediaType contentType() { return mt; }
                                    @Override public long contentLength() { return fileSize; }
                                    @Override public void writeTo(okio.BufferedSink sink) throws java.io.IOException {
                                        try (java.io.InputStream is = openInput(ufp)) {
                                            if (is == null) return;
                                            byte[] buf = new byte[65536]; int r;
                                            while ((r = is.read(buf)) != -1) sink.write(buf, 0, r);
                                        } catch (Exception e) { throw new java.io.IOException(e); }
                                    }
                                };
                                mb.addFormDataPart(name, filename, body);
                            } else {
                                mb.addFormDataPart(name, filename, okhttp3.RequestBody.create(new File(resolveFilePath(fp)), mt));
                            }
                        } else {
                            mb.addFormDataPart(name, part.optString("value", ""));
                        }
                    }
                    rb.method(opts.optString("method", "POST"), mb.build());
                    try (okhttp3.Response resp = client.newCall(rb.build()).execute()) {
                        String respBody = "";
                        if (resp.body() != null) {
                            byte[] bytes = resp.body().bytes();
                            if (bytes.length > 16 * 1024 * 1024) throw new Exception("Response too large (>16MB)");
                            respBody = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                        }
                        JSONObject result = new JSONObject();
                        result.put("ok", true);
                        result.put("status", resp.code());
                        result.put("headers", headersToJson(resp.headers()));
                        result.put("body", respBody);
                        deliverResult(cbId, result.toString());
                    }
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public String getCookies(String url) {
            try {
                okhttp3.HttpUrl httpUrl = okhttp3.HttpUrl.parse(url);
                if (httpUrl == null) return "[]";
                synchronized (cookieLock) {
                    java.util.List<okhttp3.Cookie> cookies = cookieStore.get(httpUrl.host());
                    if (cookies == null) return "[]";
                    JSONArray arr = new JSONArray();
                    for (okhttp3.Cookie c : cookies) {
                        JSONObject o = new JSONObject();
                        o.put("name", c.name());
                        o.put("value", c.value());
                        o.put("domain", c.domain());
                        o.put("path", c.path());
                        arr.put(o);
                    }
                    return arr.toString();
                }
            } catch (Exception e) { return "[]"; }
        }

        @JavascriptInterface
        public void setCookie(String url, String name, String value) {
            try {
                okhttp3.HttpUrl httpUrl = okhttp3.HttpUrl.parse(url);
                if (httpUrl == null) return;
                okhttp3.Cookie cookie = new okhttp3.Cookie.Builder()
                    .name(name).value(value).domain(httpUrl.host()).path("/").build();
                synchronized (cookieLock) {
                    java.util.List<okhttp3.Cookie> list = cookieStore.get(httpUrl.host());
                    java.util.Map<String, okhttp3.Cookie> merged = new java.util.LinkedHashMap<>();
                    if (list != null) for (okhttp3.Cookie c : list) merged.put(c.name(), c);
                    merged.put(name, cookie);
                    cookieStore.put(httpUrl.host(), new java.util.ArrayList<>(merged.values()));
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void clearCookies() {
            cookieStore.clear();
        }
    }

    // ── SSH / SFTP ──
    private com.jcraft.jsch.Session sshSession;
    private com.jcraft.jsch.Channel sshChannel;
    private java.io.OutputStream sshShellOut;
    private String sshDataCallbackFn;
    private String sshCloseCallbackFn;

    class SshBridge {
        @JavascriptInterface
        public void connect(String optionsJson, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    JSONObject opts = new JSONObject(optionsJson);
                    String host = opts.getString("host");
                    int port = opts.optInt("port", 22);
                    String user = opts.getString("user");
                    String password = opts.optString("password", "");
                    String privateKey = opts.optString("privateKey", "");
                    int timeout = opts.optInt("timeout", 15000);

                    // Disconnect previous session if any
                    disconnectSsh();

                    com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
                    if (!privateKey.isEmpty()) {
                        jsch.addIdentity("key", privateKey.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, null);
                    }
                    sshSession = jsch.getSession(user, host, port);
                    if (!password.isEmpty()) sshSession.setPassword(password);
                    // Accept all host keys (like ssh -o StrictHostKeyChecking=no)
                    java.util.Properties config = new java.util.Properties();
                    config.put("StrictHostKeyChecking", "no");
                    sshSession.setConfig(config);
                    sshSession.setTimeout(timeout);
                    sshSession.setServerAliveInterval(15000);
                    sshSession.setServerAliveCountMax(3);
                    sshSession.connect(timeout);
                    NetworkService.requestStart(ShellActivity.this, "SSH session");

                    String fingerprint = sshSession.getHostKey().getFingerPrint(jsch);
                    deliverResult(cbId, "{\"ok\":true,\"fingerprint\":\"" + escapeJson(fingerprint) + "\"}");
                } catch (Exception e) {
                    sshSession = null;
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void exec(String command, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    if (sshSession == null || !sshSession.isConnected()) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"not connected\"}");
                        return;
                    }
                    com.jcraft.jsch.ChannelExec ch = (com.jcraft.jsch.ChannelExec) sshSession.openChannel("exec");
                    ch.setCommand(command);
                    ch.setErrStream(null);
                    java.io.InputStream stdoutStream = ch.getInputStream();
                    java.io.InputStream stderrStream = ch.getErrStream();
                    ch.connect(15000);

                    String stdout = readStream(stdoutStream);
                    String stderr = readStream(stderrStream);
                    // Wait for exit status to arrive (up to 1s after streams close)
                    int exitCode = ch.getExitStatus();
                    if (exitCode == -1) {
                        for (int i = 0; i < 10 && exitCode == -1; i++) {
                            Thread.sleep(100);
                            exitCode = ch.getExitStatus();
                        }
                    }
                    ch.disconnect();

                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("stdout", stdout);
                    result.put("stderr", stderr);
                    result.put("exitCode", exitCode);
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void shell(String cbId) {
            httpClientPool.submit(() -> {
                try {
                    if (sshSession == null || !sshSession.isConnected()) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"not connected\"}");
                        return;
                    }
                    // Close previous shell if any
                    if (sshChannel != null && sshChannel.isConnected()) sshChannel.disconnect();

                    com.jcraft.jsch.ChannelShell ch = (com.jcraft.jsch.ChannelShell) sshSession.openChannel("shell");
                    ch.setPtyType("xterm", 80, 24, 640, 480);
                    sshShellOut = ch.getOutputStream();
                    java.io.InputStream in = ch.getInputStream();
                    ch.connect(15000);
                    sshChannel = ch;

                    // Read loop
                    new Thread(() -> {
                        byte[] buf = new byte[8192];
                        try {
                            int r;
                            while (sshChannel != null && sshChannel.isConnected() && (r = in.read(buf)) != -1) {
                                if (sshDataCallbackFn == null) continue;
                                String data = new String(buf, 0, r, java.nio.charset.StandardCharsets.UTF_8);
                                fireEvent(sshDataCallbackFn, "{\"data\":\"" + escapeJson(data) + "\"}");
                            }
                        } catch (Exception e) {
                            if (sshChannel != null && sshChannel.isConnected())
                                Log.e("iappyxOS", "SSH shell read: " + e.getMessage());
                        }
                        if (sshCloseCallbackFn != null) fireEvent(sshCloseCallbackFn, "{}");
                    }).start();

                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void send(String data) {
            java.io.OutputStream os = sshShellOut;
            if (os == null) return;
            httpClientPool.submit(() -> {
                try { os.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)); os.flush(); }
                catch (Exception e) { Log.e("iappyxOS", "SSH send: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void resize(String colsStr, String rowsStr) {
            try {
                int cols = Integer.parseInt(colsStr);
                int rows = Integer.parseInt(rowsStr);
                if (sshChannel instanceof com.jcraft.jsch.ChannelShell) {
                    ((com.jcraft.jsch.ChannelShell) sshChannel).setPtySize(cols, rows, cols * 8, rows * 16);
                }
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void onData(String callbackFn) { sshDataCallbackFn = callbackFn; }

        @JavascriptInterface
        public void onClose(String callbackFn) { sshCloseCallbackFn = callbackFn; }

        @JavascriptInterface
        public void forwardLocal(String localPortStr, String remoteHost, String remotePortStr, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    if (sshSession == null || !sshSession.isConnected()) { deliverResult(cbId, "{\"ok\":false,\"error\":\"not connected\"}"); return; }
                    int localPort = Integer.parseInt(localPortStr);
                    int remotePort = Integer.parseInt(remotePortStr);
                    int assigned = sshSession.setPortForwardingL(localPort, remoteHost, remotePort);
                    deliverResult(cbId, "{\"ok\":true,\"localPort\":" + assigned + "}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void forwardRemote(String remotePortStr, String localHost, String localPortStr, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    if (sshSession == null || !sshSession.isConnected()) { deliverResult(cbId, "{\"ok\":false,\"error\":\"not connected\"}"); return; }
                    int remotePort = Integer.parseInt(remotePortStr);
                    int localPort = Integer.parseInt(localPortStr);
                    sshSession.setPortForwardingR(remotePort, localHost, localPort);
                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void removeForward(String localPortStr) {
            try {
                if (sshSession != null && sshSession.isConnected())
                    sshSession.delPortForwardingL(Integer.parseInt(localPortStr));
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void removeRemoteForward(String remotePortStr) {
            try {
                if (sshSession != null && sshSession.isConnected())
                    sshSession.delPortForwardingR(Integer.parseInt(remotePortStr));
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void disconnect() { disconnectSsh(); }

        @JavascriptInterface
        public boolean isConnected() { return sshSession != null && sshSession.isConnected(); }

        // ── SFTP ──

        @JavascriptInterface
        public void upload(String localPath, String remotePath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    if (sshSession == null || !sshSession.isConnected()) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"not connected\"}");
                        return;
                    }
                    com.jcraft.jsch.ChannelSftp sftp = (com.jcraft.jsch.ChannelSftp) sshSession.openChannel("sftp");
                    sftp.connect(15000);
                    try (java.io.InputStream is = openInput(localPath)) {
                        if (is == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"file not found\"}"); sftp.disconnect(); return; }
                        long fileSize = getContentSize(localPath);
                        sftp.put(is, remotePath, new com.jcraft.jsch.SftpProgressMonitor() {
                            long transferred = 0;
                            @Override public void init(int op, String src, String dest, long max) {}
                            @Override public boolean count(long count) {
                                transferred += count;
                                fireEvent("window.onTransferProgress", "{\"transferred\":" + transferred + ",\"total\":" + fileSize + "}");
                                return true;
                            }
                            @Override public void end() {}
                        });
                    }
                    sftp.disconnect();
                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void download(String remotePath, String localPath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    if (sshSession == null || !sshSession.isConnected()) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"not connected\"}");
                        return;
                    }
                    com.jcraft.jsch.ChannelSftp sftp = (com.jcraft.jsch.ChannelSftp) sshSession.openChannel("sftp");
                    sftp.connect(15000);
                    String resolved = resolveFilePath(localPath);
                    try (FileOutputStream fos = new FileOutputStream(resolved)) {
                        sftp.get(remotePath, fos, new com.jcraft.jsch.SftpProgressMonitor() {
                            long transferred = 0;
                            @Override public void init(int op, String src, String dest, long max) {}
                            @Override public boolean count(long count) {
                                transferred += count;
                                fireEvent("window.onTransferProgress", "{\"transferred\":" + transferred + ",\"total\":-1}");
                                return true;
                            }
                            @Override public void end() {}
                        });
                    }
                    sftp.disconnect();
                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("filePath", resolved);
                    result.put("size", new File(resolved).length());
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void listDir(String remotePath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    if (sshSession == null || !sshSession.isConnected()) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"not connected\"}");
                        return;
                    }
                    com.jcraft.jsch.ChannelSftp sftp = (com.jcraft.jsch.ChannelSftp) sshSession.openChannel("sftp");
                    sftp.connect(15000);
                    @SuppressWarnings("unchecked")
                    java.util.Vector<com.jcraft.jsch.ChannelSftp.LsEntry> entries = sftp.ls(remotePath);
                    JSONArray arr = new JSONArray();
                    for (com.jcraft.jsch.ChannelSftp.LsEntry entry : entries) {
                        JSONObject o = new JSONObject();
                        o.put("name", entry.getFilename());
                        o.put("size", entry.getAttrs().getSize());
                        o.put("isDir", entry.getAttrs().isDir());
                        o.put("modified", (long) entry.getAttrs().getMTime() * 1000);
                        o.put("permissions", entry.getAttrs().getPermissionsString());
                        arr.put(o);
                    }
                    sftp.disconnect();
                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("files", arr);
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        private String readStream(java.io.InputStream is) throws Exception {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            long total = 0;
            while ((r = is.read(buf)) != -1) {
                total += r;
                if (total > 16 * 1024 * 1024) throw new Exception("Output too large (>16MB), use shell() for streaming");
                baos.write(buf, 0, r);
            }
            return baos.toString("UTF-8");
        }
    }

    private void disconnectSsh() {
        if (sshChannel != null) { try { sshChannel.disconnect(); } catch (Exception ignored) {} sshChannel = null; }
        sshShellOut = null;
        if (sshSession != null) {
            try { sshSession.disconnect(); } catch (Exception ignored) {}
            sshSession = null;
            NetworkService.requestStop(this);
        }
    }

    // ── SMB / Network Shares ──
    private jcifs.smb.SmbFile smbRoot;
    private jcifs.CIFSContext smbContext;

    class SmbBridge {
        private jcifs.smb.SmbFile resolveSmbPath(String remotePath) throws Exception {
            if (smbRoot == null) throw new Exception("not connected");
            if (remotePath == null || remotePath.isEmpty()) return smbRoot;
            String path = remotePath.startsWith("/") ? remotePath.substring(1) : remotePath;
            if (path.isEmpty()) return smbRoot;
            return new jcifs.smb.SmbFile(smbRoot, path);
        }

        @JavascriptInterface
        public void connect(String optionsJson, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    JSONObject opts = new JSONObject(optionsJson);
                    String host = opts.getString("host");
                    String share = opts.getString("share");
                    String user = opts.optString("user", "guest");
                    String password = opts.optString("password", "");
                    String domain = opts.optString("domain", "");

                    disconnectSmb();

                    jcifs.config.PropertyConfiguration config = new jcifs.config.PropertyConfiguration(new java.util.Properties() {{
                        setProperty("jcifs.smb.client.minVersion", "SMB202");
                        setProperty("jcifs.smb.client.maxVersion", "SMB311");
                    }});
                    jcifs.context.BaseContext bc = new jcifs.context.BaseContext(config);
                    jcifs.smb.NtlmPasswordAuthenticator auth = new jcifs.smb.NtlmPasswordAuthenticator(domain, user, password);
                    smbContext = bc.withCredentials(auth);

                    String url = "smb://" + host + "/" + share + "/";
                    smbRoot = new jcifs.smb.SmbFile(url, smbContext);
                    smbRoot.exists(); // test connection
                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    smbRoot = null;
                    smbContext = null;
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void listDir(String remotePath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    jcifs.smb.SmbFile dir = resolveSmbPath(remotePath.endsWith("/") || remotePath.equals("/") ? remotePath : remotePath + "/");
                    jcifs.smb.SmbFile[] files = dir.listFiles();
                    JSONArray arr = new JSONArray();
                    if (files != null) {
                        for (jcifs.smb.SmbFile f : files) {
                            JSONObject o = new JSONObject();
                            String name = f.getName();
                            if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                            o.put("name", name);
                            try { o.put("size", f.length()); } catch (Exception e) { o.put("size", 0); }
                            o.put("isDir", f.isDirectory());
                            try { o.put("modified", f.lastModified()); } catch (Exception e) { o.put("modified", 0); }
                            arr.put(o);
                        }
                    }
                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("files", arr);
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void download(String remotePath, String localPath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    jcifs.smb.SmbFile remote = resolveSmbPath(remotePath);
                    String resolved = resolveFilePath(localPath);
                    long total = remote.length();
                    long size = 0; long lastProgress = 0;
                    try (java.io.InputStream is = remote.getInputStream();
                         FileOutputStream fos = new FileOutputStream(resolved)) {
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            fos.write(buf, 0, r); size += r;
                            if (size - lastProgress >= 131072 || size == total) { // every 128KB or at end
                                fireEvent("window.onTransferProgress", "{\"transferred\":" + size + ",\"total\":" + total + "}");
                                lastProgress = size;
                            }
                        }
                    }
                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("filePath", resolved);
                    result.put("size", size);
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void upload(String localPath, String remotePath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    jcifs.smb.SmbFile remote = resolveSmbPath(remotePath);
                    long total = getContentSize(localPath);
                    long transferred = 0; long lastProgress = 0;
                    try (java.io.InputStream is = openInput(localPath);
                         java.io.OutputStream os = remote.getOutputStream()) {
                        if (is == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"file not found\"}"); return; }
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            os.write(buf, 0, r); transferred += r;
                            if (transferred - lastProgress >= 131072 || transferred == total) {
                                fireEvent("window.onTransferProgress", "{\"transferred\":" + transferred + ",\"total\":" + total + "}");
                                lastProgress = transferred;
                            }
                        }
                    }
                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void delete(String remotePath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    jcifs.smb.SmbFile remote = resolveSmbPath(remotePath);
                    remote.delete();
                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void mkdir(String remotePath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    jcifs.smb.SmbFile remote = resolveSmbPath(remotePath.endsWith("/") ? remotePath : remotePath + "/");
                    remote.mkdir();
                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void copy(String srcPath, String destPath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    jcifs.smb.SmbFile src = resolveSmbPath(srcPath);
                    jcifs.smb.SmbFile dest = resolveSmbPath(destPath);
                    src.copyTo(dest);
                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void rename(String oldPath, String newPath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    jcifs.smb.SmbFile src = resolveSmbPath(oldPath);
                    jcifs.smb.SmbFile dest = resolveSmbPath(newPath);
                    src.renameTo(dest);
                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void getFileInfo(String remotePath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    jcifs.smb.SmbFile f = resolveSmbPath(remotePath);
                    if (!f.exists()) { deliverResult(cbId, "{\"ok\":true,\"exists\":false}"); return; }
                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("exists", true);
                    result.put("name", f.getName().replaceAll("/$", ""));
                    result.put("size", f.length());
                    result.put("isDir", f.isDirectory());
                    result.put("modified", f.lastModified());
                    result.put("hidden", f.isHidden());
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void exists(String remotePath, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    jcifs.smb.SmbFile f = resolveSmbPath(remotePath);
                    deliverResult(cbId, "{\"ok\":true,\"exists\":" + f.exists() + "}");
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void disconnect() { disconnectSmb(); }

        @JavascriptInterface
        public boolean isConnected() { return smbRoot != null; }

        @JavascriptInterface
        public void listShares(String host, String optionsJson, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    String user = "guest", password = "", domain = "";
                    if (optionsJson != null && !optionsJson.isEmpty()) {
                        JSONObject opts = new JSONObject(optionsJson);
                        user = opts.optString("user", "guest");
                        password = opts.optString("password", "");
                        domain = opts.optString("domain", "");
                    }
                    jcifs.config.PropertyConfiguration config = new jcifs.config.PropertyConfiguration(new java.util.Properties() {{
                        setProperty("jcifs.smb.client.minVersion", "SMB202");
                        setProperty("jcifs.smb.client.maxVersion", "SMB311");
                    }});
                    jcifs.context.BaseContext bc = new jcifs.context.BaseContext(config);
                    jcifs.smb.NtlmPasswordAuthenticator auth = new jcifs.smb.NtlmPasswordAuthenticator(domain, user, password);
                    jcifs.CIFSContext ctx = bc.withCredentials(auth);
                    jcifs.smb.SmbFile root = new jcifs.smb.SmbFile("smb://" + host + "/", ctx);
                    jcifs.smb.SmbFile[] shares = root.listFiles();
                    JSONArray arr = new JSONArray();
                    if (shares != null) {
                        for (jcifs.smb.SmbFile s : shares) {
                            String name = s.getName();
                            if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                            arr.put(name);
                        }
                    }
                    root.close();
                    ctx.close();
                    JSONObject result = new JSONObject();
                    result.put("ok", true);
                    result.put("shares", arr);
                    deliverResult(cbId, result.toString());
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }
    }

    private void disconnectSmb() {
        if (smbRoot != null) { try { smbRoot.close(); } catch (Exception ignored) {} smbRoot = null; }
        if (smbContext != null) { try { smbContext.close(); } catch (Exception ignored) {} smbContext = null; }
    }

    // ── Bluetooth LE ──
    private android.bluetooth.BluetoothAdapter bleAdapter;
    private android.bluetooth.le.BluetoothLeScanner bleScanner;
    private android.bluetooth.le.ScanCallback bleScanCallback;
    private String bleScanCallbackFn;
    private final java.util.concurrent.ConcurrentHashMap<String, android.bluetooth.BluetoothGatt> bleDevices = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.Map<String, String>> bleSubscriptions = new java.util.concurrent.ConcurrentHashMap<>();
    private Runnable blePendingAction;
    private static final int REQ_BLE = 1012;
    private final Object bleOpLock = new Object(); // serialize GATT read/write (Android allows only one at a time)
    private volatile java.util.concurrent.CountDownLatch bleReadLatch;
    private volatile byte[] bleReadValue;
    private volatile java.util.concurrent.CountDownLatch bleWriteLatch;
    private volatile boolean bleWriteOk;

    class BleBridge {
        private void ensureBleAdapter() {
            if (bleAdapter == null) {
                android.bluetooth.BluetoothManager bm = (android.bluetooth.BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                if (bm != null) bleAdapter = bm.getAdapter();
            }
        }

        private boolean ensureBlePermissions(Runnable action) {
            java.util.List<String> needed = new java.util.ArrayList<>();
            if (ContextCompat.checkSelfPermission(ShellActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
            if (Build.VERSION.SDK_INT >= 31) {
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED)
                    needed.add("android.permission.BLUETOOTH_SCAN");
                if (ContextCompat.checkSelfPermission(ShellActivity.this, "android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED)
                    needed.add("android.permission.BLUETOOTH_CONNECT");
            }
            if (needed.isEmpty()) return true;
            blePendingAction = action;
            ActivityCompat.requestPermissions(ShellActivity.this, needed.toArray(new String[0]), REQ_BLE);
            return false;
        }

        @JavascriptInterface
        public boolean isEnabled() {
            ensureBleAdapter();
            return bleAdapter != null && bleAdapter.isEnabled();
        }

        @JavascriptInterface
        public void startScan(String callbackFn) {
            runOnUiThread(() -> {
                ensureBleAdapter();
                if (bleAdapter == null || !bleAdapter.isEnabled()) {
                    fireEvent(callbackFn, "{\"event\":\"error\",\"error\":\"Bluetooth is off\"}");
                    return;
                }
                if (!ensureBlePermissions(() -> startScan(callbackFn))) return;
                bleScanCallbackFn = callbackFn;
                bleScanner = bleAdapter.getBluetoothLeScanner();
                if (bleScanner == null) {
                    fireEvent(callbackFn, "{\"event\":\"error\",\"error\":\"Scanner not available\"}");
                    return;
                }
                if (bleScanCallback != null) {
                    try { bleScanner.stopScan(bleScanCallback); } catch (Exception ignored) {}
                }
                bleScanCallback = new android.bluetooth.le.ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
                        if (bleScanCallbackFn == null) return;
                        try {
                            android.bluetooth.BluetoothDevice d = result.getDevice();
                            String name = "";
                            try { name = d.getName() != null ? d.getName() : ""; } catch (SecurityException ignored) {}
                            fireEvent(bleScanCallbackFn, "{\"event\":\"found\",\"name\":\"" + escapeJson(name) +
                                "\",\"address\":\"" + d.getAddress() +
                                "\",\"rssi\":" + result.getRssi() + "}");
                        } catch (Exception ignored) {}
                    }
                    @Override
                    public void onScanFailed(int errorCode) {
                        if (bleScanCallbackFn != null)
                            fireEvent(bleScanCallbackFn, "{\"event\":\"error\",\"error\":\"Scan failed: " + errorCode + "\"}");
                    }
                };
                try { bleScanner.startScan(bleScanCallback); } catch (SecurityException e) {
                    fireEvent(callbackFn, "{\"event\":\"error\",\"error\":\"permission denied\"}");
                }
            });
        }

        @JavascriptInterface
        public void stopScan() {
            runOnUiThread(() -> {
                if (bleScanner != null && bleScanCallback != null) {
                    try { bleScanner.stopScan(bleScanCallback); } catch (Exception ignored) {}
                    bleScanCallback = null;
                }
            });
        }

        @JavascriptInterface
        public void connect(String address, String cbId) {
            httpClientPool.submit(() -> {
                try {
                    ensureBleAdapter();
                    if (bleAdapter == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"Bluetooth not available\"}"); return; }
                    android.bluetooth.BluetoothDevice device = bleAdapter.getRemoteDevice(address);
                    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    final JSONObject[] resultHolder = {null};
                    final android.bluetooth.BluetoothGatt[] gattHolder = {null};

                    // Close existing GATT for this address to prevent leaks
                    android.bluetooth.BluetoothGatt existing = bleDevices.remove(address);
                    if (existing != null) { try { existing.close(); } catch (Exception ignored) {} }

                    runOnUiThread(() -> {
                        try {
                            gattHolder[0] = device.connectGatt(ShellActivity.this, false, new android.bluetooth.BluetoothGattCallback() {
                                @Override
                                public void onConnectionStateChange(android.bluetooth.BluetoothGatt g, int status, int newState) {
                                    if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                                        try { g.discoverServices(); } catch (SecurityException ignored) {}
                                    } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                                        boolean wasTracked = bleDevices.remove(address) != null;
                                        if (wasTracked && bleDevices.isEmpty()) NetworkService.requestStop(ShellActivity.this);
                                        // Notify JS of unexpected disconnect
                                        if (wasTracked && bleScanCallbackFn != null) {
                                            fireEvent(bleScanCallbackFn, "{\"event\":\"disconnected\",\"address\":\"" + escapeJson(address) + "\"}");
                                        }
                                        if (resultHolder[0] == null) {
                                            try {
                                                resultHolder[0] = new JSONObject();
                                                resultHolder[0].put("ok", false);
                                                resultHolder[0].put("error", "Connection failed (status " + status + ")");
                                            } catch (Exception ignored) {}
                                            latch.countDown();
                                        }
                                    }
                                }
                                @Override
                                public void onServicesDiscovered(android.bluetooth.BluetoothGatt g, int status) {
                                    try {
                                        bleDevices.put(address, g);
                                        if (bleDevices.size() == 1) NetworkService.requestStart(ShellActivity.this, "BLE device connected");
                                        JSONObject r = new JSONObject();
                                        r.put("ok", true);
                                        JSONArray services = new JSONArray();
                                        for (android.bluetooth.BluetoothGattService s : g.getServices()) {
                                            JSONObject svc = new JSONObject();
                                            svc.put("uuid", s.getUuid().toString());
                                            JSONArray chars = new JSONArray();
                                            for (android.bluetooth.BluetoothGattCharacteristic c : s.getCharacteristics()) {
                                                JSONObject ch = new JSONObject();
                                                ch.put("uuid", c.getUuid().toString());
                                                int props = c.getProperties();
                                                JSONArray propList = new JSONArray();
                                                if ((props & android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ) != 0) propList.put("read");
                                                if ((props & android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) propList.put("write");
                                                if ((props & android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) propList.put("writeNoResponse");
                                                if ((props & android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) propList.put("notify");
                                                if ((props & android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) propList.put("indicate");
                                                ch.put("properties", propList);
                                                chars.put(ch);
                                            }
                                            svc.put("characteristics", chars);
                                            services.put(svc);
                                        }
                                        r.put("services", services);
                                        resultHolder[0] = r;
                                    } catch (Exception e) {
                                        try {
                                            resultHolder[0] = new JSONObject();
                                            resultHolder[0].put("ok", false);
                                            resultHolder[0].put("error", escapeJson(e.getMessage()));
                                        } catch (Exception ignored) {}
                                    }
                                    latch.countDown();
                                }
                                @Override
                                public void onCharacteristicRead(android.bluetooth.BluetoothGatt g, android.bluetooth.BluetoothGattCharacteristic c, int status) {
                                    if (bleReadLatch != null) {
                                        bleReadValue = (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) ? c.getValue() : null;
                                        bleReadLatch.countDown();
                                    }
                                }
                                @Override
                                public void onCharacteristicWrite(android.bluetooth.BluetoothGatt g, android.bluetooth.BluetoothGattCharacteristic c, int status) {
                                    if (bleWriteLatch != null) {
                                        bleWriteOk = (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS);
                                        bleWriteLatch.countDown();
                                    }
                                }
                                @Override
                                public void onCharacteristicChanged(android.bluetooth.BluetoothGatt g, android.bluetooth.BluetoothGattCharacteristic c) {
                                    String key = address + "|" + c.getService().getUuid() + "|" + c.getUuid();
                                    java.util.Map<String, String> subs = bleSubscriptions.get(key);
                                    if (subs == null) return;
                                    String fn = subs.get("fn");
                                    if (fn == null) return;
                                    byte[] val = c.getValue();
                                    if (val == null) return;
                                    StringBuilder hex = new StringBuilder();
                                    for (byte b : val) hex.append(String.format("%02x", b));
                                    String str = new String(val, java.nio.charset.StandardCharsets.UTF_8);
                                    fireEvent(fn, "{\"value\":\"" + escapeJson(str) + "\",\"hex\":\"" + hex + "\"}");
                                }
                            }, android.bluetooth.BluetoothDevice.TRANSPORT_LE);
                        } catch (SecurityException e) {
                            try {
                                resultHolder[0] = new JSONObject();
                                resultHolder[0].put("ok", false);
                                resultHolder[0].put("error", "permission denied");
                            } catch (Exception ignored) {}
                            latch.countDown();
                        }
                    });

                    latch.await(15, java.util.concurrent.TimeUnit.SECONDS);
                    if (resultHolder[0] != null) {
                        deliverResult(cbId, resultHolder[0].toString());
                    } else {
                        // Timeout — close leaked GATT if not stored in bleDevices
                        if (gattHolder[0] != null && !bleDevices.containsValue(gattHolder[0])) {
                            try { gattHolder[0].disconnect(); gattHolder[0].close(); } catch (Exception ignored) {}
                        }
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"Connection timeout\"}");
                    }
                } catch (Exception e) {
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void disconnect(String address) {
            android.bluetooth.BluetoothGatt gatt = bleDevices.remove(address);
            if (gatt != null) {
                try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) {}
                // Clean up subscriptions for this device
                java.util.Iterator<String> it = bleSubscriptions.keySet().iterator();
                while (it.hasNext()) { if (it.next().startsWith(address + "|")) it.remove(); }
                if (bleDevices.isEmpty()) NetworkService.requestStop(ShellActivity.this);
            }
        }

        @JavascriptInterface
        public void read(String address, String serviceUuid, String charUuid, String cbId) {
            httpClientPool.submit(() -> {
                synchronized (bleOpLock) {
                    try {
                        android.bluetooth.BluetoothGatt gatt = bleDevices.get(address);
                        if (gatt == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"not connected\"}"); return; }
                        android.bluetooth.BluetoothGattService svc = gatt.getService(java.util.UUID.fromString(serviceUuid));
                        if (svc == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"service not found\"}"); return; }
                        android.bluetooth.BluetoothGattCharacteristic ch = svc.getCharacteristic(java.util.UUID.fromString(charUuid));
                        if (ch == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"characteristic not found\"}"); return; }

                        bleReadLatch = new java.util.concurrent.CountDownLatch(1);
                        bleReadValue = null;
                        try {
                            gatt.readCharacteristic(ch);
                        } catch (SecurityException e) {
                            bleReadLatch = null;
                            deliverResult(cbId, "{\"ok\":false,\"error\":\"permission denied\"}");
                            return;
                        }
                        bleReadLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                        byte[] val = bleReadValue;
                        bleReadLatch = null;
                        if (val != null) {
                            StringBuilder hex = new StringBuilder();
                            for (byte b : val) hex.append(String.format("%02x", b));
                            String str = new String(val, java.nio.charset.StandardCharsets.UTF_8);
                            deliverResult(cbId, "{\"ok\":true,\"value\":\"" + escapeJson(str) + "\",\"hex\":\"" + hex + "\"}");
                        } else {
                            deliverResult(cbId, "{\"ok\":false,\"error\":\"read returned no data\"}");
                        }
                    } catch (Exception e) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                    }
                }
            });
        }

        @JavascriptInterface
        public void write(String address, String serviceUuid, String charUuid, String hexData, String cbId) {
            httpClientPool.submit(() -> {
                synchronized (bleOpLock) {
                    try {
                        android.bluetooth.BluetoothGatt gatt = bleDevices.get(address);
                        if (gatt == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"not connected\"}"); return; }
                        android.bluetooth.BluetoothGattService svc = gatt.getService(java.util.UUID.fromString(serviceUuid));
                        if (svc == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"service not found\"}"); return; }
                        android.bluetooth.BluetoothGattCharacteristic ch = svc.getCharacteristic(java.util.UUID.fromString(charUuid));
                        if (ch == null) { deliverResult(cbId, "{\"ok\":false,\"error\":\"characteristic not found\"}"); return; }
                        int len = hexData.length() / 2;
                        byte[] bytes = new byte[len];
                        for (int i = 0; i < len; i++) bytes[i] = (byte) Integer.parseInt(hexData.substring(i * 2, i * 2 + 2), 16);
                        ch.setValue(bytes);
                        bleWriteLatch = new java.util.concurrent.CountDownLatch(1);
                        bleWriteOk = false;
                        try {
                            boolean queued = gatt.writeCharacteristic(ch);
                            if (!queued) { deliverResult(cbId, "{\"ok\":false,\"error\":\"write not queued\"}"); bleWriteLatch = null; return; }
                            bleWriteLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                            deliverResult(cbId, bleWriteOk ? "{\"ok\":true}" : "{\"ok\":false,\"error\":\"write failed on device\"}");
                            bleWriteLatch = null;
                        } catch (SecurityException e) {
                            bleWriteLatch = null;
                            deliverResult(cbId, "{\"ok\":false,\"error\":\"permission denied\"}");
                        }
                    } catch (Exception e) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                    }
                }
            });
        }

        @JavascriptInterface
        public void subscribe(String address, String serviceUuid, String charUuid, String callbackFn) {
            runOnUiThread(() -> {
                try {
                    android.bluetooth.BluetoothGatt gatt = bleDevices.get(address);
                    if (gatt == null) return;
                    android.bluetooth.BluetoothGattService svc = gatt.getService(java.util.UUID.fromString(serviceUuid));
                    if (svc == null) return;
                    android.bluetooth.BluetoothGattCharacteristic ch = svc.getCharacteristic(java.util.UUID.fromString(charUuid));
                    if (ch == null) return;
                    gatt.setCharacteristicNotification(ch, true);
                    // Write CCC descriptor to enable notifications
                    android.bluetooth.BluetoothGattDescriptor desc = ch.getDescriptor(
                        java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (desc != null) {
                        desc.setValue(android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(desc);
                    }
                    String key = address + "|" + serviceUuid + "|" + charUuid;
                    java.util.Map<String, String> sub = new java.util.HashMap<>();
                    sub.put("fn", callbackFn);
                    bleSubscriptions.put(key, sub);
                } catch (SecurityException ignored) {}
            });
        }

        @JavascriptInterface
        public void unsubscribe(String address, String serviceUuid, String charUuid) {
            runOnUiThread(() -> {
                try {
                    android.bluetooth.BluetoothGatt gatt = bleDevices.get(address);
                    if (gatt == null) return;
                    android.bluetooth.BluetoothGattService svc = gatt.getService(java.util.UUID.fromString(serviceUuid));
                    if (svc == null) return;
                    android.bluetooth.BluetoothGattCharacteristic ch = svc.getCharacteristic(java.util.UUID.fromString(charUuid));
                    if (ch == null) return;
                    gatt.setCharacteristicNotification(ch, false);
                    android.bluetooth.BluetoothGattDescriptor desc = ch.getDescriptor(
                        java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    if (desc != null) {
                        desc.setValue(android.bluetooth.BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(desc);
                    }
                    bleSubscriptions.remove(address + "|" + serviceUuid + "|" + charUuid);
                } catch (SecurityException ignored) {}
            });
        }

        @JavascriptInterface
        public String getConnectedDevices() {
            try {
                JSONArray arr = new JSONArray();
                for (String addr : bleDevices.keySet()) arr.put(addr);
                return arr.toString();
            } catch (Exception e) { return "[]"; }
        }
    }

    private void disconnectAllBle() {
        boolean hadDevices = !bleDevices.isEmpty();
        for (android.bluetooth.BluetoothGatt gatt : bleDevices.values()) {
            try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) {}
        }
        bleDevices.clear();
        if (hadDevices) NetworkService.requestStop(this);
        bleSubscriptions.clear();
        if (bleScanner != null && bleScanCallback != null) {
            try { bleScanner.stopScan(bleScanCallback); } catch (Exception ignored) {}
        }
        bleScanCallback = null;
    }

    // ── Push Notifications (FCM) ──
    private boolean firebaseInitialized = false;

    private void initFirebaseIfNeeded() {
        if (firebaseInitialized) return;
        try {
            // Read firebase config from assets (injected during APK build)
            java.io.InputStream is = getAssets().open("app/firebase_config.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            String json = new String(buf, java.nio.charset.StandardCharsets.UTF_8);
            JSONObject cfg = new JSONObject(json);

            // Extract values — handle both formats (raw google-services.json and simplified)
            String projectId = cfg.optString("project_id", "");
            String appId = "";
            String apiKey = "";
            String senderId = cfg.optString("project_number", "");

            // google-services.json has nested client array
            JSONArray clients = cfg.optJSONArray("client");
            if (clients != null && clients.length() > 0) {
                JSONObject client = clients.getJSONObject(0);
                JSONObject clientInfo = client.optJSONObject("client_info");
                if (clientInfo != null) appId = clientInfo.optString("mobilesdk_app_id", "");
                JSONArray apiKeys = client.optJSONArray("api_key");
                if (apiKeys != null && apiKeys.length() > 0) apiKey = apiKeys.getJSONObject(0).optString("current_key", "");
            }

            if (projectId.isEmpty() || appId.isEmpty() || apiKey.isEmpty()) {
                Log.w("iappyxOS", "Firebase config incomplete — push disabled");
                return;
            }

            com.google.firebase.FirebaseOptions options = new com.google.firebase.FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .setGcmSenderId(senderId)
                .build();

            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this, options);
            }
            firebaseInitialized = true;
            Log.i("iappyxOS", "Firebase initialized for push notifications");
        } catch (java.io.FileNotFoundException e) {
            // No firebase config — push not available, this is fine
        } catch (Exception e) {
            Log.e("iappyxOS", "Firebase init failed: " + e.getMessage());
        }
    }

    class PushBridge {
        @JavascriptInterface
        public boolean isAvailable() {
            initFirebaseIfNeeded();
            return firebaseInitialized;
        }

        @JavascriptInterface
        public void getToken(String cbId) {
            initFirebaseIfNeeded();
            if (!firebaseInitialized) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"Firebase not configured\"}");
                return;
            }
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> deliverResult(cbId, "{\"ok\":true,\"token\":\"" + token + "\"}"))
                .addOnFailureListener(e -> deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}"));
        }

        @JavascriptInterface
        public void onMessage(String callbackFn) {
            PushService.foregroundCallbackFn = callbackFn;
            PushService.activeActivity = ShellActivity.this;
        }

        @JavascriptInterface
        public void onTokenRefresh(String callbackFn) {
            PushService.tokenRefreshFn = callbackFn;
            PushService.activeActivity = ShellActivity.this;
        }
    }

    // ── TCP Socket ──
    private java.net.Socket tcpSocket;
    private java.io.OutputStream tcpOut;
    private Thread tcpReadThread;
    private String tcpDataCallbackFn;
    private String tcpCloseCallbackFn;
    private volatile boolean tcpRunning = false;
    private volatile int tcpConnectionId = 0;
    private final Object tcpLock = new Object();

    class TcpBridge {
        @JavascriptInterface
        public void open(String host, String portStr, String useTlsStr, String cbId) {
            final int myId;
            synchronized (tcpLock) {
                if (tcpRunning) { deliverResult(cbId, "{\"ok\":false,\"error\":\"already open\"}"); return; }
                tcpRunning = true;
                myId = ++tcpConnectionId;
            }
            httpClientPool.submit(() -> {
                try {
                    int port = Integer.parseInt(portStr);
                    boolean useTls = "true".equalsIgnoreCase(useTlsStr);
                    if (useTls) {
                        javax.net.ssl.TrustManager[] tm = {new javax.net.ssl.X509TrustManager() {
                            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String t) {}
                            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String t) {}
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                        }};
                        javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
                        ctx.init(null, tm, new java.security.SecureRandom());
                        tcpSocket = ctx.getSocketFactory().createSocket();
                        tcpSocket.connect(new java.net.InetSocketAddress(host, port), 5000);
                    } else {
                        tcpSocket = new java.net.Socket();
                        tcpSocket.connect(new java.net.InetSocketAddress(host, port), 5000);
                    }
                    tcpSocket.setKeepAlive(true);
                    tcpOut = tcpSocket.getOutputStream();
                    tcpRunning = true;
                    NetworkService.requestStart(ShellActivity.this, "TCP connection");

                    // Start read loop
                    tcpReadThread = new Thread(() -> {
                        byte[] buf = new byte[65536];
                        try {
                            java.io.InputStream in = tcpSocket.getInputStream();
                            int r;
                            while (tcpRunning && (r = in.read(buf)) != -1) {
                                if (tcpDataCallbackFn == null) continue;
                                // Provide both UTF-8 string and hex
                                String data = new String(buf, 0, r, java.nio.charset.StandardCharsets.UTF_8);
                                StringBuilder hex = new StringBuilder();
                                for (int i = 0; i < r; i++) hex.append(String.format("%02x", buf[i]));
                                fireEvent(tcpDataCallbackFn, "{\"data\":\"" + escapeJson(data) +
                                    "\",\"hex\":\"" + hex.toString() +
                                    "\",\"length\":" + r + "}");
                            }
                        } catch (Exception e) {
                            if (tcpRunning) Log.e("iappyxOS", "TCP read: " + e.getMessage());
                        }
                        if (tcpRunning) { tcpRunning = false; NetworkService.requestStop(ShellActivity.this); }
                        if (tcpCloseCallbackFn != null) fireEvent(tcpCloseCallbackFn, "{}");
                    });
                    tcpReadThread.setDaemon(true);
                    tcpReadThread.start();

                    String localAddr = tcpSocket.getLocalAddress().getHostAddress();
                    int localPort = tcpSocket.getLocalPort();
                    deliverResult(cbId, "{\"ok\":true,\"localAddress\":\"" + escapeJson(localAddr) + "\",\"localPort\":" + localPort + "}");
                } catch (Exception e) {
                    if (myId == tcpConnectionId) tcpRunning = false;
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void openTrustPin(String host, String portStr, String fingerprint, String cbId) {
            final int myId;
            synchronized (tcpLock) {
                if (tcpRunning) { deliverResult(cbId, "{\"ok\":false,\"error\":\"already open\"}"); return; }
                tcpRunning = true;
                myId = ++tcpConnectionId;
            }
            httpClientPool.submit(() -> {
                try {
                    int port = Integer.parseInt(portStr);
                    javax.net.ssl.TrustManager[] tm = {new javax.net.ssl.X509TrustManager() {
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String t) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String t) throws java.security.cert.CertificateException {
                            if (c == null || c.length == 0) throw new java.security.cert.CertificateException("No cert");
                            try {
                                byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(c[0].getEncoded());
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < hash.length; i++) { if (i > 0) sb.append(':'); sb.append(String.format("%02X", hash[i])); }
                                if (!sb.toString().equalsIgnoreCase(fingerprint)) throw new java.security.cert.CertificateException("Fingerprint mismatch");
                            } catch (java.security.cert.CertificateException ce) { throw ce; }
                            catch (Exception e) { throw new java.security.cert.CertificateException(e.getMessage()); }
                        }
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    }};
                    javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
                    ctx.init(null, tm, new java.security.SecureRandom());
                    tcpSocket = ctx.getSocketFactory().createSocket();
                    tcpSocket.connect(new java.net.InetSocketAddress(host, port), 5000);
                    tcpSocket.setKeepAlive(true);
                    tcpOut = tcpSocket.getOutputStream();
                    tcpRunning = true;
                    NetworkService.requestStart(ShellActivity.this, "TCP connection");

                    tcpReadThread = new Thread(() -> {
                        byte[] buf = new byte[65536];
                        try {
                            java.io.InputStream in = tcpSocket.getInputStream();
                            int r;
                            while (tcpRunning && (r = in.read(buf)) != -1) {
                                if (tcpDataCallbackFn == null) continue;
                                String data = new String(buf, 0, r, java.nio.charset.StandardCharsets.UTF_8);
                                StringBuilder hex = new StringBuilder();
                                for (int i = 0; i < r; i++) hex.append(String.format("%02x", buf[i]));
                                fireEvent(tcpDataCallbackFn, "{\"data\":\"" + escapeJson(data) +
                                    "\",\"hex\":\"" + hex.toString() +
                                    "\",\"length\":" + r + "}");
                            }
                        } catch (Exception e) {
                            if (tcpRunning) Log.e("iappyxOS", "TCP read: " + e.getMessage());
                        }
                        if (tcpRunning) { tcpRunning = false; NetworkService.requestStop(ShellActivity.this); }
                        if (tcpCloseCallbackFn != null) fireEvent(tcpCloseCallbackFn, "{}");
                    });
                    tcpReadThread.setDaemon(true);
                    tcpReadThread.start();

                    deliverResult(cbId, "{\"ok\":true}");
                } catch (Exception e) {
                    if (myId == tcpConnectionId) tcpRunning = false;
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void send(String data) {
            java.io.OutputStream os = tcpOut;
            if (os == null || !tcpRunning) return;
            httpClientPool.submit(() -> {
                try { os.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)); os.flush(); }
                catch (Exception e) {
                    Log.e("iappyxOS", "TCP send: " + e.getMessage());
                    if (tcpRunning) { tcpRunning = false; NetworkService.requestStop(ShellActivity.this); }
                    if (tcpCloseCallbackFn != null) fireEvent(tcpCloseCallbackFn, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            });
        }

        @JavascriptInterface
        public void sendHex(String hexData) {
            java.io.OutputStream os = tcpOut;
            if (os == null || !tcpRunning) return;
            httpClientPool.submit(() -> {
                try {
                    int len = hexData.length() / 2;
                    byte[] bytes = new byte[len];
                    for (int i = 0; i < len; i++) bytes[i] = (byte) Integer.parseInt(hexData.substring(i * 2, i * 2 + 2), 16);
                    os.write(bytes); os.flush();
                } catch (Exception e) { Log.e("iappyxOS", "TCP sendHex: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void sendFile(String filePath) {
            java.io.OutputStream os = tcpOut;
            if (os == null || !tcpRunning) return;
            httpClientPool.submit(() -> {
                try (java.io.InputStream is = openInput(filePath)) {
                    if (is == null) return;
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = is.read(buf)) != -1) os.write(buf, 0, r);
                    os.flush();
                } catch (Exception e) { Log.e("iappyxOS", "TCP sendFile: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void onData(String callbackFn) { tcpDataCallbackFn = callbackFn; }

        @JavascriptInterface
        public void onClose(String callbackFn) { tcpCloseCallbackFn = callbackFn; }

        @JavascriptInterface
        public void close() {
            boolean wasRunning;
            synchronized (tcpLock) {
                wasRunning = tcpRunning;
                tcpRunning = false;
                tcpConnectionId++; // invalidate any in-flight connection attempts
            }
            try { if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close(); } catch (Exception ignored) {}
            tcpSocket = null;
            tcpOut = null;
            if (wasRunning) NetworkService.requestStop(ShellActivity.this);
        }

        @JavascriptInterface
        public boolean isConnected() {
            return tcpRunning && tcpSocket != null && tcpSocket.isConnected() && !tcpSocket.isClosed();
        }
    }

    // ── UDP ──
    private java.net.MulticastSocket udpSocket;
    private Thread udpReceiveThread;
    private String udpReceiveCallbackFn;
    private volatile boolean udpRunning = false;

    class UdpBridge {
        @JavascriptInterface
        public void open(String portStr, String cbId) {
            if (udpRunning) { deliverResult(cbId, "{\"ok\":false,\"error\":\"already open\"}"); return; }
            try {
                int port = Integer.parseInt(portStr);
                udpSocket = new java.net.MulticastSocket(null);
                udpSocket.setReuseAddress(true);
                if (port != 0) udpSocket.bind(new java.net.InetSocketAddress(port));
                udpRunning = true;
                int actualPort = udpSocket.getLocalPort();
                // Start receive loop
                udpReceiveThread = new Thread(() -> {
                    byte[] buf = new byte[65507];
                    while (udpRunning && udpSocket != null && !udpSocket.isClosed()) {
                        try {
                            java.net.DatagramPacket pkt = new java.net.DatagramPacket(buf, buf.length);
                            udpSocket.receive(pkt);
                            if (udpReceiveCallbackFn == null) continue;
                            String data = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), java.nio.charset.StandardCharsets.UTF_8);
                            StringBuilder hex = new StringBuilder();
                            for (int i = pkt.getOffset(); i < pkt.getOffset() + pkt.getLength(); i++) {
                                hex.append(String.format("%02x", buf[i]));
                            }
                            String from = pkt.getAddress().getHostAddress();
                            int srcPort = pkt.getPort();
                            fireEvent(udpReceiveCallbackFn, "{\"from\":\"" + escapeJson(from) +
                                "\",\"port\":" + srcPort +
                                ",\"data\":\"" + escapeJson(data) +
                                "\",\"hex\":\"" + hex.toString() + "\"}");
                        } catch (Exception e) {
                            if (udpRunning) Log.e("iappyxOS", "UDP receive error: " + e.getMessage());
                            break;
                        }
                    }
                });
                udpReceiveThread.setDaemon(true);
                udpReceiveThread.start();
                deliverResult(cbId, "{\"ok\":true,\"port\":" + actualPort + "}");
            } catch (Exception e) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        @JavascriptInterface
        public void close() {
            udpRunning = false;
            if (udpSocket != null && !udpSocket.isClosed()) {
                try { udpSocket.close(); } catch (Exception ignored) {}
            }
            udpSocket = null;
            releaseMulticastLock();
        }

        @JavascriptInterface
        public void send(String host, String portStr, String data) {
            java.net.MulticastSocket sock = udpSocket;
            if (sock == null || sock.isClosed()) return;
            httpClientPool.submit(() -> {
                try {
                    int port = Integer.parseInt(portStr);
                    byte[] bytes = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    java.net.DatagramPacket pkt = new java.net.DatagramPacket(bytes, bytes.length,
                        java.net.InetAddress.getByName(host), port);
                    sock.send(pkt);
                } catch (Exception e) { Log.e("iappyxOS", "UDP send: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void sendHex(String host, String portStr, String hexData) {
            java.net.MulticastSocket sock = udpSocket;
            if (sock == null || sock.isClosed()) return;
            httpClientPool.submit(() -> {
                try {
                    int port = Integer.parseInt(portStr);
                    int len = hexData.length() / 2;
                    byte[] bytes = new byte[len];
                    for (int i = 0; i < len; i++) bytes[i] = (byte) Integer.parseInt(hexData.substring(i * 2, i * 2 + 2), 16);
                    java.net.DatagramPacket pkt = new java.net.DatagramPacket(bytes, bytes.length,
                        java.net.InetAddress.getByName(host), port);
                    sock.send(pkt);
                } catch (Exception e) { Log.e("iappyxOS", "UDP sendHex: " + e.getMessage()); }
            });
        }

        @JavascriptInterface
        public void onReceive(String callbackFn) {
            udpReceiveCallbackFn = callbackFn;
        }

        @JavascriptInterface
        public void joinMulticast(String group) {
            if (udpSocket == null) return;
            try {
                acquireMulticastLock();
                udpSocket.joinGroup(java.net.InetAddress.getByName(group));
            } catch (Exception e) { Log.e("iappyxOS", "UDP joinMulticast: " + e.getMessage()); }
        }

        @JavascriptInterface
        public void leaveMulticast(String group) {
            if (udpSocket == null) return;
            try {
                udpSocket.leaveGroup(java.net.InetAddress.getByName(group));
                releaseMulticastLock();
            } catch (Exception e) { Log.e("iappyxOS", "UDP leaveMulticast: " + e.getMessage()); }
        }
    }

    private void acquireMulticastLock() {
        if (multicastLock == null) {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("iappyx_nsd");
                multicastLock.setReferenceCounted(false);
            }
        }
        if (multicastLock != null && !multicastLock.isHeld()) multicastLock.acquire();
    }

    private void releaseMulticastLock() {
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
    }

    // ── NSD (Network Service Discovery / mDNS) ──
    class NsdBridge {
        @JavascriptInterface
        public void register(String serviceType, String serviceName, String portStr, String txtRecordsJson, String cbId) {
            try {
                int port = Integer.parseInt(portStr);
                if (nsdManager == null) nsdManager = (android.net.nsd.NsdManager) getSystemService(Context.NSD_SERVICE);
                acquireMulticastLock();
                // Unregister previous if any
                if (nsdRegistrationListener != null) {
                    try { nsdManager.unregisterService(nsdRegistrationListener); } catch (Exception ignored) {}
                    nsdRegistrationListener = null;
                }
                android.net.nsd.NsdServiceInfo info = new android.net.nsd.NsdServiceInfo();
                info.setServiceName(serviceName);
                info.setServiceType(serviceType);
                info.setPort(port);
                if (txtRecordsJson != null && !txtRecordsJson.isEmpty()) {
                    try {
                        JSONObject txt = new JSONObject(txtRecordsJson);
                        java.util.Iterator<String> keys = txt.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            info.setAttribute(k, txt.optString(k, ""));
                        }
                    } catch (Exception ignored) {}
                }
                nsdRegistrationListener = new android.net.nsd.NsdManager.RegistrationListener() {
                    @Override public void onRegistrationFailed(android.net.nsd.NsdServiceInfo si, int err) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"registration failed: " + err + "\"}");
                    }
                    @Override public void onUnregistrationFailed(android.net.nsd.NsdServiceInfo si, int err) {}
                    @Override public void onServiceRegistered(android.net.nsd.NsdServiceInfo si) {
                        deliverResult(cbId, "{\"ok\":true,\"serviceName\":\"" + escapeJson(si.getServiceName()) + "\"}");
                    }
                    @Override public void onServiceUnregistered(android.net.nsd.NsdServiceInfo si) {}
                };
                nsdManager.registerService(info, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener);
            } catch (Exception e) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }

        @JavascriptInterface
        public void unregister() {
            if (nsdManager != null && nsdRegistrationListener != null) {
                try { nsdManager.unregisterService(nsdRegistrationListener); } catch (Exception ignored) {}
                nsdRegistrationListener = null;
            }
        }

        @JavascriptInterface
        public void startDiscovery(String serviceType, String callbackFn) {
            if (nsdManager == null) nsdManager = (android.net.nsd.NsdManager) getSystemService(Context.NSD_SERVICE);
            acquireMulticastLock();
            // Stop previous discovery if any
            if (nsdDiscoveryListener != null) {
                try { nsdManager.stopServiceDiscovery(nsdDiscoveryListener); } catch (Exception ignored) {}
                nsdDiscoveryListener = null;
            }
            nsdDiscoveryCallbackFn = callbackFn;
            nsdDiscoveryListener = new android.net.nsd.NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String type) {}
                @Override public void onDiscoveryStopped(String type) {}
                @Override public void onStartDiscoveryFailed(String type, int err) {
                    fireEvent(nsdDiscoveryCallbackFn, "{\"event\":\"error\",\"error\":\"discovery start failed: " + err + "\"}");
                }
                @Override public void onStopDiscoveryFailed(String type, int err) {}
                @Override public void onServiceFound(android.net.nsd.NsdServiceInfo si) {
                    fireEvent(nsdDiscoveryCallbackFn, "{\"event\":\"found\",\"serviceName\":\"" +
                        escapeJson(si.getServiceName()) + "\",\"serviceType\":\"" + escapeJson(si.getServiceType()) + "\"}");
                }
                @Override public void onServiceLost(android.net.nsd.NsdServiceInfo si) {
                    fireEvent(nsdDiscoveryCallbackFn, "{\"event\":\"lost\",\"serviceName\":\"" +
                        escapeJson(si.getServiceName()) + "\",\"serviceType\":\"" + escapeJson(si.getServiceType()) + "\"}");
                }
            };
            nsdManager.discoverServices(serviceType, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener);
        }

        @JavascriptInterface
        public void stopDiscovery() {
            if (nsdManager != null && nsdDiscoveryListener != null) {
                try { nsdManager.stopServiceDiscovery(nsdDiscoveryListener); } catch (Exception ignored) {}
                nsdDiscoveryListener = null;
            }
        }

        @JavascriptInterface
        public void resolve(String serviceType, String serviceName, String cbId) {
            if (nsdManager == null) nsdManager = (android.net.nsd.NsdManager) getSystemService(Context.NSD_SERVICE);
            Runnable task = () -> {
                nsdResolving = true;
                android.net.nsd.NsdServiceInfo info = new android.net.nsd.NsdServiceInfo();
                info.setServiceName(serviceName);
                info.setServiceType(serviceType);
                nsdManager.resolveService(info, new android.net.nsd.NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(android.net.nsd.NsdServiceInfo si, int err) {
                        deliverResult(cbId, "{\"ok\":false,\"error\":\"resolve failed: " + err + "\"}");
                        synchronized (nsdResolveQueue) { nsdResolving = false; }
                        processNextResolve();
                    }
                    @Override public void onServiceResolved(android.net.nsd.NsdServiceInfo si) {
                        try {
                            JSONObject r = new JSONObject();
                            r.put("ok", true);
                            r.put("host", si.getHost() != null ? si.getHost().getHostAddress() : "");
                            r.put("port", si.getPort());
                            JSONObject txt = new JSONObject();
                            Map<String, byte[]> attrs = si.getAttributes();
                            if (attrs != null) {
                                for (Map.Entry<String, byte[]> e : attrs.entrySet()) {
                                    txt.put(e.getKey(), e.getValue() != null ? new String(e.getValue(), java.nio.charset.StandardCharsets.UTF_8) : "");
                                }
                            }
                            r.put("txtRecords", txt);
                            deliverResult(cbId, r.toString());
                        } catch (Exception e) {
                            deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                        }
                        synchronized (nsdResolveQueue) { nsdResolving = false; }
                        processNextResolve();
                    }
                });
            };
            synchronized (nsdResolveQueue) {
                if (nsdResolving) {
                    nsdResolveQueue.add(task);
                } else {
                    task.run();
                }
            }
        }
    }

    private void processNextResolve() {
        synchronized (nsdResolveQueue) {
            Runnable next = nsdResolveQueue.poll();
            if (next != null) next.run();
        }
    }

    // ── HTTP Server ──
    class HttpServerBridge {
        @JavascriptInterface
        public void start(String portStr, String useTlsStr, String cbId) {
            boolean useTls = "true".equalsIgnoreCase(useTlsStr);
            if (httpServerRunning) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"server already running\"}");
                return;
            }
            int port;
            try { port = Integer.parseInt(portStr); } catch (Exception e) {
                deliverResult(cbId, "{\"ok\":false,\"error\":\"invalid port: " + escapeJson(portStr) + "\"}");
                return;
            }
            final int fPort = port;
            httpServerRunning = true;

            if (useTls) {
                try {
                    // Generate self-signed cert via Android Keystore
                    java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
                    ks.load(null);
                    if (ks.containsAlias("iappyx_http_tls")) ks.deleteEntry("iappyx_http_tls");

                    java.util.Calendar notAfter = java.util.Calendar.getInstance();
                    notAfter.add(java.util.Calendar.YEAR, 1);

                    android.security.keystore.KeyGenParameterSpec spec = new android.security.keystore.KeyGenParameterSpec.Builder(
                        "iappyx_http_tls",
                        android.security.keystore.KeyProperties.PURPOSE_SIGN | android.security.keystore.KeyProperties.PURPOSE_VERIFY)
                        .setKeySize(2048)
                        .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setCertificateSubject(new javax.security.auth.x500.X500Principal("CN=iappyx"))
                        .setCertificateSerialNumber(java.math.BigInteger.ONE)
                        .setCertificateNotBefore(java.util.Calendar.getInstance().getTime())
                        .setCertificateNotAfter(notAfter.getTime())
                        .build();
                    java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance(
                        android.security.keystore.KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
                    kpg.initialize(spec);
                    kpg.generateKeyPair();

                    ks.load(null); // reload
                    httpSelfSignedCert = (java.security.cert.X509Certificate) ks.getCertificate("iappyx_http_tls");

                    javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                        javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(ks, null);
                    javax.net.ssl.SSLContext sslCtx = javax.net.ssl.SSLContext.getInstance("TLS");
                    sslCtx.init(kmf.getKeyManagers(), null, null);
                    httpSslServerSocket = (javax.net.ssl.SSLServerSocket) sslCtx.getServerSocketFactory().createServerSocket(fPort);
                    startAcceptLoop(httpSslServerSocket, fPort, cbId);
                } catch (Exception e) {
                    httpServerRunning = false;
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"TLS setup failed: " + escapeJson(e.getMessage()) + "\"}");
                }
            } else {
                try {
                    httpPlainServerSocket = new java.net.ServerSocket(fPort);
                    httpSelfSignedCert = null;
                    startAcceptLoop(httpPlainServerSocket, fPort, cbId);
                } catch (Exception e) {
                    httpServerRunning = false;
                    deliverResult(cbId, "{\"ok\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        }

        @JavascriptInterface
        public void stop() {
            stopHttpServer();
        }

        @JavascriptInterface
        public void onRequest(String callbackFn) {
            httpRequestCallbackFn = callbackFn;
        }

        @JavascriptInterface
        public void respond(String requestId, String statusCodeStr, String headersJson, String body) {
            HttpPendingRequest req = httpPendingRequests.get(requestId);
            if (req == null) return;
            try { req.statusCode = Integer.parseInt(statusCodeStr); } catch (Exception e) { req.statusCode = 200; }
            req.responseHeaders = headersJson;
            req.responseBody = body;
            req.responseFilePath = null;
            req.latch.countDown();
        }

        @JavascriptInterface
        public void respondFile(String requestId, String statusCodeStr, String headersJson, String filePath) {
            HttpPendingRequest req = httpPendingRequests.get(requestId);
            if (req == null) return;
            try { req.statusCode = Integer.parseInt(statusCodeStr); } catch (Exception e) { req.statusCode = 200; }
            req.responseHeaders = headersJson;
            req.responseBody = null;
            req.responseFilePath = isContentUri(filePath) ? filePath : resolveFilePath(filePath);
            req.latch.countDown();
        }

        @JavascriptInterface
        public String getCertificatePem() {
            if (httpSelfSignedCert == null) return null;
            try {
                return "-----BEGIN CERTIFICATE-----\n" +
                    Base64.encodeToString(httpSelfSignedCert.getEncoded(), Base64.DEFAULT) +
                    "-----END CERTIFICATE-----";
            } catch (Exception e) { return null; }
        }

        @JavascriptInterface
        public String getCertificateFingerprint() {
            if (httpSelfSignedCert == null) return null;
            try {
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(httpSelfSignedCert.getEncoded());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < hash.length; i++) {
                    if (i > 0) sb.append(':');
                    sb.append(String.format("%02X", hash[i]));
                }
                return sb.toString();
            } catch (Exception e) { return null; }
        }

        @JavascriptInterface
        public String getLocalIpAddress() {
            try {
                java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
                while (ifaces.hasMoreElements()) {
                    java.net.NetworkInterface iface = ifaces.nextElement();
                    if (iface.isLoopback() || !iface.isUp()) continue;
                    java.util.Enumeration<java.net.InetAddress> addrs = iface.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        java.net.InetAddress addr = addrs.nextElement();
                        if (addr instanceof java.net.Inet4Address) return addr.getHostAddress();
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }
    }

    private void startAcceptLoop(java.net.ServerSocket serverSocket, int port, String cbId) {
        httpServerPool = java.util.concurrent.Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        NetworkService.requestStart(this, "HTTP server");
        String fingerprint = null;
        if (httpSelfSignedCert != null) {
            try {
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(httpSelfSignedCert.getEncoded());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < hash.length; i++) {
                    if (i > 0) sb.append(':');
                    sb.append(String.format("%02X", hash[i]));
                }
                fingerprint = sb.toString();
            } catch (Exception ignored) {}
        }
        final String fp = fingerprint;
        int actualPort = serverSocket.getLocalPort();
        deliverResult(cbId, "{\"ok\":true,\"port\":" + actualPort +
            (fp != null ? ",\"fingerprint\":\"" + fp + "\"" : "") + "}");

        httpServerThread = new Thread(() -> {
            while (httpServerRunning && !serverSocket.isClosed()) {
                try {
                    java.net.Socket client = serverSocket.accept();
                    httpServerPool.submit(() -> handleHttpClient(client));
                } catch (Exception e) {
                    if (httpServerRunning) Log.e("iappyxOS", "HTTP accept error: " + e.getMessage());
                    break;
                }
            }
        });
        httpServerThread.setDaemon(true);
        httpServerThread.start();
    }

    private void handleHttpClient(java.net.Socket client) {
        String requestId = java.util.UUID.randomUUID().toString();
        try {
            client.setSoTimeout(30000);
            java.io.InputStream in = client.getInputStream();
            java.io.OutputStream out = client.getOutputStream();

            // Read request line
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) { client.close(); return; }

            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String fullPath = parts.length > 1 ? parts[1] : "/";
            String path = fullPath.contains("?") ? fullPath.substring(0, fullPath.indexOf('?')) : fullPath;
            String query = fullPath.contains("?") ? fullPath.substring(fullPath.indexOf('?') + 1) : "";

            // Read headers
            JSONObject headers = new JSONObject();
            long contentLength = -1;
            String contentType = "";
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String hName = line.substring(0, colon).trim();
                    String hVal = line.substring(colon + 1).trim();
                    headers.put(hName, hVal);
                    if (hName.equalsIgnoreCase("Content-Length")) {
                        try { contentLength = Long.parseLong(hVal); } catch (Exception ignored) {}
                    }
                    if (hName.equalsIgnoreCase("Content-Type")) contentType = hVal;
                }
            }

            // Handle CORS preflight
            if ("OPTIONS".equals(method)) {
                String resp = "HTTP/1.1 204 No Content\r\n" + CORS_HEADERS + "Connection: close\r\n\r\n";
                out.write(resp.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.flush();
                client.close();
                return;
            }

            // Read body
            String bodyStr = null;
            String bodyFile = null;
            if (contentLength > 0) {
                boolean isText = contentType.startsWith("text/") || contentType.contains("json") || contentType.contains("xml") || contentType.contains("form-urlencoded") || contentType.contains("javascript");
                if (isText && contentLength <= 2097152) { // 2MB for text/json bodies
                    // Small text body — pass as string
                    byte[] bodyBytes = new byte[(int) contentLength];
                    int off = 0;
                    while (off < bodyBytes.length) {
                        int r = in.read(bodyBytes, off, bodyBytes.length - off);
                        if (r < 0) break;
                        off += r;
                    }
                    bodyStr = new String(bodyBytes, 0, off, java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    // Large/binary body — stream to file
                    File temp = new File(getCacheDir(), "http_upload_" + requestId);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(temp)) {
                        byte[] buf = new byte[65536];
                        long remaining = contentLength;
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buf.length, remaining);
                            int r = in.read(buf, 0, toRead);
                            if (r < 0) break;
                            fos.write(buf, 0, r);
                            remaining -= r;
                        }
                    }
                    bodyFile = temp.getAbsolutePath();
                }
            }

            // Build event JSON
            JSONObject event = new JSONObject();
            event.put("requestId", requestId);
            event.put("method", method);
            event.put("path", path);
            event.put("query", query);
            event.put("headers", headers);
            event.put("bodyLength", contentLength);
            if (bodyStr != null) event.put("body", bodyStr);
            if (bodyFile != null) event.put("bodyFile", bodyFile);

            // Register pending request and notify JS
            HttpPendingRequest pending = new HttpPendingRequest(client, out);
            httpPendingRequests.put(requestId, pending);

            if (httpRequestCallbackFn != null) {
                fireEvent(httpRequestCallbackFn, event.toString());
            } else {
                // No handler registered yet — respond immediately
                writeHttpResponse(out, 503, "{}", "No request handler registered");
                httpPendingRequests.remove(requestId);
                return;
            }

            // Wait for JS to call respond() or timeout
            boolean responded = pending.latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
            httpPendingRequests.remove(requestId);

            if (!responded) {
                writeHttpResponse(out, 500, "{}", "Timeout — no response from app");
            } else if (pending.responseFilePath != null) {
                // Stream file response (supports file paths and content:// URIs)
                String fp = pending.responseFilePath;
                long contentSize = getContentSize(fp);
                if (contentSize < 0) {
                    writeHttpResponse(out, 404, "{}", "File not found");
                } else {
                    JSONObject respHeaders = pending.responseHeaders != null ? new JSONObject(pending.responseHeaders) : new JSONObject();
                    if (!respHeaders.has("Content-Length") && contentSize > 0) respHeaders.put("Content-Length", String.valueOf(contentSize));
                    StringBuilder sb = new StringBuilder();
                    sb.append("HTTP/1.1 ").append(pending.statusCode).append(" ").append(httpReason(pending.statusCode)).append("\r\n");
                    sb.append(CORS_HEADERS);
                    java.util.Iterator<String> keys = respHeaders.keys();
                    while (keys.hasNext()) { String k = keys.next(); sb.append(k).append(": ").append(respHeaders.getString(k)).append("\r\n"); }
                    sb.append("Connection: close\r\n\r\n");
                    out.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    try (java.io.InputStream fis = openInput(fp)) {
                        if (fis != null) {
                            byte[] buf = new byte[65536];
                            int r;
                            while ((r = fis.read(buf)) != -1) out.write(buf, 0, r);
                        }
                    }
                }
            } else {
                writeHttpResponse(out, pending.statusCode,
                    pending.responseHeaders != null ? pending.responseHeaders : "{}",
                    pending.responseBody != null ? pending.responseBody : "");
            }
            out.flush();
            client.close();

            // Clean up upload temp file
            if (bodyFile != null) new File(bodyFile).delete();
        } catch (Exception e) {
            httpPendingRequests.remove(requestId);
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private String readLine(java.io.InputStream in) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) break; // prevent OOM from malicious clients
        }
        return c == -1 && sb.length() == 0 ? null : sb.toString();
    }

    private static final String CORS_HEADERS =
        "Access-Control-Allow-Origin: *\r\n" +
        "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
        "Access-Control-Allow-Headers: *\r\n";

    private static String httpReason(int status) {
        switch (status) {
            case 200: return "OK"; case 201: return "Created"; case 204: return "No Content";
            case 301: return "Moved Permanently"; case 302: return "Found"; case 304: return "Not Modified";
            case 400: return "Bad Request"; case 401: return "Unauthorized"; case 403: return "Forbidden";
            case 404: return "Not Found"; case 405: return "Method Not Allowed"; case 408: return "Request Timeout";
            case 500: return "Internal Server Error"; case 502: return "Bad Gateway"; case 503: return "Service Unavailable";
            default: return status < 400 ? "OK" : "Error";
        }
    }

    private void writeHttpResponse(java.io.OutputStream out, int status, String headersJson, String body) throws Exception {
        JSONObject h = new JSONObject(headersJson);
        byte[] bodyBytes = body != null ? body.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
        if (!h.has("Content-Length")) h.put("Content-Length", String.valueOf(bodyBytes.length));
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status).append(" ").append(httpReason(status)).append("\r\n");
        sb.append(CORS_HEADERS);
        java.util.Iterator<String> keys = h.keys();
        while (keys.hasNext()) { String k = keys.next(); sb.append(k).append(": ").append(h.getString(k)).append("\r\n"); }
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (bodyBytes.length > 0) out.write(bodyBytes);
    }

    private boolean isContentUri(String path) {
        return path != null && path.startsWith("content://");
    }

    private java.io.InputStream openInput(String pathOrUri) throws Exception {
        if (isContentUri(pathOrUri)) {
            return getContentResolver().openInputStream(Uri.parse(pathOrUri));
        }
        return new java.io.FileInputStream(resolveFilePath(pathOrUri));
    }

    private long getContentSize(String pathOrUri) {
        if (isContentUri(pathOrUri)) {
            try (Cursor c = getContentResolver().query(Uri.parse(pathOrUri),
                    new String[]{android.provider.OpenableColumns.SIZE}, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getLong(0);
            } catch (Exception ignored) {}
            return -1;
        }
        File f = new File(resolveFilePath(pathOrUri));
        return f.exists() ? f.length() : -1;
    }

    private String resolveFilePath(String filePath) {
        if (filePath == null) return null;
        if (filePath.startsWith("file://")) return Uri.parse(filePath).getPath();
        if (filePath.startsWith("/")) return filePath;
        if (filePath.startsWith("downloads:")) {
            return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                filePath.substring(10)).getAbsolutePath();
        }
        return new File(getFilesDir(), filePath).getAbsolutePath();
    }

    private void stopHttpServer() {
        boolean wasRunning = httpServerRunning;
        httpServerRunning = false;
        if (wasRunning) NetworkService.requestStop(this);
        try { if (httpSslServerSocket != null) httpSslServerSocket.close(); } catch (Exception ignored) {}
        try { if (httpPlainServerSocket != null) httpPlainServerSocket.close(); } catch (Exception ignored) {}
        httpSslServerSocket = null;
        httpPlainServerSocket = null;
        if (httpServerPool != null) { httpServerPool.shutdownNow(); httpServerPool = null; }
        for (Map.Entry<String, HttpPendingRequest> entry : httpPendingRequests.entrySet()) {
            try { entry.getValue().socket.close(); } catch (Exception ignored) {}
            entry.getValue().latch.countDown();
        }
        httpPendingRequests.clear();
        // Clean temp upload files
        File[] temps = getCacheDir().listFiles((dir, name) -> name.startsWith("http_upload_"));
        if (temps != null) for (File f : temps) f.delete();
        // Delete TLS key
        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias("iappyx_http_tls")) ks.deleteEntry("iappyx_http_tls");
        } catch (Exception ignored) {}
        httpSelfSignedCert = null;
    }

    class CapabilitiesBridge {
        @JavascriptInterface
        public String get() {
            try {
                JSONObject caps = new JSONObject();
                caps.put("version", "1.0");
                caps.put("sdk", Build.VERSION.SDK_INT);

                JSONObject bridges = new JSONObject();
                bridges.put("storage", true);
                bridges.put("device", true);
                bridges.put("camera", true);
                bridges.put("location", true);
                bridges.put("notification", true);
                bridges.put("vibration", true);
                bridges.put("clipboard", true);
                bridges.put("sensor", true);
                bridges.put("tts", true);
                bridges.put("alarm", true);
                bridges.put("audio", true);
                bridges.put("screen", true);
                bridges.put("contacts", true);
                bridges.put("sms", true);
                bridges.put("calendar", true);
                bridges.put("biometric", Build.VERSION.SDK_INT >= Build.VERSION_CODES.P);
                bridges.put("nfc", nfcAdapter != null);
                bridges.put("sqlite", true);
                bridges.put("download", true);
                bridges.put("media", true);
                bridges.put("httpServer", true);
                bridges.put("httpClient", true);
                bridges.put("ssh", true);
                bridges.put("smb", true);
                bridges.put("ble", true);
                initFirebaseIfNeeded();
                bridges.put("push", firebaseInitialized);
                bridges.put("tcp", true);
                bridges.put("nsd", true);
                bridges.put("udp", true);
                bridges.put("wifiDirect", true);
                caps.put("bridges", bridges);

                JSONObject perms = new JSONObject();
                perms.put("camera", ContextCompat.checkSelfPermission(ShellActivity.this,
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ? "granted" : "unasked");
                perms.put("location", ContextCompat.checkSelfPermission(ShellActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ? "granted" : "unasked");
                perms.put("contacts", ContextCompat.checkSelfPermission(ShellActivity.this,
                    Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED ? "granted" : "unasked");
                perms.put("sms", ContextCompat.checkSelfPermission(ShellActivity.this,
                    Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED ? "granted" : "unasked");
                perms.put("calendar", ContextCompat.checkSelfPermission(ShellActivity.this,
                    Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED ? "granted" : "unasked");
                caps.put("permissions", perms);

                return caps.toString();
            } catch (Exception e) {
                return "{}";
            }
        }
    }
}
