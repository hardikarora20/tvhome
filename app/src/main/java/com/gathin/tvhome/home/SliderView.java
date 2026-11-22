package com.gathin.tvhome.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Non-focusable horizontal slider (0-100). The menu row owns focus and drives the value. */
public class SliderView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float density;
    private int value;

    public SliderView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        track.setColor(0x40FFFFFF);
        fill.setColor(0xFFFFFFFF);
        thumb.setColor(0xFFFFFFFF);
        thumb.setShadowLayer(3 * density, 0, density, 0x66000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // needed for the thumb shadow
        setFocusable(false);
    }

    public void setValue(int v) {
        value = Math.max(0, Math.min(100, v));
        invalidate();
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
        float x = left + (right - left) * value / 100f;
        r.set(left, cy - h / 2, x, cy + h / 2);
        canvas.drawRoundRect(r, h, h, fill);
        canvas.drawCircle(x, cy, thumbR, thumb);
    }
}
