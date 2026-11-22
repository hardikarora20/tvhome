package com.gathin.tvhome.home;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

/**
 * TV brightness. Order: vendor backlight key (WRITE_SECURE_SETTINGS) -> TV-wide dim overlay
 * ("Display over other apps") -> home-screen-only dim layer.
 */
final class Brightness {

    private static final String KEY = "brightness";
    private static final float MAX_DIM = 0.78f;

    private static final String[] VENDOR_KEYS = {
            "picture_backlight", "tv_picture_backlight", "backlight", "tv_backlight",
            "mtk_backlight", "rtk_backlight", "picture_backlight_value", "backlight_value",
            "tcl_backlight", "mitv_backlight", "hisense_backlight"
    };

    private final Context app;
    private final SharedPreferences prefs;
    private final View inAppDim;
    private View overlay;
    private String vendorKey;
    private int vendorTable = -1;

    Brightness(Context ctx, SharedPreferences prefs, View inAppDim) {
        this.app = ctx.getApplicationContext();
        this.prefs = prefs;
        this.inAppDim = inAppDim;
        probeVendorKey();
    }

    int get() {
        return prefs.getInt(KEY, 100);
    }

    boolean hasHardwareControl() {
        return vendorKey != null && canWriteSecure();
    }

    boolean canOverlay() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(app);
    }

    int set(int value) {
        int v = Math.max(10, Math.min(100, value));
        prefs.edit().putInt(KEY, v).apply();
        apply(v);
        return v;
    }

    void reapply() {
        apply(get());
    }

    private void apply(int v) {
        boolean hardware = false;
        if (hasHardwareControl()) {
            hardware = putInt(vendorTable, vendorKey, v);
        }
        if (Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(app)) {
            try {
                ContentResolver cr = app.getContentResolver();
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, Math.round(v * 255f / 100f));
            } catch (Exception ignored) {
                // some TV builds reject this
            }
        }
        float dim = hardware ? 0f : (100 - v) / 90f * MAX_DIM;
        if (!hardware && canOverlay()) {
            showOverlay(dim);
            setInApp(0f);
        } else {
            removeOverlayIfIdle(dim);
            setInApp(hardware ? 0f : dim);
        }
    }

    private void setInApp(float dim) {
        if (inAppDim != null) {
            inAppDim.animate().alpha(dim).setDuration(120).start();
        }
    }

    private void showOverlay(float dim) {
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return;
        }
        if (dim <= 0.001f) {
            removeOverlayIfIdle(0f);
            return;
        }
        if (overlay == null) {
            overlay = new View(app);
            overlay.setBackgroundColor(Color.BLACK);
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.alpha = dim;
            try {
                wm.addView(overlay, lp);
            } catch (Exception e) {
                overlay = null;
                setInApp(dim);
                return;
            }
        }
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) overlay.getLayoutParams();
        lp.alpha = dim;
        try {
            wm.updateViewLayout(overlay, lp);
        } catch (Exception ignored) {
            // window gone
        }
    }

    private void removeOverlayIfIdle(float dim) {
        if (overlay != null && dim <= 0.001f) {
            WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            try {
                if (wm != null) {
                    wm.removeView(overlay);
                }
            } catch (Exception ignored) {
                // already removed
            }
            overlay = null;
        }
    }

    private boolean canWriteSecure() {
        return app.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void probeVendorKey() {
        ContentResolver cr = app.getContentResolver();
        for (String k : VENDOR_KEYS) {
            try {
                if (Settings.Global.getString(cr, k) != null) { vendorKey = k; vendorTable = 0; return; }
                if (Settings.System.getString(cr, k) != null) { vendorKey = k; vendorTable = 1; return; }
                if (Settings.Secure.getString(cr, k) != null) { vendorKey = k; vendorTable = 2; return; }
            } catch (Exception ignored) {
                // restricted key
            }
        }
    }

    private boolean putInt(int table, String key, int v) {
        try {
            ContentResolver cr = app.getContentResolver();
            if (table == 0) return Settings.Global.putInt(cr, key, v);
            if (table == 1) return Settings.System.putInt(cr, key, v);
            return Settings.Secure.putInt(cr, key, v);
        } catch (Exception e) {
            return false;
        }
    }
}
