package com.gathin.tvhome.home;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * Loads the background audio stream from play.json - same sources and rules as banner.json:
 *   1. Override file : Android/data/com.gathin.tvhome/files/play.json  (adb push, no rebuild)
 *   2. Bundled file  : app/src/main/assets/play.json
 *   3. Built-in default (AIR Live)
 * If the active file has a "remote" URL it is fetched on open, every 30 minutes and from
 * Settings > Refresh, then cached so it works offline.
 *
 * Format: { "remote": "https://...", "title": "AIR Live", "url": "http://.../chunklist.m3u8" }
 * ("streams": [ { "title": ..., "url": ..., "enabled": true } ] also works; the first enabled one is used.)
 */
final class StreamConfig {

    private static final String TAG = "StreamConfig";
    static final String FILE = "play.json";
    private static final String CACHE_JSON = "play_remote.json";
    private static final String CACHE_URL = "play_remote.url";
    private static final int MAX_JSON = 64 * 1024;
    static final String DEFAULT_TITLE = "AIR Live";
    static final String DEFAULT_URL = "http://air.pc.cdn.bitgravity.com/air/live/pbaudio001/chunklist.m3u8";

    final String title;
    final String url;
    final String remoteUrl;
    final String raw;
    long overrideStamp;

    private StreamConfig(String title, String url, String remoteUrl, String raw) {
        this.title = title;
        this.url = url;
        this.remoteUrl = remoteUrl;
        this.raw = raw;
    }

    /** Local sources only (safe on the main thread). Never returns null. */
    static StreamConfig loadLocal(Context ctx) {
        long stamp = overrideStamp(ctx);
        StreamConfig base = null;
        File ext = ctx.getExternalFilesDir(null);
        if (ext != null) {
            base = parse(BannerConfig.readFile(new File(ext, FILE)));
        }
        if (base == null) {
            base = parse(BannerConfig.readAsset(ctx, FILE));
        }
        if (base == null) {
            base = new StreamConfig(DEFAULT_TITLE, DEFAULT_URL, null, "");
        }
        if (base.remoteUrl != null) {
            String cachedUrl = BannerConfig.readFile(new File(ctx.getFilesDir(), CACHE_URL));
            if (base.remoteUrl.equals(cachedUrl != null ? cachedUrl.trim() : null)) {
                StreamConfig cached = parse(BannerConfig.readFile(new File(ctx.getFilesDir(), CACHE_JSON)));
                if (cached != null) {
                    cached = cached.withRemote(base.remoteUrl);
                    cached.overrideStamp = stamp;
                    return cached;
                }
            }
        }
        base.overrideStamp = stamp;
        return base;
    }

    /** Downloads, validates and caches the remote play.json. Background thread only. Null on failure. */
    static StreamConfig fetchRemote(Context ctx, String url) {
        try {
            String sep = url.indexOf('?') >= 0 ? "&" : "?";
            String raw = new String(BannerConfig.httpGet(url + sep + "_ts=" + System.currentTimeMillis(),
                    MAX_JSON), "UTF-8");
            StreamConfig c = parse(raw);
            if (c == null) {
                Log.w(TAG, "Remote play.json is invalid, keeping the current stream");
                return null;
            }
            BannerConfig.writeFile(new File(ctx.getFilesDir(), CACHE_JSON), raw);
            BannerConfig.writeFile(new File(ctx.getFilesDir(), CACHE_URL), url);
            return c.withRemote(url);
        } catch (Exception e) {
            Log.w(TAG, "Remote play.json fetch failed: " + e);
            return null;
        }
    }

    static long overrideStamp(Context ctx) {
        File ext = ctx.getExternalFilesDir(null);
        File f = ext != null ? new File(ext, FILE) : null;
        return f != null && f.isFile() ? f.lastModified() : 0L;
    }

    private StreamConfig withRemote(String remote) {
        StreamConfig c = new StreamConfig(title, url, remote, raw);
        c.overrideStamp = overrideStamp;
        return c;
    }

    static StreamConfig parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(raw.trim());
            String remote = str(root, "remote");
            if (remote != null && !remote.startsWith("http")) {
                remote = null;
            }
            String title = str(root, "title");
            String url = str(root, "url");
            if (url == null) {
                url = str(root, "stream");
            }
            JSONArray list = root.optJSONArray("streams");
            if (url == null && list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject s = list.optJSONObject(i);
                    if (s == null || !s.optBoolean("enabled", true) || str(s, "url") == null) {
                        continue;
                    }
                    url = str(s, "url");
                    if (str(s, "title") != null) {
                        title = str(s, "title");
                    }
                    break;
                }
            }
            if (url == null) {
                return null;
            }
            return new StreamConfig(title != null ? title : DEFAULT_TITLE, url, remote, raw);
        } catch (Exception e) {
            Log.w(TAG, "play.json could not be parsed: " + e.getMessage());
            return null;
        }
    }

    private static String str(JSONObject o, String key) {
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
