package com.iappyx.generated.placeholder;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

public class WidgetProvider extends AppWidgetProvider {

    private static final String ACTION_WIDGET_TAP = "com.iappyx.WIDGET_TAP";
    private static final String PREFS = "iappyx_widget";
    private static final String KEY_CONFIG = "config";

    private static int getLayoutRes(String layout) {
        if (layout == null) layout = "100";
        switch (layout) {
            case "50/50": return R.layout.widget_2col_50_50;
            case "30/70": return R.layout.widget_2col_30_70;
            case "70/30": return R.layout.widget_2col_70_30;
            case "33/33/33": return R.layout.widget_3col_33;
            case "50/25/25": return R.layout.widget_3col_50_25_25;
            case "25/25/50": return R.layout.widget_3col_25_25_50;
            case "25/25/25/25": return R.layout.widget_4col_25;
            default: return R.layout.widget_1col;
        }
    }

    // Map cell position to R.id fields
    private static int cellId(int row, int col, String suffix) {
        // Use reflection-free mapping for the 4x4 grid
        String name = "r" + row + "c" + col + "_" + suffix;
        try {
            return R.id.class.getField(name).getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int rowId(int row) {
        try {
            return R.id.class.getField("row_" + row).getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int cellContainerId(int row, int col) {
        try {
            return R.id.class.getField("cell_r" + row + "c" + col).getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String configJson = prefs.getString(KEY_CONFIG, null);
        if (configJson == null) return;

        try {
            JSONObject config = new JSONObject(configJson);
            String layout = config.optString("layout", "100");
            int layoutRes = getLayoutRes(layout);

            for (int widgetId : appWidgetIds) {
                RemoteViews views = new RemoteViews(context.getPackageName(), layoutRes);

                // Background color
                String bg = config.optString("background", "");
                if (!bg.isEmpty()) {
                    try { views.setInt(R.id.widget_root, "setBackgroundColor", Color.parseColor(bg)); }
                    catch (Exception ignored) {}
                }

                // Padding
                int pad = config.optInt("padding", 8);
                int padPx = (int) (pad * context.getResources().getDisplayMetrics().density);
                views.setViewPadding(R.id.widget_root, padPx, padPx, padPx, padPx);

                // Root tap opens app
                Intent launchIntent = new Intent();
                launchIntent.setComponent(new ComponentName(context.getPackageName(),
                    "com.iappyx.generated.placeholder.ShellActivity"));
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent launchPi = PendingIntent.getActivity(context, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.widget_root, launchPi);

                // Hide all rows first
                for (int r = 0; r < 4; r++) {
                    int rid = rowId(r);
                    if (rid != 0) views.setViewVisibility(rid, View.GONE);
                }

                // Populate rows
                JSONArray rows = config.optJSONArray("rows");
                if (rows != null) {
                    int maxCols = getMaxCols(layout);
                    for (int r = 0; r < Math.min(rows.length(), 4); r++) {
                        JSONObject row = rows.getJSONObject(r);
                        JSONArray cells = row.optJSONArray("cells");
                        if (cells == null || cells.length() == 0) continue;

                        int rid = rowId(r);
                        if (rid != 0) views.setViewVisibility(rid, View.VISIBLE);

                        for (int c = 0; c < maxCols; c++) {
                            int cid = cellContainerId(r, c);
                            if (cid == 0) continue;

                            if (c < cells.length()) {
                                JSONObject cell = cells.getJSONObject(c);
                                views.setViewVisibility(cid, View.VISIBLE);
                                populateCell(context, views, r, c, cell, widgetId);
                            } else {
                                views.setViewVisibility(cid, View.GONE);
                            }
                        }
                    }
                }

                manager.updateAppWidget(widgetId, views);
            }
        } catch (Exception e) {
            android.util.Log.e("iappyxOS", "Widget update error: " + e.getMessage());
        }
    }

    private int getMaxCols(String layout) {
        if (layout == null) return 1;
        switch (layout) {
            case "50/50": case "30/70": case "70/30": return 2;
            case "33/33/33": case "50/25/25": case "25/25/50": return 3;
            case "25/25/25/25": return 4;
            default: return 1;
        }
    }

    private void populateCell(Context context, RemoteViews views, int row, int col, JSONObject cell, int widgetId) {
        try {
            // Icon
            String icon = cell.optString("icon", "");
            int iconId = cellId(row, col, "icon");
            if (!icon.isEmpty() && iconId != 0) {
                try {
                    byte[] bytes = Base64.decode(icon, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) { views.setImageViewBitmap(iconId, bmp); views.setViewVisibility(iconId, View.VISIBLE); bmp.recycle(); }
                } catch (Exception ignored) {}
            } else if (iconId != 0) { views.setViewVisibility(iconId, View.GONE); }

            // Image
            String image = cell.optString("image", "");
            int imageId = cellId(row, col, "image");
            if (!image.isEmpty() && imageId != 0) {
                try {
                    byte[] bytes = Base64.decode(image, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) { views.setImageViewBitmap(imageId, bmp); views.setViewVisibility(imageId, View.VISIBLE); bmp.recycle(); }
                } catch (Exception ignored) {}
            } else if (imageId != 0) { views.setViewVisibility(imageId, View.GONE); }

            // Clock
            int clockId = cellId(row, col, "clock");
            if (cell.has("clock") && clockId != 0) {
                views.setString(clockId, "setTimeZone", cell.getString("clock"));
                views.setViewVisibility(clockId, View.VISIBLE);
            } else if (clockId != 0) { views.setViewVisibility(clockId, View.GONE); }

            // Timer
            int timerId = cellId(row, col, "timer");
            if (cell.has("timer") && timerId != 0) {
                JSONObject timer = cell.getJSONObject("timer");
                long targetMs = timer.optLong("targetMs", 0);
                boolean countDown = timer.optBoolean("countDown", false);
                if (countDown) {
                    views.setChronometer(timerId, SystemClock.elapsedRealtime() + targetMs, null, true);
                    views.setChronometerCountDown(timerId, true);
                } else {
                    views.setChronometer(timerId, SystemClock.elapsedRealtime() - targetMs, null, true);
                    views.setChronometerCountDown(timerId, false);
                }
                views.setViewVisibility(timerId, View.VISIBLE);
            } else if (timerId != 0) { views.setViewVisibility(timerId, View.GONE); }

            // Title
            String title = cell.optString("title", "");
            int titleId = cellId(row, col, "title");
            if (!title.isEmpty() && titleId != 0) {
                views.setTextViewText(titleId, title);
                views.setViewVisibility(titleId, View.VISIBLE);
                String titleColor = cell.optString("titleColor", "");
                if (!titleColor.isEmpty()) { try { views.setTextColor(titleId, Color.parseColor(titleColor)); } catch (Exception ignored) {} }
                float titleSize = (float) cell.optDouble("titleSize", 0);
                if (titleSize > 0) views.setTextViewTextSize(titleId, android.util.TypedValue.COMPLEX_UNIT_SP, titleSize);
            } else if (titleId != 0) { views.setViewVisibility(titleId, View.GONE); }

            // Value
            String value = cell.optString("value", "");
            int valueId = cellId(row, col, "value");
            if (!value.isEmpty() && valueId != 0) {
                views.setTextViewText(valueId, value);
                views.setViewVisibility(valueId, View.VISIBLE);
                String valueColor = cell.optString("valueColor", "");
                if (!valueColor.isEmpty()) { try { views.setTextColor(valueId, Color.parseColor(valueColor)); } catch (Exception ignored) {} }
                float valueSize = (float) cell.optDouble("valueSize", 0);
                if (valueSize > 0) views.setTextViewTextSize(valueId, android.util.TypedValue.COMPLEX_UNIT_SP, valueSize);
            } else if (valueId != 0) { views.setViewVisibility(valueId, View.GONE); }

            // Progress
            int progressId = cellId(row, col, "progress");
            if (cell.has("progress") && progressId != 0) {
                views.setProgressBar(progressId, 100, (int) (cell.getDouble("progress") * 100), false);
                views.setViewVisibility(progressId, View.VISIBLE);
            } else if (progressId != 0) { views.setViewVisibility(progressId, View.GONE); }

            // CheckBox
            int cbId = cellId(row, col, "checkbox");
            if (cell.has("checkbox") && cbId != 0) {
                JSONObject cb = cell.getJSONObject("checkbox");
                views.setTextViewText(cbId, cb.optString("label", ""));
                if (android.os.Build.VERSION.SDK_INT >= 31) views.setCompoundButtonChecked(cbId, cb.optBoolean("checked", false));
                else views.setBoolean(cbId, "setChecked", cb.optBoolean("checked", false));
                views.setViewVisibility(cbId, View.VISIBLE);
                String action = cb.optString("action", "");
                if (!action.isEmpty()) setActionIntent(context, views, cbId, action, !cb.optBoolean("checked", false), widgetId);
            } else if (cbId != 0) { views.setViewVisibility(cbId, View.GONE); }

            // Toggle/Switch
            int togId = cellId(row, col, "toggle");
            if (cell.has("toggle") && togId != 0) {
                JSONObject tog = cell.getJSONObject("toggle");
                views.setTextViewText(togId, tog.optString("label", ""));
                if (android.os.Build.VERSION.SDK_INT >= 31) views.setCompoundButtonChecked(togId, tog.optBoolean("checked", false));
                else views.setBoolean(togId, "setChecked", tog.optBoolean("checked", false));
                views.setViewVisibility(togId, View.VISIBLE);
                String action = tog.optString("action", "");
                if (!action.isEmpty()) setActionIntent(context, views, togId, action, !tog.optBoolean("checked", false), widgetId);
            } else if (togId != 0) { views.setViewVisibility(togId, View.GONE); }

            // Button
            String button = cell.optString("button", "");
            int btnId = cellId(row, col, "button");
            if (!button.isEmpty() && btnId != 0) {
                views.setTextViewText(btnId, button);
                views.setViewVisibility(btnId, View.VISIBLE);
                String action = cell.optString("action", "");
                if (!action.isEmpty()) setActionIntent(context, views, btnId, action, false, widgetId);
            } else if (btnId != 0) { views.setViewVisibility(btnId, View.GONE); }

        } catch (Exception e) {
            android.util.Log.e("iappyxOS", "Widget cell error: " + e.getMessage());
        }
    }

    private void setActionIntent(Context context, RemoteViews views, int viewId, String action, boolean checked, int widgetId) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context.getPackageName(),
            "com.iappyx.generated.placeholder.ShellActivity"));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("widget_action", action);
        intent.putExtra("widget_checked", checked);
        // Unique action per state so PendingIntent is always distinct
        intent.setAction(ACTION_WIDGET_TAP + "_" + action + "_" + checked);
        PendingIntent pi = PendingIntent.getActivity(context, viewId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setOnClickPendingIntent(viewId, pi);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("com.iappyx.WIDGET_UPDATE".equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, WidgetProvider.class));
            if (ids.length > 0) onUpdate(context, manager, ids);
        }
    }
}
