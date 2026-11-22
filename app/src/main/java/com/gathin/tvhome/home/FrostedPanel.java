package com.gathin.tvhome.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.FrameLayout;

/** Frosted-glass container drawing the live blurred backdrop slice beneath it. */
public class FrostedPanel extends FrameLayout {

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final RectF rimRect = new RectF();
    private final Rect src = new Rect();
    private final int[] loc = new int[2];

    private Backdrop backdrop;
    private float radius;
    private int screenW = 1;
    private int screenH = 1;

    public FrostedPanel(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        tintPaint.setColor(0x8C141418);
        dimPaint.setColor(0xFF000000);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setColor(0x33FFFFFF);
        rimPaint.setStrokeWidth(1f);
    }

    void attach(Backdrop b, int screenWidth, int screenHeight) {
        backdrop = b;
        screenW = Math.max(1, screenWidth);
        screenH = Math.max(1, screenHeight);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (backdrop != null) {
            backdrop.register(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (backdrop != null) {
            backdrop.unregister(this);
        }
        super.onDetachedFromWindow();
    }

    public void setCornerRadius(float px) {
        radius = px;
        rebuild();
        invalidate();
    }

    public void setTint(int argb) {
        tintPaint.setColor(argb);
        invalidate();
    }

    public void setRim(int argb, float widthPx) {
        rimPaint.setColor(argb);
        rimPaint.setStrokeWidth(widthPx);
        rebuild();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rebuild();
    }

    private void rebuild() {
        rect.set(0, 0, getWidth(), getHeight());
        float half = rimPaint.getStrokeWidth() / 2f;
        rimRect.set(half, half, getWidth() - half, getHeight() - half);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clip);
        if (backdrop != null) {
            getLocationInWindow(loc);
            Bitmap prev = backdrop.previous();
            Bitmap cur = backdrop.current();
            if (prev != null && backdrop.mix() < 1f) {
                drawSlice(canvas, prev, 255);
            }
            if (cur != null) {
                drawSlice(canvas, cur, Math.round(255 * backdrop.mix()));
            }
            if (backdrop.dim() > 0f) {
                dimPaint.setAlpha(Math.round(255 * backdrop.dim()));
                canvas.drawRect(rect, dimPaint);
            }
        }
        canvas.drawRect(rect, tintPaint);
        canvas.restoreToCount(save);
        int save2 = canvas.save();
        if (clipChildrenToShape) {
            canvas.clipPath(clip);
        }
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save2);
        canvas.drawRoundRect(rimRect, radius, radius, rimPaint);
    }

    /** Menus clip their (scrolling) content to the rounded shape; the dock does not,
     *  so focused tiles can grow past its edge. */
    private boolean clipChildrenToShape = true;

    void setClipChildrenToShape(boolean clipIt) {
        clipChildrenToShape = clipIt;
        invalidate();
    }

    private void drawSlice(Canvas canvas, Bitmap bmp, int alpha) {
        if (bmp.isRecycled() || alpha <= 0) {
            return;
        }
        float sx = bmp.getWidth() / (float) screenW;
        float sy = bmp.getHeight() / (float) screenH;
        float w = getWidth() * getScaleX();
        float h = getHeight() * getScaleY();
        src.set(Math.round(loc[0] * sx), Math.round(loc[1] * sy),
                Math.round((loc[0] + w) * sx), Math.round((loc[1] + h) * sy));
        bmpPaint.setAlpha(alpha);
        canvas.drawBitmap(bmp, src, rect, bmpPaint);
    }
}
