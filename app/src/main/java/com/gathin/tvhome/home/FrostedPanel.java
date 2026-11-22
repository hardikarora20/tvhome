package com.gathin.tvhome.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Frosted-glass container.
 * Android views cannot sample what is drawn behind them (true backdrop blur), so the panel
 * snapshots the hero layer region underneath itself, blurs it by down/up-sampling, then draws:
 *   blurred backdrop -> dark tint -> children -> 1px translucent rim.
 * Call {@link #capture(View)} whenever the backdrop or the panel position changes.
 */
public class FrostedPanel extends FrameLayout {

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final RectF rimRect = new RectF();
    private final int[] srcLoc = new int[2];
    private final int[] selfLoc = new int[2];

    private Bitmap blurred;
    private float radius;

    public FrostedPanel(Context context) {
        super(context);
        setWillNotDraw(false);
        tintPaint.setColor(0x8C141418);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setColor(0x33FFFFFF);
        rimPaint.setStrokeWidth(1f);
    }

    public void setCornerRadius(float px) {
        radius = px;
        rebuildPath();
        invalidate();
    }

    public float getCornerRadius() {
        return radius;
    }

    public void setTint(int argb) {
        tintPaint.setColor(argb);
        invalidate();
    }

    public void setRim(int argb, float widthPx) {
        rimPaint.setColor(argb);
        rimPaint.setStrokeWidth(widthPx);
        rebuildPath();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildPath();
    }

    private void rebuildPath() {
        rect.set(0, 0, getWidth(), getHeight());
        float half = rimPaint.getStrokeWidth() / 2f;
        rimRect.set(half, half, getWidth() - half, getHeight() - half);
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    /** Snapshot + blur the part of {@code source} that sits behind this panel. */
    public void capture(View source) {
        if (source == null || getWidth() == 0 || getHeight() == 0
                || source.getWidth() == 0) {
            return;
        }
        final int down = 10;
        int w = Math.max(1, getWidth() / down);
        int h = Math.max(1, getHeight() / down);
        try {
            source.getLocationInWindow(srcLoc);
            getLocationInWindow(selfLoc);
            Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            c.scale(1f / down, 1f / down);
            c.translate(srcLoc[0] - selfLoc[0], srcLoc[1] - selfLoc[1]);
            source.draw(c);
            // Second down/up pass widens the blur kernel cheaply.
            Bitmap tiny = Bitmap.createScaledBitmap(small, Math.max(1, w / 3), Math.max(1, h / 3), true);
            Bitmap result = Bitmap.createScaledBitmap(tiny, w, h, true);
            small.recycle();
            tiny.recycle();
            Bitmap old = blurred;
            blurred = result;
            if (old != null && old != result) {
                old.recycle();
            }
            invalidate();
        } catch (Throwable ignored) {
            // Out of memory or detached view: keep the plain tint.
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clipPath);
        if (blurred != null && !blurred.isRecycled()) {
            canvas.drawBitmap(blurred, null, rect, bitmapPaint);
        }
        canvas.drawRect(rect, tintPaint);
        canvas.restoreToCount(save);
        super.dispatchDraw(canvas);
        canvas.drawRoundRect(rimRect, radius, radius, rimPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (blurred != null) {
            blurred.recycle();
            blurred = null;
        }
    }
}
