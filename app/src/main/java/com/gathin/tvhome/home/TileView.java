package com.gathin.tvhome.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/** Un-clipped wrapper + clipped rounded card; ring/glow live in the wrapper padding. */
public class TileView extends FrameLayout {

    final String pkg;
    int brandColor = 0xFF000000;
    private final float radius;
    private final int pad;
    private final float d;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float focusAmt;
    private ValueAnimator anim;

    public TileView(Context ctx, String pkg, View content, float radius, int pad) {
        super(ctx);
        this.pkg = pkg;
        this.radius = radius;
        this.pad = pad;
        d = ctx.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setPadding(pad, pad, pad, pad);

        FrameLayout card = new FrameLayout(ctx);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), TileView.this.radius);
            }
        });
        card.setClipToOutline(true);
        card.addView(content, new LayoutParams(-1, -1));
        addView(card, new LayoutParams(-1, -1));

        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFFFFFFFF);
        ring.setStrokeWidth(2.5f * d);
        glow.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (anim != null) {
            anim.cancel();
        }
        anim = ValueAnimator.ofFloat(focusAmt, gainFocus ? 1f : 0f);
        anim.setDuration(gainFocus ? 240 : 180);
        anim.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        anim.addUpdateListener(a -> {
            focusAmt = (Float) a.getAnimatedValue();
            float s = 1f + 0.065f * focusAmt;
            setScaleX(s);
            setScaleY(s);
            invalidate();
        });
        anim.start();
        setTranslationZ(gainFocus ? 4 * d : 0f);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (focusAmt > 0.01f) {
            float gap = 3.5f * d;
            for (int i = 3; i >= 1; i--) {
                float w = i * 2.6f * d;
                glow.setStrokeWidth(w);
                glow.setColor(((int) (focusAmt * (34 - i * 8)) << 24) | 0xFFFFFF);
                float inset = pad - gap - w / 2f + 1.2f * d;
                r.set(inset, inset, getWidth() - inset, getHeight() - inset);
                canvas.drawRoundRect(r, radius + gap + w / 2f, radius + gap + w / 2f, glow);
            }
        }
        super.dispatchDraw(canvas);
        if (focusAmt > 0.01f) {
            float gap = 3.5f * d;
            float inset = pad - gap;
            r.set(inset, inset, getWidth() - inset, getHeight() - inset);
            ring.setAlpha(Math.round(255 * focusAmt));
            canvas.drawRoundRect(r, radius + gap, radius + gap, ring);
        }
    }
}
