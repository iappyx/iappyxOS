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

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for iappyx.trigger.* registrations.
 *
 * Each trigger is stored as one JSON blob under key "trig_<id>". JSON shape:
 *   {
 *     "id": "...",
 *     "type": "charger" | "headphones" | "bluetooth" | "wifi",
 *     "event": "connected" | "disconnected" | "plugged" | "unplugged" | "any",
 *     "match": "...",          // optional: SSID for wifi, address for bluetooth
 *     "callbackFn": "...",
 *     "lastFiredMs": 0,
 *     "debounceMs": 30000
 *   }
 */
public final class TriggerStore {
    private static final String PREFS = "iappyx_triggers";
    private static final String KEY_PREFIX = "trig_";
    public static final long DEFAULT_DEBOUNCE_MS = 30_000L;

    private TriggerStore() {}

    public static void put(Context ctx, JSONObject trigger) {
        try {
            String id = trigger.getString("id");
            prefs(ctx).edit().putString(KEY_PREFIX + id, trigger.toString()).apply();
        } catch (JSONException ignored) {}
    }

    public static JSONObject get(Context ctx, String id) {
        String s = prefs(ctx).getString(KEY_PREFIX + id, null);
        if (s == null) return null;
        try { return new JSONObject(s); } catch (JSONException e) { return null; }
    }

    public static void remove(Context ctx, String id) {
        prefs(ctx).edit().remove(KEY_PREFIX + id).apply();
    }

    public static void clear(Context ctx) {
        SharedPreferences p = prefs(ctx);
        SharedPreferences.Editor e = p.edit();
        for (String k : p.getAll().keySet()) if (k.startsWith(KEY_PREFIX)) e.remove(k);
        e.apply();
    }

    public static List<JSONObject> all(Context ctx) {
        List<JSONObject> out = new ArrayList<>();
        for (java.util.Map.Entry<String, ?> e : prefs(ctx).getAll().entrySet()) {
            if (!e.getKey().startsWith(KEY_PREFIX)) continue;
            try { out.add(new JSONObject(e.getValue().toString())); } catch (JSONException ignored) {}
        }
        return out;
    }

    public static List<JSONObject> byType(Context ctx, String type) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject t : all(ctx)) if (type.equals(t.optString("type"))) out.add(t);
        return out;
    }

    public static JSONArray toJsonArray(List<JSONObject> list) {
        JSONArray arr = new JSONArray();
        for (JSONObject t : list) arr.put(t);
        return arr;
    }

    public static boolean hasAnyPersistent(Context ctx) {
        for (JSONObject t : all(ctx)) {
            if (t.optBoolean("persistent", false)) return true;
        }
        return false;
    }

    public static int countPersistent(Context ctx) {
        int n = 0;
        for (JSONObject t : all(ctx)) if (t.optBoolean("persistent", false)) n++;
        return n;
    }

    public static void updateLastFired(Context ctx, String id, long ts) {
        JSONObject t = get(ctx, id);
        if (t == null) return;
        try { t.put("lastFiredMs", ts); } catch (JSONException ignored) {}
        put(ctx, t);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
