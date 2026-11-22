package com.gathin.tvhome.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.tvhome.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Glassmorphic Android TV home screen.
 *
 * Layers (bottom -> top):
 *   heroLayer   : two cross-fading full-bleed artwork ImageViews + top / bottom / left scrims
 *   lowerStack  : hero overlay (logo, meta, Play, Watchlist, carousel controls),
 *                 frosted dock (5 tiles) and peeking app rows. Scrolls as one unit on D-pad.
 *   topBar      : single-line greeting (left) and 3 circular icons | divider | clock (right)
 *   menu        : Settings dropdown (frosted, focus-trapped)
 *   tooltip     : label shown under a focused top-bar icon
 *
 * D-pad navigation is resolved by an explicit row model (see {@link #moveFocus}) so focus
 * movement is deterministic and only one element is ever highlighted.
 */
public class HomeActivity extends Activity {

    // ----------------------------------------------------------------- configuration

    private static final String PREFS = "glass_home";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_WATCHLIST = "watchlist";
    private static final String DEFAULT_NAME = "Hardik";
    private static final long SLIDE_MS = 8000L;
    private static final int STEP = 5; // slider step for D-pad left / right

    /** true: always draw the branded tiles from the mockup; false: prefer the apps' own TV banners. */
    private static final boolean STYLED_DOCK_TILES = false;

    private static final String PKG_LIVE = "com.google.android.tv";
    private static final String PKG_NETFLIX = "com.netflix.ninja";
    private static final String PKG_PRIME = "com.amazon.amazonvideo.livingroom";
    private static final String PKG_YOUTUBE = "com.google.android.youtube.tv";
    private static final String PKG_HOTSTAR = "in.startv.hotstar";

    private static final String[] DOCK_PKGS = {PKG_LIVE, PKG_NETFLIX, PKG_PRIME, PKG_YOUTUBE, PKG_HOTSTAR};

    private final HeroItem[] items = {
            new HeroItem("anupamaa", "Anupamaa",
                    "JioHotstar  \u2022  Family Drama  \u2022  Hindi  \u2022  New Episodes Daily",
                    PKG_HOTSTAR, "https://www.hotstar.com/in/shows/anupama/1260022017",
                    R.drawable.hero_anupamaa, HeroItem.LOGO_SERIF, true),
            new HeroItem("yrkkh", "Yeh Rishta Kya Kehlata Hai",
                    "JioHotstar  \u2022  Romantic Family Drama  \u2022  Hindi  \u2022  New Episodes Daily",
                    PKG_HOTSTAR, "https://www.hotstar.com/in/shows/yeh-rishta/586",
                    R.drawable.hero_yrkkh, HeroItem.LOGO_SERIF, true),
            new HeroItem("syhp", "Shayad Yahi Hai Pyaar",
                    "JioHotstar  \u2022  Romance Drama  \u2022  Hindi  \u2022  Season 1",
                    PKG_HOTSTAR, "https://www.hotstar.com/in/shows/shayad-yahi-hai-pyaar/1271646563",
                    R.drawable.hero_syhp, HeroItem.LOGO_BOLD, true),
            new HeroItem("yft", "Yeh Fitoor Tera",
                    "JioHotstar  \u2022  Romance Drama  \u2022  Hindi  \u2022  Season 1",
                    PKG_HOTSTAR, "https://www.hotstar.com/in/shows/yeh-fitoor-tera/1271650854",
                    R.drawable.hero_yft, HeroItem.LOGO_BOLD, true),
            new HeroItem("manifest", "MANIFEST",
                    "Netflix  \u2022  4 Seasons  \u2022  Mystery Drama  \u2022  Sci-Fi",
                    PKG_NETFLIX, "https://www.netflix.com/title/80241318",
                    R.drawable.hero_manifest, HeroItem.LOGO_WIDE, true),
            new HeroItem("live", "Live Channels",
                    "Live Channels App  \u2022  Live TV  \u2022  All your channels in one place",
                    PKG_LIVE, null,
                    R.drawable.hero_live, HeroItem.LOGO_APP, false),
    };

    // ----------------------------------------------------------------- state

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private AudioManager audio;
    private float density;
    private int screenW;
    private int screenH;
    private int margin;

    private FrameLayout root;
    private FrameLayout heroLayer;
    private ImageView artBottom;
    private ImageView artTop;
    private boolean topShowing;
    private final Drawable[] artCache = new Drawable[6];
    private View pageDim;

    private TextView greeting;
    private TextView clockTime;
    private TextView clockAmPm;
    private TextView clockDate;
    private TextView tooltip;
    private FrameLayout btnInputs;
    private FrameLayout btnWifi;
    private FrameLayout btnSettings;
    private View vpnDot;

    private LinearLayout lowerStack;
    private LinearLayout heroInfo;
    private LinearLayout heroText;
    private LinearLayout logoBox;
    private TextView heroMeta;
    private LinearLayout btnPlay;
    private LinearLayout btnWatch;
    private ImageView watchIcon;
    private TextView watchLabel;
    private FrameLayout btnPrev;
    private FrameLayout btnNext;
    private IndicatorView indicator;

    private FrostedPanel dock;
    private final List<View> dockTiles = new ArrayList<>();
    private final List<LinearLayout> extraRows = new ArrayList<>();
    private int tileW;
    private int tileH;
    private float tileRadius;
    private int dockPad;
    private int tileGap;

    private FrostedPanel menu;
    private boolean menuOpen;
    private final List<View> menuRows = new ArrayList<>();
    private View rowBrightness;
    private View rowVolume;
    private SliderView brightSlider;
    private SliderView volSlider;
    private TextView brightPct;
    private TextView volPct;
    private TextView nameValue;

    private int current;
    private ValueAnimator slideTimer;
    private float baseShift = Float.NaN;
    private int shiftedRow = -1; // index into extraRows, -1 = dock / hero visible

    // ----------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        density = getResources().getDisplayMetrics().density;
        screenW = Math.max(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        screenH = Math.min(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        margin = Math.round(screenW * 0.0625f);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        setContentView(root);
        hideSystemBars();

        buildHeroLayer();
        buildLowerStack();
        buildTopBar();
        buildMenu();
        buildTooltip();

        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (dock.getHeight() == 0) {
                    return;
                }
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                // Park the dock so its top sits at ~72% of the screen height (as in the mockup);
                // everything below it peeks off the bottom edge.
                baseShift = screenH * 0.725f - dock.getTop();
                lowerStack.setTranslationY(baseShift);
                lowerStack.setAlpha(1f);
                showSlide(0, false);
                dockTiles.get(0).requestFocus();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        dock.capture(heroLayer);
                    }
                }, 150);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        refreshVolume();
        refreshBrightness();
        if (slideTimer != null && !menuOpen && shiftedRow < 0) {
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
        // Home screen: Back never leaves the launcher. Handled in dispatchKeyEvent.
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

    // ----------------------------------------------------------------- hero layer

    private void buildHeroLayer() {
        heroLayer = new FrameLayout(this);
        root.addView(heroLayer, new FrameLayout.LayoutParams(-1, -1));

        artBottom = newArt();
        artTop = newArt();
        artTop.setAlpha(0f);
        heroLayer.addView(artBottom, new FrameLayout.LayoutParams(-1, -1));
        heroLayer.addView(artTop, new FrameLayout.LayoutParams(-1, -1));

        // Top-edge scrim: keeps greeting, icons and clock legible on bright skies.
        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB3000000, 0x4D000000, 0x00000000}));
        heroLayer.addView(top, new FrameLayout.LayoutParams(-1, (int) (screenH * 0.30f), Gravity.TOP));

        // Left scrim behind the title / metadata block.
        View left = new View(this);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x99000000, 0x33000000, 0x00000000}));
        heroLayer.addView(left, new FrameLayout.LayoutParams((int) (screenW * 0.62f), -1, Gravity.START));

        // Bottom-third scrim behind the buttons, dock and peeking row.
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2000000, 0xB3000000, 0x40000000, 0x00000000}));
        heroLayer.addView(bottom, new FrameLayout.LayoutParams(-1, (int) (screenH * 0.62f), Gravity.BOTTOM));

        // Extra dim used when the user scrolls down into the app rows.
        pageDim = new View(this);
        pageDim.setBackgroundColor(Color.BLACK);
        pageDim.setAlpha(0f);
        heroLayer.addView(pageDim, new FrameLayout.LayoutParams(-1, -1));
    }

    private ImageView newArt() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setFocusable(false);
        return iv;
    }

    private Drawable art(int index) {
        if (artCache[index] == null) {
            artCache[index] = getResources().getDrawable(items[index].artRes, getTheme());
        }
        return artCache[index];
    }

    private void showSlide(int index, boolean animate) {
        current = (index + items.length) % items.length;
        final HeroItem item = items[current];

        // Cross-fade artwork with a slow Ken Burns settle.
        final ImageView incoming;
        if (!animate) {
            artBottom.setImageDrawable(art(current));
            artTop.setAlpha(0f);
            topShowing = false;
            incoming = artBottom;
        } else if (!topShowing) {
            artTop.setImageDrawable(art(current));
            artTop.animate().alpha(1f).setDuration(750).setListener(null).start();
            topShowing = true;
            incoming = artTop;
        } else {
            artBottom.setImageDrawable(art(current));
            artBottom.setAlpha(1f);
            artTop.animate().alpha(0f).setDuration(750).setListener(null).start();
            topShowing = false;
            incoming = artBottom;
        }
        incoming.setScaleX(1.06f);
        incoming.setScaleY(1.06f);
        incoming.animate().scaleX(1f).scaleY(1f).setDuration(SLIDE_MS + 1500)
                .setInterpolator(new DecelerateInterpolator()).start();

        // Swap the overlay text.
        if (animate) {
            heroText.animate().alpha(0f).translationX(-12 * density).setDuration(160)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            heroText.animate().setListener(null);
                            fillHero(item);
                            heroText.setTranslationX(24 * density);
                            heroText.animate().alpha(1f).translationX(0f).setDuration(420)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                        }
                    }).start();
        } else {
            fillHero(item);
        }

        indicator.setActive(current);
        restartTimer();

        // Re-sample the frosted dock / menu once the new artwork has settled.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dock.capture(heroLayer);
                if (menuOpen) {
                    menu.capture(heroLayer);
                }
            }
        }, animate ? 820 : 80);
    }

    private void fillHero(HeroItem item) {
        logoBox.removeAllViews();
        TextView logo = new TextView(this);
        logo.setTextColor(Color.WHITE);
        logo.setShadowLayer(18 * density, 0, 2 * density, 0x99000000);
        logo.setIncludeFontPadding(false);
        logo.setMaxLines(2);
        logo.setText(item.title);
        switch (item.logoStyle) {
            case HeroItem.LOGO_WIDE:
                logo.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
                logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
                logo.setLetterSpacing(0.03f);
                break;
            case HeroItem.LOGO_SERIF:
                logo.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
                logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, item.title.length() > 14 ? 42 : 56);
                break;
            case HeroItem.LOGO_APP:
                ImageView tv = new ImageView(this);
                tv.setImageResource(R.drawable.ic_h_tv);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
                lp.rightMargin = dp(14);
                logoBox.addView(tv, lp);
                logo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
                break;
            default:
                logo.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
                logo.setLetterSpacing(0.01f);
                break;
        }
        logoBox.addView(logo, new LinearLayout.LayoutParams(-2, -2));
        heroMeta.setText(item.meta);

        if (item.watchlist) {
            btnWatch.setVisibility(View.VISIBLE);
            updateWatchButton();
        } else {
            if (btnWatch.hasFocus()) {
                btnPlay.requestFocus();
            }
            btnWatch.setVisibility(View.GONE);
        }
    }

    // ----------------------------------------------------------------- auto-scroll timer

    private void restartTimer() {
        if (slideTimer != null) {
            slideTimer.removeAllListeners();
            slideTimer.cancel();
        }
        slideTimer = ValueAnimator.ofFloat(0f, 1f);
        slideTimer.setDuration(SLIDE_MS);
        slideTimer.setInterpolator(new LinearInterpolator());
        slideTimer.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                indicator.setProgress((Float) a.getAnimatedValue());
            }
        });
        slideTimer.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    showSlide(current + 1, true);
                }
            }
        });
        slideTimer.start();
        if (menuOpen || shiftedRow >= 0) {
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

    // ----------------------------------------------------------------- lower stack

    private void buildLowerStack() {
        lowerStack = new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                // Let the stack grow past the screen so additional rows can peek / scroll in.
                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            }
        };
        lowerStack.setOrientation(LinearLayout.VERTICAL);
        lowerStack.setClipChildren(false);
        lowerStack.setClipToPadding(false);
        lowerStack.setPadding(margin, 0, margin, 0);
        lowerStack.setAlpha(0f);
        root.addView(lowerStack, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        computeTileGeometry();
        buildHeroInfo();
        buildDock();
        buildExtraRows();
    }

    private void computeTileGeometry() {
        int contentW = screenW - 2 * margin;
        dockPad = dp(15);
        tileGap = dp(26);
        tileRadius = dp(12);
        tileW = (contentW - 2 * dockPad - 4 * tileGap) / 5;
        tileH = Math.round(tileW * 0.55f);
    }

    private void buildHeroInfo() {
        heroInfo = new LinearLayout(this);
        heroInfo.setOrientation(LinearLayout.HORIZONTAL);
        heroInfo.setGravity(Gravity.BOTTOM);
        heroInfo.setClipChildren(false);
        lowerStack.addView(heroInfo, new LinearLayout.LayoutParams(-1, -2));

        heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setClipChildren(false);
        heroInfo.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1f));

        logoBox = new LinearLayout(this);
        logoBox.setOrientation(LinearLayout.HORIZONTAL);
        logoBox.setGravity(Gravity.CENTER_VERTICAL);
        heroText.addView(logoBox, new LinearLayout.LayoutParams(-2, -2));

        heroMeta = new TextView(this);
        heroMeta.setTextColor(0xD9FFFFFF);
        heroMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        heroMeta.setSingleLine(true);
        heroMeta.setEllipsize(TextUtils.TruncateAt.END);
        heroMeta.setShadowLayer(6 * density, 0, density, 0x99000000);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.topMargin = dp(8);
        heroText.addView(heroMeta, mlp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = dp(20);
        heroText.addView(buttons, blp);

        // Solid white "Play" pill.
        btnPlay = pill(true);
        ImageView playIcon = icon(R.drawable.ic_h_play, 20);
        playIcon.setColorFilter(0xFF111111, PorterDuff.Mode.SRC_IN);
        btnPlay.addView(playIcon);
        TextView playText = pillText("Play", 0xFF111111);
        btnPlay.addView(playText);
        buttons.addView(btnPlay, new LinearLayout.LayoutParams(-2, dp(40)));
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchHero(items[current]);
            }
        });
        btnPlay.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                v.animate().scaleX(f ? 1.1f : 1f).scaleY(f ? 1.1f : 1f).setDuration(160).start();
                v.setElevation(f ? dp(10) : 0);
            }
        });

        // Dark frosted "Watchlist" toggle pill.
        btnWatch = pill(false);
        watchIcon = icon(R.drawable.ic_h_check, 18);
        btnWatch.addView(watchIcon);
        watchLabel = pillText("Watchlist", Color.WHITE);
        btnWatch.addView(watchLabel);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, dp(40));
        wlp.leftMargin = dp(12);
        buttons.addView(btnWatch, wlp);
        btnWatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleWatchlist(items[current]);
            }
        });
        btnWatch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                v.setBackground(frostedPill(f));
                v.animate().scaleX(f ? 1.1f : 1f).scaleY(f ? 1.1f : 1f).setDuration(160).start();
            }
        });

        // Carousel controls: circle  |  progress pill  |  circle
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setClipChildren(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(40));
        heroInfo.addView(controls, clp);

        btnPrev = circleButton(R.drawable.ic_h_chevron_left, 34, 20);
        controls.addView(btnPrev);
        indicator = new IndicatorView(this);
        indicator.setCount(items.length);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2);
        ilp.leftMargin = dp(12);
        ilp.rightMargin = dp(12);
        controls.addView(indicator, ilp);
        btnNext = circleButton(R.drawable.ic_h_chevron_right, 34, 20);
        controls.addView(btnNext);

        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSlide(current - 1, true);
            }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSlide(current + 1, true);
            }
        });
    }

    private LinearLayout pill(boolean solidWhite) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(18), 0, dp(22), 0);
        p.setFocusable(true);
        p.setClickable(true);
        if (solidWhite) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.WHITE);
            g.setCornerRadius(dp(20));
            p.setBackground(g);
        } else {
            p.setBackground(frostedPill(false));
        }
        return p;
    }

    private GradientDrawable frostedPill(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(focused ? 0x59FFFFFF : 0x4D1A1A1E);
        g.setStroke(focused ? dp(2) : Math.max(1, dp(1)), focused ? 0xFFFFFFFF : 0x40FFFFFF);
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

    /** Strict 1:1 circular frosted button. Focus = solid white disc with dark glyph. */
    private FrameLayout circleButton(int iconRes, int sizeDp, int iconDp) {
        final FrameLayout b = new FrameLayout(this);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        b.setFocusable(true);
        b.setClickable(true);
        b.setClipChildren(false);
        b.setBackground(circleBg(false));
        final ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        b.addView(iv, new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                b.setBackground(circleBg(f));
                if (f) {
                    iv.setColorFilter(0xFF111111, PorterDuff.Mode.SRC_IN);
                } else {
                    iv.clearColorFilter();
                }
                b.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(150).start();
                onTopIconFocus(b, f);
            }
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

    // ----------------------------------------------------------------- dock & rows

    private void buildDock() {
        dock = new FrostedPanel(this);
        dock.setCornerRadius(tileRadius + dockPad); // concentric: outer = inner + padding
        dock.setTint(0x66141418);
        dock.setRim(0x2EFFFFFF, Math.max(1f, density));
        dock.setPadding(dockPad, dockPad, dockPad, dockPad);
        dock.setClipChildren(false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, tileH + 2 * dockPad);
        dlp.topMargin = dp(24);
        lowerStack.addView(dock, dlp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        dock.addView(row, new FrameLayout.LayoutParams(-1, -1));

        PackageManager pm = getPackageManager();
        for (int i = 0; i < DOCK_PKGS.length; i++) {
            String pkg = DOCK_PKGS[i];
            Drawable banner = STYLED_DOCK_TILES ? null : bannerFor(pm, pkg);
            View content = banner != null ? bannerView(banner) : styledTile(pkg);
            FrameLayout tile = tile(content, pkg, null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            if (i > 0) {
                lp.leftMargin = tileGap;
            }
            row.addView(tile, lp);
            dockTiles.add(tile);
        }
    }

    private void buildExtraRows() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        Set<String> skip = new HashSet<>(Arrays.asList(DOCK_PKGS));
        skip.add(getPackageName());
        final List<ResolveInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            String p = ri.activityInfo.packageName;
            if (!skip.contains(p) && seen.add(p)) {
                apps.add(ri);
            }
        }
        final PackageManager fpm = pm;
        Collections.sort(apps, (a, b) -> String.valueOf(a.loadLabel(fpm))
                .compareToIgnoreCase(String.valueOf(b.loadLabel(fpm))));

        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                // Same left inset as the dock interior so the columns line up exactly.
                row.setPadding(dockPad, 0, dockPad, 0);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, tileH);
                rlp.topMargin = extraRows.isEmpty() ? dp(20) : tileGap;
                lowerStack.addView(row, rlp);
                extraRows.add(row);
            }
            ResolveInfo ri = apps.get(i);
            ActivityInfo ai = ri.activityInfo;
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) {
                banner = ai.applicationInfo.loadBanner(pm);
            }
            View content = banner != null ? bannerView(banner)
                    : iconTile(ri.loadIcon(pm), String.valueOf(ri.loadLabel(pm)));
            ComponentName cn = new ComponentName(ai.packageName, ai.name);
            FrameLayout tile = tile(content, ai.packageName, cn);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            if (i % 5 > 0) {
                lp.leftMargin = tileGap;
            }
            row.addView(tile, lp);
        }
        // Bottom breathing room when the last row is scrolled into view.
        View spacer = new View(this);
        lowerStack.addView(spacer, new LinearLayout.LayoutParams(1, (int) (screenH * 0.2f)));
    }

    private Drawable bannerFor(PackageManager pm, String pkg) {
        try {
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li != null && li.getComponent() != null) {
                Drawable d = pm.getActivityBanner(li.getComponent());
                if (d != null) {
                    return d;
                }
            }
            return pm.getApplicationBanner(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private View bannerView(Drawable banner) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageDrawable(banner);
        return iv;
    }

    private View iconTile(Drawable icon, String label) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF34343A, 0xFF1E1E22}));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        int s = Math.round(tileH * 0.42f);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
        ilp.bottomMargin = Math.round(tileH * 0.12f);
        f.addView(iv, ilp);
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

    /** Branded fallback tiles matching the mockup (used when an app has no TV banner). */
    private View styledTile(String pkg) {
        FrameLayout f = new FrameLayout(this);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER);
        f.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        TextView t = new TextView(this);
        t.setIncludeFontPadding(false);
        float big = tileH * 0.24f / getResources().getDisplayMetrics().scaledDensity;
        if (PKG_LIVE.equals(pkg)) {
            f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF2AA6FF, 0xFF0A78F0}));
            ImageView iv = new ImageView(this);
            iv.setImageResource(R.drawable.ic_h_tv);
            int s = Math.round(tileH * 0.42f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.rightMargin = dp(10);
            inner.addView(iv, lp);
            t.setText("Live\nChannels");
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big * 0.62f);
        } else if (PKG_NETFLIX.equals(pkg)) {
            f.setBackgroundColor(Color.WHITE);
            t.setText("NETFLIX");
            t.setTextColor(0xFFE50914);
            t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big);
        } else if (PKG_PRIME.equals(pkg)) {
            f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF1AA3F0, 0xFF0B6FB0}));
            t.setText("prime video");
            t.setTextColor(Color.WHITE);
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big * 0.85f);
        } else if (PKG_YOUTUBE.equals(pkg)) {
            f.setBackgroundColor(0xFFF7F7F7);
            FrameLayout logo = new FrameLayout(this);
            GradientDrawable red = new GradientDrawable();
            red.setColor(0xFFFF0000);
            red.setCornerRadius(tileH * 0.07f);
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
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big * 0.85f);
        } else {
            f.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFF1446FF, 0xFF8B2BF5, 0xFFE02AB4}));
            t.setText("\u2726 JioHotstar");
            t.setTextColor(Color.WHITE);
            t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big * 0.72f);
        }
        inner.addView(t);
        return f;
    }

    /** Wide rounded tile. Focus = lift + 3dp white ring; nothing is drawn outside the tile. */
    private FrameLayout tile(View content, final String pkg, final ComponentName component) {
        final FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClickable(true);
        t.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), tileRadius);
            }
        });
        t.setClipToOutline(true);
        t.addView(content, new FrameLayout.LayoutParams(-1, -1));
        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(tileRadius);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(3), Color.WHITE);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                t.setForeground(f ? ring : null);
                t.setElevation(f ? dp(12) : 0);
                t.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f)
                        .setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (component != null) {
                    Intent i = new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            .setComponent(component)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (tryStart(i)) {
                        return;
                    }
                }
                launchApp(pkg);
            }
        });
        return t;
    }

    // ----------------------------------------------------------------- top bar

    private void buildTopBar() {
        int top = dp(34);

        greeting = new TextView(this);
        greeting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        greeting.setSingleLine(true);
        greeting.setShadowLayer(10 * density, 0, density, 0x80000000);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-2, dp(44), Gravity.TOP | Gravity.START);
        glp.leftMargin = margin;
        glp.topMargin = top;
        greeting.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(greeting, glp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-2, dp(44), Gravity.TOP | Gravity.END);
        rlp.rightMargin = margin;
        rlp.topMargin = top;
        root.addView(right, rlp);

        btnInputs = circleButton(R.drawable.ic_h_input, 40, 20);
        btnInputs.setTag("Inputs");
        right.addView(btnInputs);

        btnWifi = circleButton(R.drawable.ic_h_wifi, 40, 20);
        btnWifi.setTag("Wi-Fi");
        vpnDot = new View(this);
        GradientDrawable green = new GradientDrawable();
        green.setShape(GradientDrawable.OVAL);
        green.setColor(0xFF34C759);
        green.setStroke(Math.max(1, dp(1)), 0xFF101012);
        vpnDot.setBackground(green);
        vpnDot.setVisibility(View.GONE);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(9), dp(9), Gravity.BOTTOM | Gravity.END);
        dlp.rightMargin = dp(8);
        dlp.bottomMargin = dp(8);
        btnWifi.addView(vpnDot, dlp);
        LinearLayout.LayoutParams wlp = (LinearLayout.LayoutParams) btnWifi.getLayoutParams();
        wlp.leftMargin = dp(16);
        right.addView(btnWifi, wlp);

        btnSettings = circleButton(R.drawable.ic_h_settings, 40, 22);
        btnSettings.setTag("Settings");
        LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) btnSettings.getLayoutParams();
        slp.leftMargin = dp(16);
        right.addView(btnSettings, slp);

        View divider = new View(this);
        divider.setBackgroundColor(0x4DFFFFFF);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(36));
        vlp.leftMargin = dp(20);
        vlp.rightMargin = dp(20);
        right.addView(divider, vlp);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.START);
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

        btnInputs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInputs();
            }
        });
        btnWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!tryStart(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                    openSystemSettings();
                }
            }
        });
        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMenu();
            }
        });
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

    private void onTopIconFocus(View b, boolean focused) {
        if (tooltip == null || !(b.getTag() instanceof String)) {
            return;
        }
        if (!focused || menuOpen) {
            tooltip.animate().alpha(0f).setDuration(120).start();
            return;
        }
        tooltip.setText((String) b.getTag());
        tooltip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] a = new int[2];
        int[] r = new int[2];
        b.getLocationInWindow(a);
        root.getLocationInWindow(r);
        float cx = a[0] - r[0] + b.getWidth() / 2f;
        tooltip.setTranslationX(cx - tooltip.getMeasuredWidth() / 2f);
        tooltip.setTranslationY(a[1] - r[1] + b.getHeight() + dp(10));
        tooltip.animate().alpha(1f).setDuration(160).start();
    }

    // ----------------------------------------------------------------- settings dropdown

    private void buildMenu() {
        menu = new FrostedPanel(this);
        menu.setCornerRadius(dp(22));
        menu.setTint(0xBF18181B); // ~75% dark charcoal
        menu.setRim(0x33FFFFFF, Math.max(1f, density));
        menu.setVisibility(View.INVISIBLE);
        menu.setElevation(dp(20));
        int w = dp(272);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(w, -2, Gravity.TOP | Gravity.END);
        mlp.rightMargin = margin;
        mlp.topMargin = dp(34) + dp(44) + dp(14);
        root.addView(menu, mlp);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.addView(list, new FrameLayout.LayoutParams(-1, -2));

        // 1. Brightness
        brightSlider = new SliderView(this);
        brightPct = valueText();
        rowBrightness = sliderRow(list, R.drawable.ic_h_brightness, brightSlider, brightPct);

        // 2. Volume
        volSlider = new SliderView(this);
        volPct = valueText();
        rowVolume = sliderRow(list, R.drawable.ic_h_volume, volSlider, volPct);

        // 3. User Name
        LinearLayout nameRow = menuRow(list, dp(54));
        nameRow.addView(icon(R.drawable.ic_h_person, 22));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        TextView label = new TextView(this);
        label.setText("User Name");
        label.setTextColor(0x99FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        col.addView(label);
        nameValue = new TextView(this);
        nameValue.setTextColor(Color.WHITE);
        nameValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        nameValue.setSingleLine(true);
        nameValue.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(nameValue);
        nameRow.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView pencil = icon(R.drawable.ic_h_edit, 18);
        pencil.setAlpha(0.8f);
        nameRow.addView(pencil);
        nameRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editUserName();
            }
        });

        // 4. Divider + System Settings
        View div = new View(this);
        div.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
        dlp.topMargin = dp(6);
        dlp.bottomMargin = dp(6);
        dlp.leftMargin = dp(6);
        dlp.rightMargin = dp(6);
        list.addView(div, dlp);

        LinearLayout sys = menuRow(list, dp(46));
        sys.addView(icon(R.drawable.ic_h_settings, 22));
        TextView sysText = new TextView(this);
        sysText.setText("System Settings");
        sysText.setTextColor(Color.WHITE);
        sysText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sysText.setPadding(dp(14), 0, 0, 0);
        sys.addView(sysText, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView chev = icon(R.drawable.ic_h_chevron_right, 20);
        chev.setAlpha(0.8f);
        sys.addView(chev);
        sys.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeMenu();
                openSystemSettings();
            }
        });

        updateGreeting();
    }

    private TextView valueText() {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        t.setMinWidth(dp(42));
        return t;
    }

    private View sliderRow(LinearLayout list, int iconRes, SliderView slider, TextView pct) {
        LinearLayout row = menuRow(list, dp(46));
        row.addView(icon(iconRes, 22));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(24), 1f);
        slp.leftMargin = dp(12);
        slp.rightMargin = dp(10);
        row.addView(slider, slp);
        row.addView(pct, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    /** Uniform rounded-rectangle row container with a clean focus state. */
    private LinearLayout menuRow(LinearLayout list, int height) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackground(rowBg(false));
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                row.setBackground(rowBg(f));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, height);
        lp.topMargin = menuRows.isEmpty() ? 0 : dp(4);
        list.addView(row, lp);
        menuRows.add(row);
        return row;
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

    private void openMenu() {
        if (menuOpen) {
            return;
        }
        menuOpen = true;
        refreshVolume();
        refreshBrightness();
        tooltip.animate().alpha(0f).setDuration(100).start();
        pauseTimer();
        menu.capture(heroLayer);
        menu.setVisibility(View.VISIBLE);
        menu.setAlpha(0f);
        menu.setTranslationY(-dp(10));
        menu.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
        // Visual priority on the menu: dim the dock; focus moves into the menu so no dock ring remains.
        dock.animate().alpha(0.45f).setDuration(220).start();
        for (LinearLayout r : extraRows) {
            r.animate().alpha(0.45f).setDuration(220).start();
        }
        menuRows.get(0).requestFocus();
    }

    private void closeMenu() {
        if (!menuOpen) {
            return;
        }
        menuOpen = false;
        menu.animate().alpha(0f).translationY(-dp(10)).setDuration(160)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (!menuOpen) {
                            menu.setVisibility(View.INVISIBLE);
                        }
                    }
                }).start();
        dock.animate().alpha(1f).setDuration(200).start();
        for (LinearLayout r : extraRows) {
            r.animate().alpha(1f).setDuration(200).start();
        }
        btnSettings.requestFocus();
        if (shiftedRow < 0) {
            resumeTimer();
        }
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
            if (menuOpen) {
                menuRows.get(2).requestFocus();
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

    // ----------------------------------------------------------------- brightness / volume

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
            // Do-not-disturb policy can block volume changes.
        }
        refreshVolume();
    }

    private void refreshBrightness() {
        if (brightSlider == null) {
            return;
        }
        float w = getWindow().getAttributes().screenBrightness;
        int v;
        if (w >= 0f) {
            v = Math.round(w * 100f);
        } else {
            int sys = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 180);
            v = Math.round(sys * 100f / 255f);
        }
        brightSlider.setValue(v);
        brightPct.setText(brightSlider.getValue() + "%");
    }

    private void changeBrightness(int delta) {
        int v = Math.max(5, Math.min(100, brightSlider.getValue() + delta));
        brightSlider.setValue(v);
        brightPct.setText(v + "%");
        // Always applies while the home screen is visible ...
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = v / 100f;
        getWindow().setAttributes(lp);
        // ... and system-wide when "Modify system settings" has been granted.
        if (Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(this)) {
            try {
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS,
                        Math.round(v * 255f / 100f));
            } catch (Exception ignored) {
                // Some TV builds reject the write; the window-level brightness still applies.
            }
        }
    }

    // ----------------------------------------------------------------- watchlist

    private Set<String> watchlist() {
        return new HashSet<>(prefs.getStringSet(KEY_WATCHLIST, new HashSet<String>()));
    }

    private void toggleWatchlist(HeroItem item) {
        Set<String> set = watchlist();
        if (!set.remove(item.id)) {
            set.add(item.id);
        }
        prefs.edit().putStringSet(KEY_WATCHLIST, set).apply();
        updateWatchButton();
        restartTimer(); // give the user a full slide after interacting
    }

    private void updateWatchButton() {
        boolean saved = watchlist().contains(items[current].id);
        watchIcon.setImageResource(saved ? R.drawable.ic_h_check : R.drawable.ic_h_add);
        watchLabel.setText("Watchlist");
        btnWatch.setContentDescription(saved ? "Remove from Watchlist" : "Add to Watchlist");
    }

    // ----------------------------------------------------------------- clock & greeting

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
        String prefix;
        if (hour >= 5 && hour < 12) {
            prefix = "Good Morning, ";
        } else if (hour >= 12 && hour < 17) {
            prefix = "Good Afternoon, ";
        } else {
            prefix = "Good Evening, ";
        }
        String name = userName();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(prefix);
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
            // Missing ACCESS_NETWORK_STATE or OEM restrictions.
        }
        return false;
    }

    // ----------------------------------------------------------------- D-pad

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            boolean r = super.dispatchKeyEvent(event);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    refreshVolume();
                }
            }, 120);
            return r;
        }

        if (menuOpen) {
            return handleMenuKey(code, down, event);
        }

        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (down && event.getRepeatCount() == 0) {
                if (shiftedRow >= 0) {
                    dockTiles.get(0).requestFocus();
                    scrollToRow(-1);
                } else if (!dockTiles.contains(getCurrentFocus())) {
                    dockTiles.get(0).requestFocus();
                }
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

    /** Focus trap: only menu rows are reachable; Left/Right drive the sliders; Back closes. */
    private boolean handleMenuKey(int code, boolean down, KeyEvent event) {
        View focused = getCurrentFocus();
        int idx = menuRows.indexOf(focused);
        if (idx < 0) {
            menuRows.get(0).requestFocus();
            idx = 0;
        }
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_MENU:
                if (down && event.getRepeatCount() == 0) {
                    closeMenu();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && idx > 0) {
                    menuRows.get(idx - 1).requestFocus();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && idx < menuRows.size() - 1) {
                    menuRows.get(idx + 1).requestFocus();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    int delta = code == KeyEvent.KEYCODE_DPAD_RIGHT ? STEP : -STEP;
                    if (focused == rowBrightness) {
                        changeBrightness(delta);
                    } else if (focused == rowVolume) {
                        changeVolume(delta);
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (!down && focused != null && focused != rowBrightness && focused != rowVolume) {
                    focused.performClick();
                }
                return true;
            case KeyEvent.KEYCODE_HOME:
                return super.dispatchKeyEvent(event);
            default:
                return true; // swallow everything else while the menu is open
        }
    }

    /** Deterministic row-based navigation: top bar / hero controls / dock / app rows. */
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
            dockTiles.get(0).requestFocus();
            return true;
        }
        if (dCol != 0) {
            int nc = c + dCol;
            if (nc >= 0 && nc < rows.get(r).size()) {
                rows.get(r).get(nc).requestFocus();
            }
            return true;
        }
        int nr = r + dRow;
        if (nr < 0 || nr >= rows.size()) {
            return true;
        }
        View target;
        if (nr == 1 && dRow < 0) {
            target = btnPlay; // entering the hero from below always lands on Play
        } else {
            target = nearestByX(rows.get(nr), focused);
        }
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
        hero.add(btnPrev);
        hero.add(btnNext);
        rows.add(hero);
        rows.add(dockTiles);
        for (LinearLayout row : extraRows) {
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

    /** Smoothly scroll the lower stack so app row {@code index} sits where the dock was. */
    private void scrollToRow(int index) {
        if (Float.isNaN(baseShift) || index == shiftedRow) {
            return;
        }
        shiftedRow = index;
        float target = baseShift;
        if (index >= 0) {
            LinearLayout row = extraRows.get(index);
            target = baseShift - (row.getTop() - dock.getTop());
        }
        boolean showingHero = index < 0;
        lowerStack.animate().translationY(target).setDuration(420)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        dock.capture(heroLayer);
                    }
                }).start();
        heroInfo.animate().alpha(showingHero ? 1f : 0f).setDuration(260).start();
        pageDim.animate().alpha(showingHero ? 0f : 0.45f).setDuration(320).start();
        if (showingHero) {
            resumeTimer();
        } else {
            pauseTimer();
        }
    }

    // ----------------------------------------------------------------- launching

    private void launchHero(HeroItem item) {
        if (item.deepLink != null) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(item.deepLink))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setPackage(item.pkg);
            if (PKG_NETFLIX.equals(item.pkg)) {
                i.putExtra("source", "30"); // Netflix TV deep-link source hint
            }
            if (tryStart(i)) {
                return;
            }
        }
        launchApp(item.pkg);
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

    private void openInputs() {
        if (tryStart(new Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return;
        }
        launchApp(PKG_LIVE);
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

    // ----------------------------------------------------------------- utils

    private int dp(float v) {
        return Math.round(v * density);
    }
}
