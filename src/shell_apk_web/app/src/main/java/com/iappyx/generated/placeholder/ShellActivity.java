package com.iappyx.generated.placeholder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * Lightweight WebView shell for "Website as App" — no native bridges.
 * Loads index.html which redirects to the target URL.
 */
public class ShellActivity extends Activity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private TextView offlineBanner;
    private ValueCallback<Uri[]> pendingFileCallback;
    private static final int REQ_FILE_PICKER = 2002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#0d0d1a"));
        }

        // Root layout
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0d0d1a"));

        // WebView inside SwipeRefreshLayout
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#0d0d1a"));
        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.addView(webView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        swipeRefresh.setOnRefreshListener(() -> webView.reload());
        swipeRefresh.setColorSchemeColors(Color.parseColor("#4FC3F7"));
        root.addView(swipeRefresh, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Progress bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 6);
        pbParams.gravity = Gravity.TOP;
        root.addView(progressBar, pbParams);

        // Offline banner
        offlineBanner = new TextView(this);
        offlineBanner.setText("No internet connection");
        offlineBanner.setTextColor(Color.WHITE);
        offlineBanner.setBackgroundColor(Color.parseColor("#FF6B6B"));
        offlineBanner.setGravity(Gravity.CENTER);
        offlineBanner.setPadding(0, 8, 0, 8);
        offlineBanner.setTextSize(12);
        offlineBanner.setVisibility(View.GONE);
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bannerParams.gravity = Gravity.TOP;
        root.addView(offlineBanner, bannerParams);

        setContentView(root);

        // WebView settings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " iappyxOS-Web/1.0");

        // WebViewClient — URL routing, offline handling
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if (scheme == null) return false;
                // Keep http/https/file in WebView
                if ("http".equals(scheme) || "https".equals(scheme) || "file".equals(scheme)) {
                    return false;
                }
                // Route tel:, mailto:, geo:, sms:, market:// etc. to system
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (!isNetworkAvailable()) {
                    offlineBanner.setVisibility(View.VISIBLE);
                }
                view.loadData(
                    "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<style>body{font-family:sans-serif;background:#0d0d1a;color:#eaeaea;display:flex;" +
                    "flex-direction:column;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:20px;text-align:center}" +
                    "h2{font-size:20px;margin-bottom:8px}p{color:rgba(255,255,255,0.5);font-size:14px;margin-bottom:24px}" +
                    "button{background:#1a1a2e;color:#fff;border:none;padding:14px 24px;border-radius:50px;" +
                    "font-size:15px;margin:0 6px;cursor:pointer;min-height:44px}</style></head><body>" +
                    "<h2>Can\u2019t reach this page</h2>" +
                    "<p>Check your internet connection and try again.</p>" +
                    "<div>" +
                    "<button onclick='history.back()'>Back</button> " +
                    "<button onclick='location.reload()'>Retry</button>" +
                    "</div></body></html>",
                    "text/html", "UTF-8");
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                offlineBanner.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                swipeRefresh.setRefreshing(false);
                progressBar.setVisibility(View.GONE);
                // No bridge injection — this is a sandboxed web shell
            }
        });

        // WebChromeClient — progress bar + file chooser
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (pendingFileCallback != null) pendingFileCallback.onReceiveValue(null);
                pendingFileCallback = filePathCallback;
                try {
                    startActivityForResult(fileChooserParams.createIntent(), REQ_FILE_PICKER);
                } catch (Exception e) {
                    pendingFileCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Load the redirect page
        webView.loadUrl("file:///android_asset/app/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_PICKER && pendingFileCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                String dataStr = data.getDataString();
                if (dataStr != null) results = new Uri[]{Uri.parse(dataStr)};
            }
            pendingFileCallback.onReceiveValue(results);
            pendingFileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }
}
