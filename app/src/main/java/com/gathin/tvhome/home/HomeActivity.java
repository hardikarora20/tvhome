package com.gathin.tvhome.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.tvhome.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Glassmorphic Android TV home screen (v3.6).
 *
 * Home layout, top to bottom: header -> hero banner (large) -> favourites dock pinned to the
 * bottom edge. All other apps live below the screen and only appear when you press Down
 * from the dock.
 */
public class HomeActivity extends Activity {

    // ------------------------------------------------------------------ config

    private static final String PREFS = "glass_home";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_WATCHLIST = "watchlist";
    private static final String KEY_FAVS = "favourites";
    private static final String KEY_DIM_HINT = "dim_hint_shown";
    private static final String KEY_TV_PERM_ASKED = "tv_perm_asked";
    private static final String DEFAULT_NAME = "Hardik";
    private static final long SLIDE_MS = 8000L;
    private static final long FADE_MS = 900L;
    private static final int MAX_FAVS = 5;
    private static final int STEP = 5;
    private static final int REQ_TV_PERM = 41;

    private static final String PERM_EPG = "com.android.providers.tv.permission.READ_EPG_DATA";
    private static final String PERM_LISTINGS = "android.permission.READ_TV_LISTINGS";

    private static final String PKG_LIVE = "com.google.android.tv";
    private static final String PKG_NETFLIX = "com.netflix.ninja";
    private static final String PKG_PRIME = "com.amazon.amazonvideo.livingroom";
    private static final String PKG_YOUTUBE = "com.google.android.youtube.tv";
    private static final String PKG_HOTSTAR = "in.startv.hotstar";
    private static final String[] DEFAULT_FAVS = {PKG_LIVE, PKG_NETFLIX, PKG_PRIME, PKG_YOUTUBE, PKG_HOTSTAR};

    private static final String DOT = "  \u2022  ";

    /** Banner order: Anupamaa, YRKKH, TGIKS, Aaj Tak, Sony, SYHP, YFT, Live Channels. */
    private final HeroItem[] items = {
            new HeroItem("anupamaa", "Anupamaa",
                    "JioHotstar" + DOT + "Family Drama" + DOT + "Hindi" + DOT + "New Episodes Daily",
                    PKG_HOTSTAR, "https://www.hotstar.com/in/shows/anupama/1260022017", null,
                    R.drawable.hero_anupamaa, true),
            new HeroItem("yrkkh", "Yeh Rishta Kya Kehlata Hai",
                    "JioHotstar" + DOT + "Romantic Family Drama" + DOT + "Hindi" + DOT + "New Episodes Daily",
                    PKG_HOTSTAR, "https://www.hotstar.com/in/shows/yeh-rishta/586", null,
                    R.drawable.hero_yrkkh, true),
            new HeroItem("kapil", "The Great Indian Kapil Show",
                    "Netflix" + DOT + "5 Seasons" + DOT + "Comedy Talk Show" + DOT + "Hindi",
                    PKG_NETFLIX, "https://www.netflix.com/title/81737430", null,
                    R.drawable.hero_kapil, true),
            new HeroItem("aajtak", "Aaj Tak",
                    "Live Channels" + DOT + "Channel 734" + DOT + "Hindi News" + DOT + "Live 24x7",
                    PKG_LIVE, null, "734",
                    R.drawable.hero_aajtak, false),
            new HeroItem("sony", "Sony TV",
                    "Live Channels" + DOT + "Channel 35" + DOT + "Hindi Entertainment" + DOT + "Live",
                    PKG_LIVE, null, "35",
                    R.drawable.hero_sony, false),
            new HeroItem("syhp", "Shayad Yahi Hai Pyaar",
                    "JioHotstar" + DOT + "Romance" + DOT + "Hindi" + DOT + "New Episodes",
                    PKG_HOTSTAR, "https://www.hotstar.com/in/shows/shayad-yahi-hai-pyaar/1271646563", null,
                    R.drawable.hero_syhp, true),
            new HeroItem("yft", "Yeh Fitoor Tera",
                    "JioHotstar" + DOT + "Romance Drama" + DOT + "Hindi" + DOT + "New Episodes",
                    PKG_HOTSTAR, "https://www.hotstar.com/in/shows/yeh-fitoor-tera/1271650854", null,
                    R.drawable.hero_yft, true),
            new HeroItem("live", "Live Channels",
                    "Live Channels App" + DOT + "Live TV" + DOT + "All your channels in one place",
                    PKG_LIVE, null, null,
                    R.drawable.hero_live, false),
    };

    // ------------------------------------------------------------------ motion

    private static final Interpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final Interpolator EASE_IN_OUT = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    // ------------------------------------------------------------------ state

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private AudioManager audio;
    private Brightness brightness;
    private final Backdrop backdrop = new Backdrop();
    private float density;
    private int screenW;
    private int screenH;
    private int margin;

    private FrameLayout root;
    private ImageView artA;
    private ImageView artB;
    private boolean bOnTop;
    private View pageDim;
    private final Bitmap[] artCache;
    private final Bitmap[] blurCache;

    private FrameLayout header;
    private TextView greeting;
    private TextView clockTime;
    private TextView clockAmPm;
    private TextView clockDate;
    private TextView tooltip;
    private FrameLayout btnInputs;
    private FrameLayout btnWifi;
    private FrameLayout btnSettings;
    private View vpnDot;

    private LinearLayout heroInfo;
    private LinearLayout heroText;
    private TextView heroTitle;
    private TextView heroMeta;
    private LinearLayout btnPlay;
    private LinearLayout btnWatch;
    private ImageView watchIcon;
    private TextView watchLabel;
    private IndicatorView indicator;
    private int heroTop;
    private int heroH;
    private int titleBoxH;
    private int titleMaxW;

    private LinearLayout lowerStack;
    private FrostedPanel dock;
    private LinearLayout dockRow;
    private LinearLayout rowsBox;
    private final List<TileView> dockTiles = new ArrayList<>();
    private final List<LinearLayout> appRows = new ArrayList<>();
    private int dockTop;
    private int tileW;
    private int tileH;
    private float tileRadius;
    private int dockPad;
    private int tileGap;
    private int tilePad;

    private Menu activeMenu;
    private Menu settingsMenu;
    private SliderView brightSlider;
    private SliderView volSlider;
    private TextView brightPct;
    private TextView volPct;
    private TextView nameValue;

    private int current;
    private ValueAnimator slideTimer;
    private ValueAnimator dimAnim;
    private int shiftedRow = -1;
    private boolean menuConfirmDown;
    private HeroItem pendingTune;
    private final Map<String, Uri> channelCache = new HashMap<>();
    private BroadcastReceiver packageReceiver;

    /** Frosted, focus-trapped popup. */
    private static final class Menu {
        FrostedPanel panel;
        LinearLayout list;
        final List<View> rows = new ArrayList<>();
        final Map<View, Adjust> adjust = new HashMap<>();
        View returnFocus;
        boolean dynamic;
    }

    private interface Adjust {
        void by(int delta);
    }

    public HomeActivity() {
        artCache = new Bitmap[items.length];
        blurCache = new Bitmap[items.length];
    }

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        density = dm.density;
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        margin = Math.round(screenW * 0.0625f);
        computeGeometry();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        setContentView(root);
        hideSystemBars();

        buildHeroLayer();
        buildHeroInfo();
        buildLowerStack();
        buildHeader();
        buildTooltip();

        View inAppDim = new View(this);
        inAppDim.setBackgroundColor(Color.BLACK);
        inAppDim.setAlpha(0f);
        inAppDim.setFocusable(false);
        root.addView(inAppDim, new FrameLayout.LayoutParams(-1, -1));
        brightness = new Brightness(this, prefs, inAppDim);
        brightness.reapply();

        showSlide(0, false, 1);
        preloadSlides();
        rebuildApps(null);
        handler.post(() -> {
            if (!dockTiles.isEmpty()) {
                dockTiles.get(0).requestFocus();
            } else {
                btnPlay.requestFocus();
            }
        });
        registerPackageReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        refreshVolume();
        if (activeMenu == null && shiftedRow < 0) {
            resumeTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        pauseTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (slideTimer != null) {
            slideTimer.removeAllListeners();
            slideTimer.cancel();
        }
        if (packageReceiver != null) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (Exception ignored) {
                // not registered
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onBackPressed() {
        // The home screen never finishes. Back is handled in dispatchKeyEvent.
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ================================================================== geometry

    private void computeGeometry() {
        int contentW = screenW - 2 * margin;
        dockPad = dp(15);
        tileGap = dp(26);
        tilePad = dp(9);
        tileRadius = dp(12);
        tileW = (contentW - 2 * dockPad - 4 * tileGap) / 5;
        tileH = Math.round(tileW * 0.56f);

        // Dock pinned to the bottom edge; equal breathing room under it as at the sides.
        int dockH = tileH + 2 * dockPad;
        int bottomGap = Math.max(dp(26), Math.round(margin * 0.5f));
        dockTop = screenH - bottomGap - dockH;

        // Title: at most ~38% of the banner width, up to 2 lines, in a box that never resizes.
        titleMaxW = Math.round(screenW * 0.38f);
        titleBoxH = dp(104);
        heroH = titleBoxH + dp(10) + dp(20) + dp(22) + dp(42);
        heroTop = dockTop - dp(34) - heroH;
    }

    // ================================================================== hero art

    private void buildHeroLayer() {
        FrameLayout layer = new FrameLayout(this);
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));

        artA = newArt();
        artB = newArt();
        artB.setAlpha(0f);
        layer.addView(artA, new FrameLayout.LayoutParams(-1, -1));
        layer.addView(artB, new FrameLayout.LayoutParams(-1, -1));

        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB3000000, 0x4D000000, 0x00000000}));
        layer.addView(top, new FrameLayout.LayoutParams(-1, Math.round(screenH * 0.28f), Gravity.TOP));

        View left = new View(this);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xA6000000, 0x40000000, 0x00000000}));
        layer.addView(left, new FrameLayout.LayoutParams(Math.round(screenW * 0.58f), -1, Gravity.START));

        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x99000000, 0x26000000, 0x00000000}));
        layer.addView(bottom, new FrameLayout.LayoutParams(-1, Math.round(screenH * 0.45f), Gravity.BOTTOM));

        pageDim = new View(this);
        pageDim.setBackgroundColor(Color.BLACK);
        pageDim.setAlpha(0f);
        layer.addView(pageDim, new FrameLayout.LayoutParams(-1, -1));
    }

    private ImageView newArt() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setFocusable(false);
        return iv;
    }

    /**
     * Artwork for slide i. A file named {@code <id>.jpg/.png/.webp} in
     * Android/data/com.gathin.tvhome/files/hero/ overrides the bundled image.
     */
    private Bitmap loadArt(int i) {
        File dir = getExternalFilesDir("hero");
        if (dir != null) {
            for (String ext : new String[]{".jpg", ".jpeg", ".png", ".webp"}) {
                File f = new File(dir, items[i].id + ext);
                if (f.isFile()) {
                    Bitmap b = decodeFile(f);
                    if (b != null) {
                        return b;
                    }
                }
            }
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeResource(getResources(), items[i].artRes, o);
    }

    private Bitmap decodeFile(File f) {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), b);
        int sample = 1;
        while (b.outWidth / (sample * 2) >= screenW) {
            sample *= 2;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private Bitmap makeBlur(Bitmap art) {
        int w = Math.max(16, screenW / 10);
        int h = Math.max(9, screenH / 10);
        float scale = Math.max(w / (float) art.getWidth(), h / (float) art.getHeight());
        int cw = Math.round(w / scale);
        int ch = Math.round(h / scale);
        int cx = Math.max(0, (art.getWidth() - cw) / 2);
        int cy = Math.max(0, (art.getHeight() - ch) / 2);
        Bitmap crop = Bitmap.createBitmap(art, cx, cy, Math.min(cw, art.getWidth()), Math.min(ch, art.getHeight()));
        Bitmap small = Bitmap.createScaledBitmap(crop, w, h, true).copy(Bitmap.Config.ARGB_8888, false);
        return Backdrop.blur(small, 3);
    }

    private synchronized Bitmap art(int i) {
        if (artCache[i] == null) {
            artCache[i] = loadArt(i);
        }
        return artCache[i];
    }

    private synchronized Bitmap blurred(int i) {
        if (blurCache[i] == null) {
            Bitmap a = art(i);
            if (a != null) {
                blurCache[i] = makeBlur(a);
            }
        }
        return blurCache[i];
    }

    private void preloadSlides() {
        Thread t = new Thread(() -> {
            for (int i = 0; i < items.length; i++) {
                blurred(i);
            }
        }, "hero-preload");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // ================================================================== hero info

    private void buildHeroInfo() {
        heroInfo = new LinearLayout(this);
        heroInfo.setOrientation(LinearLayout.VERTICAL);
        heroInfo.setClipChildren(false);
        heroInfo.setClipToPadding(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(screenW * 0.56f), heroH,
                Gravity.TOP | Gravity.START);
        lp.leftMargin = margin;
        lp.topMargin = heroTop;
        root.addView(heroInfo, lp);

        heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroInfo.addView(heroText, new LinearLayout.LayoutParams(-1, titleBoxH + dp(10) + dp(20)));

        // Same typeface for every title: heavy sans, upper-case, bottom-aligned in a fixed box.
        heroTitle = new TextView(this);
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        heroTitle.setLetterSpacing(0.015f);
        heroTitle.setIncludeFontPadding(false);
        heroTitle.setGravity(Gravity.BOTTOM | Gravity.START);
        heroTitle.setShadowLayer(18 * density, 0, 2 * density, 0x8C000000);
        heroTitle.setLineSpacing(0f, 0.9f);
        heroText.addView(heroTitle, new LinearLayout.LayoutParams(titleMaxW + dp(8), titleBoxH));

        heroMeta = new TextView(this);
        heroMeta.setTextColor(0xD9FFFFFF);
        heroMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        heroMeta.setSingleLine(true);
        heroMeta.setEllipsize(TextUtils.TruncateAt.END);
        heroMeta.setShadowLayer(6 * density, 0, density, 0x99000000);
        heroMeta.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, dp(20));
        mlp.topMargin = dp(10);
        heroText.addView(heroMeta, mlp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, dp(42));
        blp.topMargin = dp(22);
        heroInfo.addView(buttons, blp);

        btnPlay = pill();
        final ImageView playIcon = icon(R.drawable.ic_h_play, 20);
        playIcon.setColorFilter(0xFF111111, PorterDuff.Mode.SRC_IN);
        btnPlay.addView(playIcon);
        btnPlay.addView(pillText("Play", 0xFF111111));
        btnPlay.setBackground(pillBg(0xEBFFFFFF, 0));
        buttons.addView(btnPlay, new LinearLayout.LayoutParams(-2, -1));
        btnPlay.setOnClickListener(v -> launchHero(items[current]));
        btnPlay.setOnFocusChangeListener((v, f) -> {
            v.setBackground(pillBg(f ? 0xFFFFFFFF : 0xEBFFFFFF, 0));
            focusScale(v, f, 1.08f);
        });

        btnWatch = pill();
        watchIcon = icon(R.drawable.ic_h_check, 18);
        btnWatch.addView(watchIcon);
        watchLabel = pillText("Watchlist", Color.WHITE);
        btnWatch.addView(watchLabel);
        btnWatch.setBackground(pillBg(0x4D1A1A1E, 0x40FFFFFF));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -1);
        wlp.leftMargin = dp(12);
        buttons.addView(btnWatch, wlp);
        btnWatch.setOnClickListener(v -> toggleWatchlist(items[current]));
        btnWatch.setOnFocusChangeListener((v, f) -> {
            v.setBackground(f ? pillBg(0xFFFFFFFF, 0) : pillBg(0x4D1A1A1E, 0x40FFFFFF));
            int ink = f ? 0xFF111111 : Color.WHITE;
            watchIcon.setColorFilter(ink, PorterDuff.Mode.SRC_IN);
            watchLabel.setTextColor(ink);
            focusScale(v, f, 1.08f);
        });

        indicator = new IndicatorView(this);
        indicator.setCount(items.length);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        ilp.rightMargin = margin;
        ilp.topMargin = heroTop + heroH - dp(42) + (dp(42) - dp(30)) / 2;
        root.addView(indicator, ilp);
    }

    private void showSlide(int index, boolean animate, final int direction) {
        current = (index + items.length) % items.length;
        final HeroItem item = items[current];
        Bitmap bmp = art(current);

        // artB sits above artA; z-order never changes, so nothing re-lays out mid-animation.
        final ImageView incoming;
        artB.animate().cancel();
        if (!animate) {
            artA.setImageBitmap(bmp);
            artB.setAlpha(0f);
            bOnTop = false;
            incoming = artA;
        } else if (!bOnTop) {
            artB.setImageBitmap(bmp);
            artB.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(EASE_IN_OUT).withLayer().start();
            bOnTop = true;
            incoming = artB;
        } else {
            artA.setImageBitmap(bmp);
            artB.animate().alpha(0f).setDuration(FADE_MS).setInterpolator(EASE_IN_OUT).withLayer().start();
            bOnTop = false;
            incoming = artA;
        }
        kenBurns(incoming);

        backdrop.show(blurred(current), animate ? FADE_MS : 0);

        if (animate) {
            final float shift = 28 * density * direction;
            heroText.animate().cancel();
            heroText.animate().alpha(0f).translationX(-shift * 0.6f).setDuration(200)
                    .setInterpolator(EASE_IN_OUT).withLayer()
                    .withEndAction(() -> {
                        fillHero(item);
                        heroText.setTranslationX(shift);
                        heroText.animate().alpha(1f).translationX(0f).setDuration(520)
                                .setInterpolator(EASE).withLayer().start();
                    }).start();
        } else {
            fillHero(item);
        }
        updateWatchVisibility(item, animate);
        indicator.setActive(current);
        restartTimer();
    }

    /** Slow, linear Ken Burns settle, independent of the cross-fade. */
    private void kenBurns(ImageView v) {
        Object old = v.getTag();
        if (old instanceof ObjectAnimator) {
            ((ObjectAnimator) old).cancel();
        }
        ObjectAnimator kb = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.045f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.045f, 1f));
        kb.setDuration(SLIDE_MS + FADE_MS);
        kb.setInterpolator(new LinearInterpolator());
        kb.start();
        v.setTag(kb);
    }

    private void fillHero(HeroItem item) {
        heroMeta.setText(item.meta);
        fitTitle(item.title);
        if (item.watchlist) {
            updateWatchButton();
        }
    }

    /**
     * Title fitting (box width = ~38% of the screen, box height fixed):
     *  - short titles stay on one line at up to 56sp (never below 40sp on one line);
     *  - longer titles break into two balanced lines at a word boundary, e.g.
     *    "THE GREAT INDIAN / KAPIL SHOW", sized so the wider line fits (44sp down to 26sp).
     */
    private void fitTitle(String title) {
        String t = title.toUpperCase(Locale.getDefault()).trim();
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        Paint p = new Paint(heroTitle.getPaint());

        p.setTextSize(40f * scaled);
        boolean oneLine = p.measureText(t) <= titleMaxW || !t.contains(" ");
        String text;
        float maxSp;
        float minSp;
        if (oneLine) {
            text = t;
            maxSp = 56f;
            minSp = 26f;
        } else {
            text = balancedBreak(t, p);
            maxSp = 44f;
            minSp = 26f;
        }
        float sp = maxSp;
        while (sp > minSp) {
            p.setTextSize(sp * scaled);
            if (widestLine(text, p) <= titleMaxW) {
                break;
            }
            sp -= 1f;
        }
        heroTitle.setMaxLines(oneLine ? 1 : 2);
        heroTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        heroTitle.setText(text);
    }

    /** Word break that minimises the wider of the two lines; ties favour a longer first line. */
    private static String balancedBreak(String t, Paint p) {
        String[] words = t.split("\\s+");
        String best = t;
        float bestW = Float.MAX_VALUE;
        for (int i = 1; i < words.length; i++) {
            String a = TextUtils.join(" ", Arrays.copyOfRange(words, 0, i));
            String b = TextUtils.join(" ", Arrays.copyOfRange(words, i, words.length));
            float wa = p.measureText(a);
            float w = Math.max(wa, p.measureText(b));
            if (w < bestW - 0.5f || (Math.abs(w - bestW) <= 0.5f && wa >= w)) {
                bestW = w;
                best = a + "\n" + b;
            }
        }
        return best;
    }

    private static float widestLine(String text, Paint p) {
        float w = 0f;
        for (String line : text.split("\n")) {
            w = Math.max(w, p.measureText(line));
        }
        return w;
    }

    private void updateWatchVisibility(HeroItem item, boolean animate) {
        if (item.watchlist) {
            if (btnWatch.getVisibility() != View.VISIBLE) {
                btnWatch.setVisibility(View.VISIBLE);
                btnWatch.setAlpha(0f);
                btnWatch.animate().alpha(1f).setDuration(animate ? 360 : 0).start();
            }
        } else {
            if (btnWatch.hasFocus()) {
                btnPlay.requestFocus();
            }
            btnWatch.setVisibility(View.GONE);
        }
    }

    // ================================================================== auto-advance

    private void restartTimer() {
        if (slideTimer != null) {
            slideTimer.removeAllListeners();
            slideTimer.cancel();
        }
        slideTimer = ValueAnimator.ofFloat(0f, 1f);
        slideTimer.setDuration(SLIDE_MS);
        slideTimer.setInterpolator(new LinearInterpolator());
        slideTimer.addUpdateListener(a -> indicator.setProgress((Float) a.getAnimatedValue()));
        slideTimer.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    showSlide(current + 1, true, 1);
                }
            }
        });
        slideTimer.start();
        if (activeMenu != null || shiftedRow >= 0) {
            slideTimer.pause();
        }
    }

    private void pauseTimer() {
        if (slideTimer != null && slideTimer.isRunning()) {
            slideTimer.pause();
        }
    }

    private void resumeTimer() {
        if (slideTimer != null && slideTimer.isPaused()) {
            slideTimer.resume();
        }
    }

    // ================================================================== dock + rows

    private void buildLowerStack() {
        lowerStack = new LinearLayout(this) {
            @Override
            protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            }
        };
        lowerStack.setOrientation(LinearLayout.VERTICAL);
        lowerStack.setClipChildren(false);
        lowerStack.setClipToPadding(false);
        lowerStack.setPadding(margin, 0, margin, 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        lp.topMargin = dockTop;
        root.addView(lowerStack, lp);

        dock = new FrostedPanel(this);
        dock.attach(backdrop, screenW, screenH);
        dock.setCornerRadius(tileRadius + dockPad); // concentric: outer = inner + padding
        dock.setTint(0x59141418);
        dock.setRim(0x2EFFFFFF, Math.max(1f, density));
        int inner = dockPad - tilePad;
        dock.setPadding(inner, inner, inner, inner);
        lowerStack.addView(dock, new LinearLayout.LayoutParams(-1, tileH + 2 * dockPad));

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dock.addView(dockRow, new FrameLayout.LayoutParams(-1, -1));

        // All other apps: below the screen edge and invisible until you scroll down.
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setClipChildren(false);
        rowsBox.setClipToPadding(false);
        rowsBox.setAlpha(0f);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Math.max(dp(40), screenH - dockTop - (tileH + 2 * dockPad));
        lowerStack.addView(rowsBox, rlp);
    }

    private List<String> favourites() {
        String raw = prefs.getString(KEY_FAVS, null);
        List<String> out = new ArrayList<>();
        if (raw == null) {
            out.addAll(Arrays.asList(DEFAULT_FAVS));
        } else if (!raw.isEmpty()) {
            out.addAll(Arrays.asList(raw.split(",")));
        }
        return out;
    }

    private void saveFavourites(List<String> favs) {
        prefs.edit().putString(KEY_FAVS, TextUtils.join(",", favs)).apply();
    }

    private void rebuildApps(String focusPkg) {
        PackageManager pm = getPackageManager();
        dockRow.removeAllViews();
        dockTiles.clear();
        rowsBox.removeAllViews();
        appRows.clear();

        List<String> favs = favourites();
        int slot = 0;
        for (String pkg : favs) {
            if (slot >= MAX_FAVS) {
                break;
            }
            ComponentName cn = launchComponent(pm, pkg);
            TileView t = makeTile(pkg, cn, tileContent(pm, pkg, cn));
            dockRow.addView(t, tileParams(slot));
            dockTiles.add(t);
            slot++;
        }
        for (; slot < MAX_FAVS; slot++) {
            FrameLayout empty = new FrameLayout(this);
            View ghost = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(tileRadius);
            g.setColor(0x0DFFFFFF);
            g.setStroke(Math.max(1, dp(1)), 0x1AFFFFFF);
            ghost.setBackground(g);
            FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-1, -1);
            glp.setMargins(tilePad, tilePad, tilePad, tilePad);
            empty.addView(ghost, glp);
            dockRow.addView(empty, tileParams(slot));
        }

        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        Set<String> skip = new HashSet<>(favs);
        skip.add(getPackageName());
        final List<ResolveInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            String p = ri.activityInfo.packageName;
            if (!skip.contains(p) && seen.add(p)) {
                apps.add(ri);
            }
        }
        Collections.sort(apps, (a, b) -> String.valueOf(a.loadLabel(pm))
                .compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));

        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                row.setPadding(dockPad - tilePad, 0, dockPad - tilePad, 0);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, tileH + 2 * tilePad);
                rlp.topMargin = appRows.isEmpty() ? 0 : tileGap - 2 * tilePad;
                rowsBox.addView(row, rlp);
                appRows.add(row);
            }
            ActivityInfo ai = apps.get(i).activityInfo;
            ComponentName cn = new ComponentName(ai.packageName, ai.name);
            row.addView(makeTile(ai.packageName, cn, tileContent(pm, ai.packageName, cn)), tileParams(i % 5));
        }
        View spacer = new View(this);
        rowsBox.addView(spacer, new LinearLayout.LayoutParams(1, Math.round(screenH * 0.5f)));

        if (focusPkg != null) {
            handler.post(() -> {
                View t = findTile(focusPkg);
                if (t != null) {
                    t.requestFocus();
                    syncScrollToFocus();
                } else if (!dockTiles.isEmpty()) {
                    dockTiles.get(0).requestFocus();
                    scrollToRow(-1);
                }
            });
        }
    }

    private LinearLayout.LayoutParams tileParams(int col) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW + 2 * tilePad, tileH + 2 * tilePad);
        if (col > 0) {
            lp.leftMargin = tileGap - 2 * tilePad;
        }
        return lp;
    }

    private TileView makeTile(final String pkg, final ComponentName cn, View content) {
        final TileView t = new TileView(this, pkg, content, tileRadius, tilePad);
        t.setOnClickListener(v -> {
            if (cn != null) {
                Intent i = new Intent(Intent.ACTION_MAIN).setComponent(cn)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (tryStart(i)) {
                    return;
                }
            }
            launchApp(pkg);
        });
        t.setOnLongClickListener(v -> {
            openAppMenu(t);
            return true;
        });
        return t;
    }

    private TileView findTile(String pkg) {
        for (TileView t : dockTiles) {
            if (t.pkg.equals(pkg)) {
                return t;
            }
        }
        for (LinearLayout r : appRows) {
            for (int i = 0; i < r.getChildCount(); i++) {
                View c = r.getChildAt(i);
                if (c instanceof TileView && ((TileView) c).pkg.equals(pkg)) {
                    return (TileView) c;
                }
            }
        }
        return null;
    }

    private ComponentName launchComponent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) {
            i = pm.getLaunchIntentForPackage(pkg);
        }
        return i != null ? i.getComponent() : null;
    }

    private View tileContent(PackageManager pm, String pkg, ComponentName cn) {
        View styled = styledTile(pkg);
        if (styled != null) {
            return styled;
        }
        Drawable banner = null;
        try {
            if (cn != null) {
                banner = pm.getActivityBanner(cn);
            }
            if (banner == null) {
                banner = pm.getApplicationBanner(pkg);
            }
        } catch (Exception ignored) {
            // not installed
        }
        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            return iv;
        }
        Drawable icon = null;
        String label = pkg;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            icon = pm.getApplicationIcon(ai);
            label = String.valueOf(pm.getApplicationLabel(ai));
        } catch (Exception ignored) {
            // not installed
        }
        return iconTile(icon, label);
    }

    private View iconTile(Drawable icon, String label) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF3A3A42, 0xFF1C1C22}));
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            int s = Math.round(tileH * 0.42f);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
            ilp.bottomMargin = Math.round(tileH * 0.12f);
            f.addView(iv, ilp);
        }
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        tlp.bottomMargin = dp(7);
        tlp.leftMargin = dp(8);
        tlp.rightMargin = dp(8);
        f.addView(t, tlp);
        return f;
    }

    private View styledTile(String pkg) {
        float big = tileH * 0.25f / getResources().getDisplayMetrics().scaledDensity;
        FrameLayout f = new FrameLayout(this);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER);
        f.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        TextView t = new TextView(this);
        t.setIncludeFontPadding(false);
        switch (pkg) {
            case PKG_LIVE: {
                f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF35AFFF, 0xFF0A6FE8}));
                ImageView iv = new ImageView(this);
                iv.setImageResource(R.drawable.ic_h_tv);
                int s = Math.round(tileH * 0.40f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
                lp.rightMargin = dp(10);
                inner.addView(iv, lp);
                t.setText("Live\nChannels");
                t.setTextColor(Color.WHITE);
                t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big * 0.62f);
                break;
            }
            case PKG_NETFLIX:
                f.setBackgroundColor(0xFFFAFAFA);
                t.setText("NETFLIX");
                t.setTextColor(0xFFE50914);
                t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big);
                t.setScaleY(1.25f);
                break;
            case PKG_PRIME: {
                f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF22B2F5, 0xFF0A6AAE}));
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER);
                t.setText("prime video");
                t.setTextColor(Color.WHITE);
                t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big * 0.82f);
                col.addView(t);
                View smile = new View(this);
                GradientDrawable sm = new GradientDrawable();
                sm.setColor(Color.WHITE);
                sm.setCornerRadius(dp(2));
                smile.setBackground(sm);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Math.round(tileW * 0.34f), dp(3));
                slp.topMargin = dp(3);
                col.addView(smile, slp);
                inner.addView(col);
                return f;
            }
            case PKG_YOUTUBE: {
                f.setBackgroundColor(0xFFF7F7F7);
                FrameLayout logo = new FrameLayout(this);
                GradientDrawable red = new GradientDrawable();
                red.setColor(0xFFFF0000);
                red.setCornerRadius(tileH * 0.08f);
                logo.setBackground(red);
                ImageView play = new ImageView(this);
                play.setImageResource(R.drawable.ic_h_play);
                logo.addView(play, new FrameLayout.LayoutParams(Math.round(tileH * 0.2f),
                        Math.round(tileH * 0.2f), Gravity.CENTER));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(tileH * 0.36f),
                        Math.round(tileH * 0.26f));
                lp.rightMargin = dp(4);
                inner.addView(logo, lp);
                t.setText("YouTube");
                t.setTextColor(0xFF0F0F0F);
                t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big * 0.86f);
                break;
            }
            case PKG_HOTSTAR:
                f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF0B3BFF, 0xFF7A2BF5, 0xFFE02AB4}));
                t.setText("\u2726 JioHotstar");
                t.setTextColor(Color.WHITE);
                t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big * 0.72f);
                break;
            default:
                return null;
        }
        inner.addView(t);
        return f;
    }

    private void registerPackageReceiver() {
        packageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                handler.removeCallbacks(rebuildRunnable);
                handler.postDelayed(rebuildRunnable, 400);
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);
    }

    private final Runnable rebuildRunnable = () -> {
        if (activeMenu != null) {
            closeMenu(false);
        }
        View f = getCurrentFocus();
        String keep = f instanceof TileView ? ((TileView) f).pkg : null;
        rebuildApps(keep != null ? keep : (dockTiles.isEmpty() ? null : dockTiles.get(0).pkg));
    };

    // ================================================================== header

    private void buildHeader() {
        header = new FrameLayout(this);
        header.setClipChildren(false);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, dp(44), Gravity.TOP);
        hlp.topMargin = dp(34);
        root.addView(header, hlp);

        greeting = new TextView(this);
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        greeting.setSingleLine(true);
        greeting.setGravity(Gravity.CENTER_VERTICAL);
        greeting.setShadowLayer(10 * density, 0, density, 0x80000000);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-2, -1, Gravity.START);
        glp.leftMargin = margin;
        header.addView(greeting, glp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-2, -1, Gravity.END);
        rlp.rightMargin = margin;
        header.addView(right, rlp);

        btnInputs = circleButton(R.drawable.ic_h_input, 40, 20, "Inputs");
        right.addView(btnInputs);

        btnWifi = circleButton(R.drawable.ic_h_wifi, 40, 20, "Wi-Fi");
        vpnDot = new View(this);
        GradientDrawable green = new GradientDrawable();
        green.setShape(GradientDrawable.OVAL);
        green.setColor(0xFF34C759);
        green.setStroke(Math.max(1, dp(1)), 0xFF101012);
        vpnDot.setBackground(green);
        vpnDot.setVisibility(View.GONE);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(9), dp(9), Gravity.BOTTOM | Gravity.END);
        dlp.rightMargin = dp(7);
        dlp.bottomMargin = dp(7);
        btnWifi.addView(vpnDot, dlp);
        ((LinearLayout.LayoutParams) btnWifi.getLayoutParams()).leftMargin = dp(16);
        right.addView(btnWifi);

        btnSettings = circleButton(R.drawable.ic_h_settings, 40, 22, "Settings");
        ((LinearLayout.LayoutParams) btnSettings.getLayoutParams()).leftMargin = dp(16);
        right.addView(btnSettings);

        View divider = new View(this);
        divider.setBackgroundColor(0x4DFFFFFF);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(36));
        vlp.leftMargin = dp(20);
        vlp.rightMargin = dp(20);
        right.addView(divider, vlp);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        right.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout timeLine = new LinearLayout(this);
        timeLine.setOrientation(LinearLayout.HORIZONTAL);
        timeLine.setGravity(Gravity.BOTTOM);
        clock.addView(timeLine);
        clockTime = new TextView(this);
        clockTime.setTextColor(Color.WHITE);
        clockTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        clockTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clockTime.setIncludeFontPadding(false);
        clockTime.setShadowLayer(8 * density, 0, density, 0x80000000);
        timeLine.addView(clockTime);
        clockAmPm = new TextView(this);
        clockAmPm.setTextColor(Color.WHITE);
        clockAmPm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        clockAmPm.setIncludeFontPadding(false);
        clockAmPm.setPadding(dp(4), 0, 0, dp(2));
        timeLine.addView(clockAmPm);
        clockDate = new TextView(this);
        clockDate.setTextColor(0xCCFFFFFF);
        clockDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        clockDate.setLetterSpacing(0.06f);
        clockDate.setPadding(0, dp(3), 0, 0);
        clock.addView(clockDate);

        btnInputs.setOnClickListener(v -> openInputsMenu());
        btnWifi.setOnClickListener(v -> {
            if (!tryStart(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                openSystemSettings();
            }
        });
        btnSettings.setOnClickListener(v -> openSettingsMenu());
        updateGreeting();
    }

    private void buildTooltip() {
        tooltip = new TextView(this);
        tooltip.setTextColor(Color.WHITE);
        tooltip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tooltip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tooltip.setPadding(dp(10), dp(4), dp(10), dp(5));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xD9141418);
        g.setCornerRadius(dp(10));
        g.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        tooltip.setBackground(g);
        tooltip.setAlpha(0f);
        root.addView(tooltip, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
    }

    private FrameLayout circleButton(int iconRes, int sizeDp, int iconDp, String label) {
        final FrameLayout b = new FrameLayout(this);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        b.setFocusable(true);
        b.setClickable(true);
        b.setClipChildren(false);
        b.setTag(label);
        b.setContentDescription(label);
        b.setBackground(circleBg(false));
        final ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        b.addView(iv, new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        b.setOnFocusChangeListener((v, f) -> {
            b.setBackground(circleBg(f));
            if (f) {
                iv.setColorFilter(0xFF111111, PorterDuff.Mode.SRC_IN);
            } else {
                iv.clearColorFilter();
            }
            focusScale(b, f, 1.12f);
            showTooltip(b, f);
        });
        return b;
    }

    private GradientDrawable circleBg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(focused ? 0xF2FFFFFF : 0x40303036);
        g.setStroke(Math.max(1, dp(1)), focused ? 0xFFFFFFFF : 0x40FFFFFF);
        return g;
    }

    private void showTooltip(View b, boolean focused) {
        if (!focused || activeMenu != null) {
            tooltip.animate().alpha(0f).setDuration(120).start();
            return;
        }
        tooltip.setText((String) b.getTag());
        tooltip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] a = new int[2];
        b.getLocationInWindow(a);
        float cx = a[0] + b.getWidth() / 2f;
        tooltip.setTranslationX(cx - tooltip.getMeasuredWidth() / 2f);
        tooltip.setTranslationY(dp(34) + dp(44) + dp(10));
        tooltip.animate().alpha(1f).setDuration(180).start();
    }

    // ================================================================== menus (generic)

    private Menu newMenu(int widthDp) {
        Menu m = new Menu();
        m.panel = new FrostedPanel(this);
        m.panel.attach(backdrop, screenW, screenH);
        m.panel.setCornerRadius(dp(22));
        m.panel.setTint(0xBF18181B);
        m.panel.setRim(0x33FFFFFF, Math.max(1f, density));
        m.panel.setElevation(dp(20));
        m.panel.setFocusable(false);
        m.list = new LinearLayout(this);
        m.list.setOrientation(LinearLayout.VERTICAL);
        m.list.setPadding(dp(8), dp(8), dp(8), dp(8));
        m.panel.addView(m.list, new FrameLayout.LayoutParams(-1, -2));
        m.panel.setLayoutParams(new FrameLayout.LayoutParams(dp(widthDp), -2, Gravity.TOP | Gravity.START));
        return m;
    }

    private LinearLayout menuRow(Menu m, int heightDp) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(rowBg(false));
        row.setOnFocusChangeListener((v, f) -> row.setBackground(rowBg(f)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(heightDp));
        lp.topMargin = m.rows.isEmpty() ? 0 : dp(4);
        m.list.addView(row, lp);
        m.rows.add(row);
        return row;
    }

    private void menuHeader(Menu m, String text) {
        TextView t = new TextView(this);
        t.setText(text.toUpperCase(Locale.getDefault()));
        t.setTextColor(0x99FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setLetterSpacing(0.1f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(14), dp(6), dp(14), dp(8));
        m.list.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void menuDivider(Menu m) {
        View div = new View(this);
        div.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
        lp.setMargins(dp(6), dp(6), dp(6), dp(2));
        m.list.addView(div, lp);
    }

    private LinearLayout labelRow(Menu m, int iconRes, String label, String sub, int trailingRes) {
        LinearLayout row = menuRow(m, sub != null ? 54 : 46);
        row.addView(icon(iconRes, 22));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, dp(8), 0);
        col.addView(smallText(label, Color.WHITE, 15));
        if (sub != null) {
            col.addView(smallText(sub, 0x99FFFFFF, 11));
        }
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (trailingRes != 0) {
            ImageView tr = icon(trailingRes, 18);
            tr.setAlpha(0.8f);
            row.addView(tr);
        }
        return row;
    }

    private TextView smallText(String s, int color, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private GradientDrawable rowBg(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(focused ? 0x33FFFFFF : 0x0AFFFFFF);
        if (focused) {
            g.setStroke(Math.max(1, dp(1)), 0x40FFFFFF);
        }
        return g;
    }

    private static final int BELOW_RIGHT_EDGE = 0;
    private static final int BELOW_ANCHOR = 1;
    private static final int ABOVE_ANCHOR = 2;

    private void openMenu(final Menu m, View anchor, int placement) {
        if (activeMenu != null) {
            closeMenu(false);
        }
        activeMenu = m;
        menuConfirmDown = false;
        m.returnFocus = getCurrentFocus();
        if (m.returnFocus != null) {
            m.returnFocus.setPressed(false);
        }
        if (m.panel.getParent() == null) {
            root.addView(m.panel, m.panel.getLayoutParams());
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) m.panel.getLayoutParams();
        m.panel.measure(View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = lp.width;
        int h = m.panel.getMeasuredHeight();
        int[] a = new int[2];
        anchor.getLocationInWindow(a);
        float ax = a[0] + anchor.getWidth() / 2f;
        int x;
        int y;
        if (placement == BELOW_RIGHT_EDGE) {
            x = screenW - margin - w;
            y = dp(34) + dp(44) + dp(14);
        } else if (placement == BELOW_ANCHOR) {
            x = Math.round(ax - w / 2f);
            y = dp(34) + dp(44) + dp(14);
        } else {
            x = Math.round(ax - w / 2f);
            y = a[1] - h - dp(10);
            if (y < dp(24)) {
                y = a[1] + anchor.getHeight() + dp(10);
            }
        }
        x = Math.max(margin, Math.min(screenW - margin - w, x));
        lp.leftMargin = x;
        lp.topMargin = Math.max(dp(16), y);
        m.panel.setLayoutParams(lp);

        m.panel.setVisibility(View.VISIBLE);
        m.panel.setAlpha(0f);
        m.panel.setScaleX(0.96f);
        m.panel.setScaleY(0.96f);
        m.panel.setPivotX(placement == BELOW_RIGHT_EDGE ? w : w / 2f);
        m.panel.setPivotY(placement == ABOVE_ANCHOR ? h : 0);
        m.panel.setTranslationY(placement == ABOVE_ANCHOR ? dp(8) : -dp(8));
        m.panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(260)
                .setInterpolator(EASE).setUpdateListener(an -> m.panel.invalidate()).start();

        tooltip.animate().alpha(0f).setDuration(100).start();
        pauseTimer();
        lowerStack.animate().alpha(0.42f).setDuration(240).setInterpolator(EASE).start();
        if (!m.rows.isEmpty()) {
            m.rows.get(0).requestFocus();
        }
    }

    private void closeMenu(boolean restoreFocus) {
        final Menu m = activeMenu;
        if (m == null) {
            return;
        }
        activeMenu = null;
        m.panel.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(170).setInterpolator(EASE_IN_OUT)
                .setUpdateListener(null)
                .withEndAction(() -> {
                    if (activeMenu != m) {
                        if (m.dynamic) {
                            root.removeView(m.panel);
                        } else {
                            m.panel.setVisibility(View.INVISIBLE);
                        }
                    }
                }).start();
        lowerStack.animate().alpha(1f).setDuration(220).setInterpolator(EASE).start();
        if (restoreFocus && m.returnFocus != null && m.returnFocus.isAttachedToWindow()) {
            m.returnFocus.requestFocus();
        }
        if (shiftedRow < 0) {
            resumeTimer();
        }
    }

    // ================================================================== settings menu

    private void openSettingsMenu() {
        if (settingsMenu == null) {
            settingsMenu = buildSettingsMenu();
        }
        refreshVolume();
        int b = brightness.get();
        brightSlider.setValue(b);
        brightPct.setText(b + "%");
        nameValue.setText(userName());
        openMenu(settingsMenu, btnSettings, BELOW_RIGHT_EDGE);
    }

    private Menu buildSettingsMenu() {
        Menu m = newMenu(276);

        brightSlider = new SliderView(this);
        brightPct = valueText();
        View rowB = sliderRow(m, R.drawable.ic_h_brightness, brightSlider, brightPct);
        m.adjust.put(rowB, delta -> {
            int v = brightness.set(brightness.get() + delta);
            brightSlider.setValue(v);
            brightPct.setText(v + "%");
            maybeShowDimHint();
        });

        volSlider = new SliderView(this);
        volPct = valueText();
        View rowV = sliderRow(m, R.drawable.ic_h_volume, volSlider, volPct);
        m.adjust.put(rowV, this::changeVolume);

        LinearLayout nameRow = menuRow(m, 54);
        nameRow.addView(icon(R.drawable.ic_h_person, 22));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        col.addView(smallText("User Name", 0x99FFFFFF, 11));
        nameValue = smallText(userName(), Color.WHITE, 15);
        col.addView(nameValue);
        nameRow.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView pencil = icon(R.drawable.ic_h_edit, 18);
        pencil.setAlpha(0.8f);
        nameRow.addView(pencil);
        nameRow.setOnClickListener(v -> editUserName());

        menuDivider(m);
        LinearLayout sys = labelRow(m, R.drawable.ic_h_settings, "System Settings", null,
                R.drawable.ic_h_chevron_right);
        sys.setOnClickListener(v -> {
            closeMenu(true);
            openSystemSettings();
        });
        return m;
    }

    private TextView valueText() {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        t.setMinWidth(dp(42));
        return t;
    }

    private View sliderRow(Menu m, int iconRes, SliderView slider, TextView pct) {
        LinearLayout row = menuRow(m, 46);
        row.addView(icon(iconRes, 22));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(24), 1f);
        slp.leftMargin = dp(12);
        slp.rightMargin = dp(10);
        row.addView(slider, slp);
        row.addView(pct, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private void maybeShowDimHint() {
        if (brightness.hasHardwareControl() || brightness.canOverlay() || prefs.getBoolean(KEY_DIM_HINT, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_DIM_HINT, true).apply();
        Toast.makeText(this, "Dimming the home screen only. Allow \"Display over other apps\" "
                + "for TV-wide brightness.", Toast.LENGTH_LONG).show();
    }

    private void editUserName() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setText(userName());
        input.setSelection(input.getText().length());
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(24), dp(8), dp(24), 0);
        wrap.addView(input);
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("User Name")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> saveUserName(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .create();
        input.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveUserName(input.getText().toString());
                dlg.dismiss();
                return true;
            }
            return false;
        });
        dlg.setOnDismissListener(d -> {
            hideSystemBars();
            if (activeMenu == settingsMenu && settingsMenu != null) {
                settingsMenu.rows.get(2).requestFocus();
            }
        });
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        input.requestFocus();
    }

    private void saveUserName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            return;
        }
        prefs.edit().putString(KEY_NAME, name).apply();
        updateGreeting();
    }

    private String userName() {
        return prefs.getString(KEY_NAME, DEFAULT_NAME);
    }

    // ================================================================== inputs menu

    private void openInputsMenu() {
        Menu m = newMenu(300);
        m.dynamic = true;
        menuHeader(m, "Inputs");
        List<TvSources.Source> sources = TvSources.list(this);
        for (final TvSources.Source s : sources) {
            int ic = s.isTuner() ? R.drawable.ic_h_antenna
                    : (s.isHdmi() ? R.drawable.ic_h_hdmi : R.drawable.ic_h_av);
            LinearLayout row = labelRow(m, ic, s.label, s.status(), 0);
            if (s.connected()) {
                View dot = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(0xFF34C759);
                dot.setBackground(g);
                row.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
            }
            row.setOnClickListener(v -> {
                closeMenu(true);
                if (!TvSources.open(this, s)) {
                    Toast.makeText(this, "Couldn't switch to " + s.label, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (sources.isEmpty()) {
            LinearLayout live = labelRow(m, R.drawable.ic_h_antenna, "Live TV", "Open the TV tuner app", 0);
            live.setOnClickListener(v -> {
                closeMenu(true);
                launchApp(PKG_LIVE);
            });
            LinearLayout panel = labelRow(m, R.drawable.ic_h_input, "Source menu",
                    "This TV doesn't list its inputs to apps", 0);
            panel.setOnClickListener(v -> {
                closeMenu(true);
                if (!tryStart(new Intent("com.android.tv.action.VIEW_INPUTS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                    openSystemSettings();
                }
            });
        }
        openMenu(m, btnInputs, BELOW_ANCHOR);
    }

    // ================================================================== app long-press menu

    private void openAppMenu(final TileView tile) {
        final String pkg = tile.pkg;
        PackageManager pm = getPackageManager();
        String label = pkg;
        boolean installed = false;
        boolean system = false;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = String.valueOf(pm.getApplicationLabel(ai));
            installed = true;
            system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (PackageManager.NameNotFoundException ignored) {
            // favourite that isn't installed
        }
        final boolean isFav = favourites().contains(pkg);

        Menu m = newMenu(256);
        m.dynamic = true;
        menuHeader(m, label);
        LinearLayout fav = labelRow(m, isFav ? R.drawable.ic_h_star : R.drawable.ic_h_star_outline,
                isFav ? "Remove from Favourites" : "Add to Favourites", null, 0);
        fav.setOnClickListener(v -> {
            closeMenu(false);
            List<String> f = favourites();
            if (isFav) {
                f.remove(pkg);
            } else {
                if (f.size() >= MAX_FAVS) {
                    Toast.makeText(this, "Dock is full. Remove a favourite first.", Toast.LENGTH_SHORT).show();
                    tile.requestFocus();
                    return;
                }
                f.add(pkg);
            }
            saveFavourites(f);
            rebuildApps(pkg);
        });
        if (installed) {
            LinearLayout info = labelRow(m, R.drawable.ic_h_info, "App info", null, 0);
            info.setOnClickListener(v -> {
                closeMenu(true);
                tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
            if (!system) {
                LinearLayout un = labelRow(m, R.drawable.ic_h_delete, "Uninstall", null, 0);
                ((ImageView) un.getChildAt(0)).setColorFilter(0xFFFF6B6B, PorterDuff.Mode.SRC_IN);
                un.setOnClickListener(v -> {
                    closeMenu(true);
                    Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (!tryStart(i)) {
                        tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                });
            }
        }
        openMenu(m, tile, ABOVE_ANCHOR);
    }

    // ================================================================== volume

    private void refreshVolume() {
        if (audio == null || volSlider == null) {
            return;
        }
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int v = Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
        volSlider.setValue(v);
        volPct.setText(v + "%");
    }

    private void changeVolume(int delta) {
        if (audio == null) {
            return;
        }
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int target = Math.max(0, Math.min(100, volSlider.getValue() + delta));
        int index = Math.round(target * max / 100f);
        if (index == audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            index = Math.max(0, Math.min(max, index + (delta > 0 ? 1 : -1)));
        }
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
        } catch (SecurityException ignored) {
            // DND policy
        }
        refreshVolume();
    }

    // ================================================================== watchlist

    private Set<String> watchlist() {
        return new HashSet<>(prefs.getStringSet(KEY_WATCHLIST, new HashSet<>()));
    }

    private void toggleWatchlist(HeroItem item) {
        Set<String> set = watchlist();
        if (!set.remove(item.id)) {
            set.add(item.id);
        }
        prefs.edit().putStringSet(KEY_WATCHLIST, set).apply();
        updateWatchButton();
        watchIcon.setScaleX(0.6f);
        watchIcon.setScaleY(0.6f);
        watchIcon.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(EASE).start();
        restartTimer();
    }

    private void updateWatchButton() {
        boolean saved = watchlist().contains(items[current].id);
        watchIcon.setImageResource(saved ? R.drawable.ic_h_check : R.drawable.ic_h_add);
        btnWatch.setContentDescription(saved ? "Remove from Watchlist" : "Add to Watchlist");
    }

    // ================================================================== clock + greeting

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateGreeting();
            vpnDot.setVisibility(vpnActive() ? View.VISIBLE : View.GONE);
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 1000 - (now % 1000) + 5);
        }
    };

    private void updateClock() {
        Calendar c = Calendar.getInstance();
        boolean is24 = DateFormat.is24HourFormat(this);
        clockTime.setText(DateFormat.format(is24 ? "H:mm" : "h:mm", c));
        clockAmPm.setText(is24 ? "" : DateFormat.format("a", c).toString().toUpperCase(Locale.getDefault()));
        clockAmPm.setVisibility(is24 ? View.GONE : View.VISIBLE);
        clockDate.setText(DateFormat.format("EEE, MMM d", c).toString().toUpperCase(Locale.getDefault()));
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String prefix = hour >= 5 && hour < 12 ? "Good Morning, "
                : (hour >= 12 && hour < 17 ? "Good Afternoon, " : "Good Evening, ");
        String name = userName();
        SpannableStringBuilder sb = new SpannableStringBuilder(prefix);
        sb.setSpan(new ForegroundColorSpan(0xBFFFFFFF), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new TypefaceSpan("sans-serif-light"), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int start = sb.length();
        sb.append(name);
        sb.setSpan(new ForegroundColorSpan(Color.WHITE), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new TypefaceSpan("sans-serif-medium"), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (greeting != null && !TextUtils.equals(greeting.getText(), sb)) {
            greeting.setText(sb);
        }
        if (nameValue != null) {
            nameValue.setText(name);
        }
    }

    private boolean vpnActive() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // restricted
        }
        return false;
    }

    // ================================================================== D-pad

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            boolean r = super.dispatchKeyEvent(event);
            handler.postDelayed(this::refreshVolume, 120);
            return r;
        }
        if (activeMenu != null) {
            return handleMenuKey(code, down, event);
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (down && event.getRepeatCount() == 0) {
                if (!dockTiles.isEmpty()) {
                    dockTiles.get(0).requestFocus();
                } else {
                    btnPlay.requestFocus();
                }
                scrollToRow(-1);
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (down && event.getRepeatCount() == 0 && getCurrentFocus() instanceof TileView) {
                openAppMenu((TileView) getCurrentFocus());
            }
            return true;
        }
        if (down) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    return moveFocus(-1, 0);
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return moveFocus(1, 0);
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return moveFocus(0, -1);
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return moveFocus(0, 1);
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleMenuKey(int code, boolean down, KeyEvent event) {
        Menu m = activeMenu;
        View focused = getCurrentFocus();
        int idx = m.rows.indexOf(focused);
        if (idx < 0 && !m.rows.isEmpty()) {
            m.rows.get(0).requestFocus();
            idx = 0;
        }
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_MENU:
                if (down && event.getRepeatCount() == 0) {
                    closeMenu(true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && idx > 0) {
                    m.rows.get(idx - 1).requestFocus();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && idx < m.rows.size() - 1) {
                    m.rows.get(idx + 1).requestFocus();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    Adjust a = m.adjust.get(focused);
                    if (a != null) {
                        a.by(code == KeyEvent.KEYCODE_DPAD_RIGHT ? STEP : -STEP);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // Only act on a press that started inside the menu (ignores the key-up that
                // ends a long-press on a tile).
                if (down) {
                    if (event.getRepeatCount() == 0) {
                        menuConfirmDown = true;
                    }
                } else if (menuConfirmDown) {
                    menuConfirmDown = false;
                    if (focused != null && !m.adjust.containsKey(focused)) {
                        focused.performClick();
                    }
                }
                return true;
            case KeyEvent.KEYCODE_HOME:
                return super.dispatchKeyEvent(event);
            default:
                return true;
        }
    }

    /**
     * Rows: 0 header icons, 1 hero (Play, Watchlist), 2 dock, 3+ app rows.
     * In the hero row, Left past Play = previous slide, Right past the last button = next slide.
     */
    private boolean moveFocus(int dRow, int dCol) {
        List<List<View>> rows = navRows();
        View focused = getCurrentFocus();
        int r = -1;
        int c = -1;
        for (int i = 0; i < rows.size() && r < 0; i++) {
            int j = rows.get(i).indexOf(focused);
            if (j >= 0) {
                r = i;
                c = j;
            }
        }
        if (r < 0) {
            (dockTiles.isEmpty() ? btnPlay : dockTiles.get(0)).requestFocus();
            return true;
        }
        if (dCol != 0) {
            int nc = c + dCol;
            List<View> row = rows.get(r);
            if (nc >= 0 && nc < row.size()) {
                row.get(nc).requestFocus();
            } else if (r == 1) {
                showSlide(current + dCol, true, dCol);
            }
            return true;
        }
        int nr = r + dRow;
        while (nr >= 0 && nr < rows.size() && rows.get(nr).isEmpty()) {
            nr += dRow;
        }
        if (nr < 0 || nr >= rows.size()) {
            return true;
        }
        View target = nr == 1 ? btnPlay : nearestByX(rows.get(nr), focused);
        target.requestFocus();
        scrollToRow(nr >= 3 ? nr - 3 : -1);
        return true;
    }

    private List<List<View>> navRows() {
        List<List<View>> rows = new ArrayList<>();
        rows.add(Arrays.<View>asList(btnInputs, btnWifi, btnSettings));
        List<View> hero = new ArrayList<>();
        hero.add(btnPlay);
        if (btnWatch.getVisibility() == View.VISIBLE) {
            hero.add(btnWatch);
        }
        rows.add(hero);
        rows.add(new ArrayList<View>(dockTiles));
        for (LinearLayout row : appRows) {
            List<View> list = new ArrayList<>();
            for (int i = 0; i < row.getChildCount(); i++) {
                list.add(row.getChildAt(i));
            }
            rows.add(list);
        }
        return rows;
    }

    private View nearestByX(List<View> candidates, View from) {
        int[] loc = new int[2];
        from.getLocationOnScreen(loc);
        float fx = loc[0] + from.getWidth() / 2f;
        View best = candidates.get(0);
        float bestD = Float.MAX_VALUE;
        for (View v : candidates) {
            v.getLocationOnScreen(loc);
            float d = Math.abs(loc[0] + v.getWidth() / 2f - fx);
            if (d < bestD) {
                bestD = d;
                best = v;
            }
        }
        return best;
    }

    private void syncScrollToFocus() {
        View f = getCurrentFocus();
        for (int i = 0; i < appRows.size(); i++) {
            if (f != null && f.getParent() == appRows.get(i)) {
                scrollToRow(i);
                return;
            }
        }
        scrollToRow(-1);
    }

    /**
     * Home (-1): header + banner visible, dock at the bottom edge, app rows hidden below.
     * App row k: the stack glides up so row k sits mid-screen, the app rows fade in, and the
     * header and banner text fade away while the artwork dims.
     */
    private void scrollToRow(int index) {
        if (index == shiftedRow) {
            return;
        }
        boolean wasHome = shiftedRow < 0;
        shiftedRow = index;
        boolean home = index < 0;
        float target = 0f;
        if (!home) {
            LinearLayout row = appRows.get(index);
            int rowTop = rowsBox.getTop() + row.getTop() + tilePad;
            target = screenH * 0.47f - dockTop - rowTop;
        }
        long dur = 560;
        lowerStack.animate().cancel();
        lowerStack.animate().translationY(target).setDuration(dur).setInterpolator(EASE)
                .setUpdateListener(a -> backdrop.invalidateAll()).start();
        rowsBox.animate().alpha(home ? 0f : 1f).setDuration(home ? 220 : 420)
                .setStartDelay(home ? 0 : 80).setInterpolator(EASE).start();

        float hideShift = -dp(24);
        header.animate().alpha(home ? 1f : 0f).translationY(home ? 0f : hideShift)
                .setDuration(home ? 420 : 260).setStartDelay(home ? 120 : 0).setInterpolator(EASE).start();
        heroInfo.animate().alpha(home ? 1f : 0f).translationY(home ? 0f : hideShift * 2)
                .setDuration(home ? 460 : 280).setStartDelay(home ? 140 : 0).setInterpolator(EASE).start();
        indicator.animate().alpha(home ? 1f : 0f).translationY(home ? 0f : hideShift * 2)
                .setDuration(home ? 460 : 280).setStartDelay(home ? 140 : 0).setInterpolator(EASE).start();
        tooltip.animate().alpha(0f).setDuration(100).start();

        if (wasHome != home) {
            animateDim(home ? 0f : 0.55f, dur);
        }
        if (home) {
            resumeTimer();
        } else {
            pauseTimer();
        }
    }

    private void animateDim(float to, long dur) {
        if (dimAnim != null) {
            dimAnim.cancel();
        }
        dimAnim = ValueAnimator.ofFloat(pageDim.getAlpha(), to);
        dimAnim.setDuration(dur);
        dimAnim.setInterpolator(EASE);
        dimAnim.addUpdateListener(a -> {
            float v = (Float) a.getAnimatedValue();
            pageDim.setAlpha(v);
            backdrop.setDim(v);
        });
        dimAnim.start();
    }

    // ================================================================== launching

    private void launchHero(HeroItem item) {
        if (item.channelNumber != null) {
            tuneChannel(item);
            return;
        }
        if (item.deepLink != null) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(item.deepLink))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setPackage(item.pkg);
            if (PKG_NETFLIX.equals(item.pkg)) {
                i.putExtra("source", "30");
            }
            if (tryStart(i)) {
                return;
            }
        }
        launchApp(item.pkg);
    }

    /** Opens Live Channels directly on the slide's channel number (e.g. 35, 734). */
    private void tuneChannel(final HeroItem item) {
        Uri cached = channelCache.get(item.channelNumber);
        if (cached != null && openChannel(cached)) {
            return;
        }
        if (!hasTvReadPermission() && !prefs.getBoolean(KEY_TV_PERM_ASKED, false)
                && Build.VERSION.SDK_INT >= 23) {
            prefs.edit().putBoolean(KEY_TV_PERM_ASKED, true).apply();
            pendingTune = item;
            requestPermissions(new String[]{PERM_EPG, PERM_LISTINGS}, REQ_TV_PERM);
            return;
        }
        new Thread(() -> {
            final Uri uri = TvSources.findChannel(this, item.channelNumber);
            handler.post(() -> {
                if (uri != null) {
                    channelCache.put(item.channelNumber, uri);
                    if (openChannel(uri)) {
                        return;
                    }
                }
                Toast.makeText(this, "Opening Live Channels. Tune to " + item.channelNumber
                        + " for " + item.title + ".", Toast.LENGTH_LONG).show();
                launchApp(PKG_LIVE);
            });
        }, "channel-lookup").start();
    }

    private boolean openChannel(Uri uri) {
        Intent i = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setPackage(PKG_LIVE);
        if (tryStart(i)) {
            return true;
        }
        i.setPackage(null); // vendor TV app
        return tryStart(i);
    }

    private boolean hasTvReadPermission() {
        return checkSelfPermission(PERM_EPG) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(PERM_LISTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_TV_PERM && pendingTune != null) {
            HeroItem item = pendingTune;
            pendingTune = null;
            tuneChannel(item);
        }
    }

    private void launchApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) {
            i = pm.getLaunchIntentForPackage(pkg);
        }
        if (i != null && tryStart(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return;
        }
        Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!tryStart(store)) {
            Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSystemSettings() {
        if (!tryStart(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            Toast.makeText(this, "Settings unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean tryStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    // ================================================================== small helpers

    private LinearLayout pill() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(18), 0, dp(22), 0);
        p.setFocusable(true);
        p.setClickable(true);
        return p;
    }

    private GradientDrawable pillBg(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(21));
        g.setColor(fill);
        if (stroke != 0) {
            g.setStroke(Math.max(1, dp(1)), stroke);
        }
        return g;
    }

    private TextView pillText(String s, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(8), 0, 0, 0);
        return t;
    }

    private ImageView icon(int res, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    private void focusScale(View v, boolean focused, float scale) {
        v.animate().scaleX(focused ? scale : 1f).scaleY(focused ? scale : 1f)
                .setDuration(focused ? 240 : 180).setInterpolator(EASE).start();
        v.setTranslationZ(focused ? dp(6) : 0f);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
