package com.gathin.tvhome.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Non-focusable 0-100 slider; the menu row owns focus and drives the value. */
public class SliderView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float density;
    private float shown;
    private int value;
    private ValueAnimator anim;

    public SliderView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        track.setColor(0x40FFFFFF);
        fill.setColor(0xFFFFFFFF);
        thumb.setColor(0xFFFFFFFF);
        setFocusable(false);
    }

    public void setValue(int v) {
        value = Math.max(0, Math.min(100, v));
        if (anim != null) {
            anim.cancel();
        }
        anim = ValueAnimator.ofFloat(shown, value);
        anim.setDuration(140);
        anim.addUpdateListener(a -> {
            shown = (Float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    public int getValue() {
        return value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float thumbR = 7 * density;
        float h = 3 * density;
        float left = thumbR;
        float right = getWidth() - thumbR;
        float cy = getHeight() / 2f;
        r.set(left, cy - h / 2, right, cy + h / 2);
        canvas.drawRoundRect(r, h, h, track);
        float x = left + (right - left) * shown / 100f;
        r.set(left, cy - h / 2, x, cy + h / 2);
        canvas.drawRoundRect(r, h, h, fill);
        canvas.drawCircle(x, cy, thumbR, thumb);
    }
}
