package com.gathin.tvhome.home;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared, pre-blurred copy of the hero artwork (full screen, ~1/10 scale).
 * Frosted panels draw the slice of this bitmap under them on every frame, so the glass
 * stays correct while the dock scrolls or slides cross-fade.
 */
final class Backdrop {

    private Bitmap current;
    private Bitmap previous;
    private float mix = 1f;
    private float dim;
    private ValueAnimator fade;
    private final List<View> panels = new ArrayList<>();

    void register(View panel) {
        if (!panels.contains(panel)) {
            panels.add(panel);
        }
    }

    void unregister(View panel) {
        panels.remove(panel);
    }

    Bitmap current() { return current; }
    Bitmap previous() { return previous; }
    float mix() { return mix; }
    float dim() { return dim; }

    void setDim(float d) {
        dim = d;
        invalidateAll();
    }

    void show(Bitmap next, long durationMs) {
        if (next == null || next == current) {
            return;
        }
        if (fade != null) {
            fade.cancel();
        }
        if (current == null || durationMs <= 0) {
            current = next;
            previous = null;
            mix = 1f;
            invalidateAll();
            return;
        }
        previous = current;
        current = next;
        mix = 0f;
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(durationMs);
        fade.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        fade.addUpdateListener(a -> {
            mix = (Float) a.getAnimatedValue();
            invalidateAll();
        });
        fade.start();
    }

    void invalidateAll() {
        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).invalidate();
        }
    }

    /** Fast 3-pass box blur (approximates a Gaussian) on a small bitmap. */
    static Bitmap blur(Bitmap src, int radius) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] a = new int[w * h];
        int[] b = new int[w * h];
        src.getPixels(a, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            boxH(a, b, w, h, radius);
            boxV(b, a, w, h, radius);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(a, 0, w, 0, 0, w, h);
        return out;
    }

    private static void boxH(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[row + clamp(i, w)];
                sr += (p >> 16) & 0xFF; sg += (p >> 8) & 0xFF; sb += p & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int pOut = in[row + clamp(x - r, w)];
                int pIn = in[row + clamp(x + r + 1, w)];
                sr += ((pIn >> 16) & 0xFF) - ((pOut >> 16) & 0xFF);
                sg += ((pIn >> 8) & 0xFF) - ((pOut >> 8) & 0xFF);
                sb += (pIn & 0xFF) - (pOut & 0xFF);
            }
        }
    }

    private static void boxV(int[] in, int[] out, int w, int h, int r) {
        int div = r * 2 + 1;
        for (int x = 0; x < w; x++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[clamp(i, h) * w + x];
                sr += (p >> 16) & 0xFF; sg += (p >> 8) & 0xFF; sb += p & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int pOut = in[clamp(y - r, h) * w + x];
                int pIn = in[clamp(y + r + 1, h) * w + x];
                sr += ((pIn >> 16) & 0xFF) - ((pOut >> 16) & 0xFF);
                sg += ((pIn >> 8) & 0xFF) - ((pOut >> 8) & 0xFF);
                sb += (pIn & 0xFF) - (pOut & 0xFF);
            }
        }
    }

    private static int clamp(int v, int max) {
        return v < 0 ? 0 : (v >= max ? max - 1 : v);
    }
}
