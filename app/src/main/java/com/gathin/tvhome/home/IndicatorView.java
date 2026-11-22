package com.gathin.tvhome.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Frosted pill: animated progress bar for the active slide, subtle dots for the others. */
public class IndicatorView extends View {

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;

    private int count = 1;
    private int active;
    private float progress;

    public IndicatorView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        bg.setColor(0x47000000);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, d * 0.75f));
        rim.setColor(0x38FFFFFF);
        barTrack.setColor(0x4DFFFFFF);
        barFill.setColor(0xFFFFFFFF);
        dot.setColor(0x8CFFFFFF);
        setFocusable(false);
    }

    public void setCount(int c) {
        count = Math.max(1, c);
        requestLayout();
        invalidate();
    }

    public void setActive(int i) {
        active = i;
        progress = 0f;
        invalidate();
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    private float barW() { return 30 * d; }
    private float dotD() { return 6 * d; }
    private float gap() { return 7 * d; }
    private float padH() { return 12 * d; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = (int) (padH() * 2 + barW() + (count - 1) * (dotD() + gap()));
        int h = (int) (34 * d);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float half = rim.getStrokeWidth() / 2f;
        r.set(half, half, getWidth() - half, h - half);
        canvas.drawRoundRect(r, h / 2, h / 2, bg);
        canvas.drawRoundRect(r, h / 2, h / 2, rim);
        float cy = h / 2f;
        float x = padH();
        float barH = 6 * d;
        for (int i = 0; i < count; i++) {
            if (i == active) {
                r.set(x, cy - barH / 2, x + barW(), cy + barH / 2);
                canvas.drawRoundRect(r, barH / 2, barH / 2, barTrack);
                r.set(x, cy - barH / 2, x + Math.max(barH, barW() * progress), cy + barH / 2);
                canvas.drawRoundRect(r, barH / 2, barH / 2, barFill);
                x += barW() + gap();
            } else {
                canvas.drawCircle(x + dotD() / 2, cy, dotD() / 2, dot);
                x += dotD() + gap();
            }
        }
    }
}
