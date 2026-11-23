package com.gathin.tvhome.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Loads the banner slides from banner.json.
 *
 * Where it is read from (first one that exists and is valid wins):
 *   1. Override file  : Android/data/com.gathin.tvhome/files/banner.json  (adb push, no rebuild)
 *   2. Bundled file   : app/src/main/assets/banner.json
 * If the active file has a "remote" URL, the launcher downloads that JSON on open (and at most
 * every 30 minutes after, or on demand from Settings > Refresh Banner), caches it, and switches the banner to it. The cached copy is shown
 * instantly on the next start, so the banner works offline too.
 *
 * Remote images are downloaded in the background and cached in files/hero_cache/.
 */
final class BannerConfig {

    private static final String TAG = "BannerConfig";
    static final String FILE = "banner.json";
    private static final String CACHE_JSON = "banner_remote.json";
    private static final String CACHE_URL = "banner_remote.url";
    private static final String IMG_CACHE = "hero_cache";
    private static final int MAX_JSON = 512 * 1024;
    private static final int MAX_IMG = 10 * 1024 * 1024;
    private static final long IMG_TTL_MS = 30L * 24 * 60 * 60 * 1000;
    private static final String DOT = "  \u2022  ";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Object IMG_LOCK = new Object();

    final List<HeroItem> items;
    final String remoteUrl;
    final String raw;
    final String source;     // override | asset | cache | remote | builtin
    long overrideStamp;      // mtime of the override file when loaded (0 = none)

    private BannerConfig(List<HeroItem> items, String remoteUrl, String raw, String source) {
        this.items = items;
        this.remoteUrl = remoteUrl;
        this.raw = raw;
        this.source = source;
    }

    // ------------------------------------------------------------------ loading

    /** Local sources only (fast, safe on the main thread). Never returns null or an empty list. */
    static BannerConfig loadLocal(Context ctx) {
        long stamp = overrideStamp(ctx);
        BannerConfig base = null;
        File ext = ctx.getExternalFilesDir(null);
        if (ext != null) {
            base = parse(readFile(new File(ext, FILE)), "override");
        }
        if (base == null) {
            base = parse(readAsset(ctx, FILE), "asset");
        }
        if (base == null) {
            base = builtin();
        }
        // Use the cached remote copy if it came from the same "remote" URL.
        if (base.remoteUrl != null) {
            String cachedUrl = readFile(new File(ctx.getFilesDir(), CACHE_URL));
            if (base.remoteUrl.equals(cachedUrl != null ? cachedUrl.trim() : null)) {
                BannerConfig cached = parse(readFile(new File(ctx.getFilesDir(), CACHE_JSON)), "cache");
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

    /** Downloads, validates and caches the remote JSON. Background thread only. Null on failure. */
    static BannerConfig fetchRemote(Context ctx, String url) {
        try {
            String sep = url.indexOf('?') >= 0 ? "&" : "?";
            byte[] body = httpGet(url + sep + "_ts=" + System.currentTimeMillis(), MAX_JSON);
            String raw = new String(body, UTF8);
            BannerConfig c = parse(raw, "remote");
            if (c == null) {
                Log.w(TAG, "Remote banner.json is invalid, keeping the current banner");
                return null;
            }
            writeFile(new File(ctx.getFilesDir(), CACHE_JSON), raw);
            writeFile(new File(ctx.getFilesDir(), CACHE_URL), url);
            return c.withRemote(url);
        } catch (Exception e) {
            Log.w(TAG, "Remote banner.json fetch failed: " + e);
            return null;
        }
    }

    static long overrideStamp(Context ctx) {
        File ext = ctx.getExternalFilesDir(null);
        File f = ext != null ? new File(ext, FILE) : null;
        return f != null && f.isFile() ? f.lastModified() : 0L;
    }

    private BannerConfig withRemote(String url) {
        BannerConfig c = new BannerConfig(items, url, raw, source);
        c.overrideStamp = overrideStamp;
        return c;
    }

    private static BannerConfig builtin() {
        List<HeroItem> one = new ArrayList<>();
        one.add(new HeroItem("live", "Live Channels", "Live TV", "res:hero_live", 0xFF0A6FE8,
                new BannerAction(BannerAction.APP, null, "com.google.android.tv", null, null, null,
                        null, null, false, null, null), null));
        return new BannerConfig(one, null, "", "builtin");
    }

    // ------------------------------------------------------------------ parsing

    /** Accepts {"remote": "...", "slides": [...]} or a bare [...] array of slides. */
    static BannerConfig parse(String raw, String source) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            String t = raw.trim();
            JSONArray slides;
            String remote = null;
            if (t.startsWith("[")) {
                slides = new JSONArray(t);
            } else {
                JSONObject root = new JSONObject(t);
                slides = root.optJSONArray("slides");
                remote = str(root, "remote");
                if (remote != null && !remote.startsWith("http")) {
                    remote = null;
                }
            }
            if (slides == null) {
                return null;
            }
            List<HeroItem> out = new ArrayList<>();
            for (int i = 0; i < slides.length(); i++) {
                JSONObject s = slides.optJSONObject(i);
                if (s == null || !s.optBoolean("enabled", true)) {
                    continue;
                }
                String title = str(s, "title");
                if (title == null) {
                    Log.w(TAG, "Slide " + i + " has no title, skipped");
                    continue;
                }
                String id = str(s, "id");
                if (id == null) {
                    id = "slide" + i;
                }
                String meta = str(s, "meta");
                JSONArray tags = s.optJSONArray("tags");
                if (meta == null && tags != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < tags.length(); k++) {
                        String tag = tags.optString(k, "").trim();
                        if (tag.isEmpty()) {
                            continue;
                        }
                        if (sb.length() > 0) {
                            sb.append(DOT);
                        }
                        sb.append(tag);
                    }
                    meta = sb.toString();
                }
                Object sec = s.has("secondary") ? s.opt("secondary") : s.opt("open");
                out.add(new HeroItem(id, title, meta != null ? meta : "", str(s, "image"),
                        parseColor(str(s, "color")), action(s.opt("play")), action(sec)));
            }
            if (out.isEmpty()) {
                return null;
            }
            return new BannerConfig(Collections.unmodifiableList(out), remote, raw, source);
        } catch (Exception e) {
            Log.w(TAG, "banner.json (" + source + ") could not be parsed: " + e.getMessage());
            return null;
        }
    }

    /** Action from an object, or a shorthand string: a URL (link) or a number (channel). */
    private static BannerAction action(Object o) {
        if (o == null || o == JSONObject.NULL) {
            return null;
        }
        if (o instanceof String) {
            String v = ((String) o).trim();
            if (v.isEmpty()) {
                return null;
            }
            if (v.matches("\\d+([.-]\\d+)?")) {
                return new BannerAction(BannerAction.CHANNEL, null, null, null, null, v,
                        null, null, false, null, null);
            }
            return new BannerAction(BannerAction.LINK, v, null, null, null, null,
                    null, null, true, null, null);
        }
        if (!(o instanceof JSONObject)) {
            return null;
        }
        JSONObject a = (JSONObject) o;
        String uri = str(a, "uri");
        String pkg = str(a, "package");
        String number = str(a, "number");
        String act = str(a, "action");
        String type = str(a, "type");
        if (type == null) {
            if (number != null) type = BannerAction.CHANNEL;
            else if (act != null) type = BannerAction.INTENT;
            else if (uri != null) type = BannerAction.LINK;
            else if (pkg != null) type = BannerAction.APP;
            else return null;
        }
        Map<String, String> extras = null;
        JSONObject ex = a.optJSONObject("extras");
        if (ex != null) {
            extras = new HashMap<>();
            Iterator<String> keys = ex.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                extras.put(k, ex.optString(k, ""));
            }
        }
        return new BannerAction(type.toLowerCase(), uri, pkg, str(a, "class"), act, number,
                str(a, "label"), str(a, "icon"), a.optBoolean("anyApp", false), extras,
                action(a.opt("fallback")));
    }

    private static String str(JSONObject o, String key) {
        Object v = o.opt(key);
        if (v == null || v == JSONObject.NULL) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static int parseColor(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ images

    static boolean isRemote(String img) {
        return img != null && (img.startsWith("https://") || img.startsWith("http://"));
    }

    /** Decodes a slide image from local sources only (no network). Null if not available yet. */
    static Bitmap decodeImage(Context ctx, String img, int maxW) {
        if (img == null || img.isEmpty()) {
            return null;
        }
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            if (img.startsWith("res:")) {
                int id = ctx.getResources().getIdentifier(img.substring(4).trim(), "drawable",
                        ctx.getPackageName());
                return id != 0 ? BitmapFactory.decodeResource(ctx.getResources(), id, o) : null;
            }
            if (img.startsWith("asset:")) {
                try (InputStream in = ctx.getAssets().open(img.substring(6).trim())) {
                    return BitmapFactory.decodeStream(in, null, o);
                }
            }
            File f;
            if (isRemote(img)) {
                f = cacheFile(ctx, img);
            } else if (img.startsWith("file:")) {
                f = new File(Uri.parse(img).getPath());
            } else if (img.startsWith("/")) {
                f = new File(img);
            } else {
                File dir = ctx.getExternalFilesDir("hero");
                f = dir != null ? new File(dir, img) : null;
            }
            return f != null && f.isFile() ? decodeFile(f, maxW) : null;
        } catch (Throwable t) {
            Log.w(TAG, "Image " + img + " could not be decoded: " + t);
            return null;
        }
    }

    /** Downloads any remote slide images that aren't cached yet. Background thread only. */
    static void prefetchImages(Context ctx, HeroItem[] items) {
        synchronized (IMG_LOCK) {
            for (HeroItem it : items) {
                if (!isRemote(it.image)) {
                    continue;
                }
                File f = cacheFile(ctx, it.image);
                if (f.isFile() && f.length() > 0) {
                    f.setLastModified(System.currentTimeMillis()); // keep it out of the prune
                    continue;
                }
                File tmp = new File(f.getPath() + ".part");
                try {
                    download(it.image, tmp, MAX_IMG);
                    if (!tmp.renameTo(f)) {
                        tmp.delete();
                    }
                } catch (Exception e) {
                    tmp.delete();
                    Log.w(TAG, "Image download failed " + it.image + ": " + e);
                }
            }
            // Drop images no slide has used for 30 days.
            File[] all = new File(ctx.getFilesDir(), IMG_CACHE).listFiles();
            long cutoff = System.currentTimeMillis() - IMG_TTL_MS;
            if (all != null) {
                for (File f : all) {
                    if (f.lastModified() < cutoff) {
                        f.delete();
                    }
                }
            }
        }
    }

    private static File cacheFile(Context ctx, String url) {
        File dir = new File(ctx.getFilesDir(), IMG_CACHE);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return new File(dir, sha1(url) + ".img");
    }

    private static Bitmap decodeFile(File f, int maxW) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), b);
        int sample = 1;
        while (maxW > 0 && b.outWidth / (sample * 2) >= maxW) {
            sample *= 2;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    // ------------------------------------------------------------------ io

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "TvHome-Banner/1.0");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IOException("HTTP " + code);
        }
        return c;
    }

    private static byte[] httpGet(String url, int max) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out, max);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File dest, int max) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
            copy(in, out, max);
        } finally {
            c.disconnect();
        }
    }

    private static void copy(InputStream in, OutputStream out, int max) throws IOException {
        byte[] buf = new byte[16 * 1024];
        int total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) {
                throw new IOException("Too large (> " + max + " bytes)");
            }
            out.write(buf, 0, n);
        }
    }

    private static String readFile(File f) {
        if (f == null || !f.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out, MAX_JSON);
            return new String(out.toByteArray(), UTF8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readAsset(Context ctx, String name) {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out, MAX_JSON);
            return new String(out.toByteArray(), UTF8);
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeFile(File f, String s) {
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(s.getBytes(UTF8));
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            tmp.delete();
        }
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(UTF8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
