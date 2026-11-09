package com.winlator.cmod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public final class ShortcutVignetteView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int strength;

    public ShortcutVignetteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    public void setStrength(int strength) {
        int clamped = Math.max(0, Math.min(100, strength));
        if (this.strength == clamped) return;
        this.strength = clamped;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (strength == 0 || getWidth() == 0 || getHeight() == 0) return;

        float normalizedStrength = strength / 100f;
        int edgeAlpha = Math.round(245f * (float) Math.pow(normalizedStrength, 0.8f));
        int middleAlpha = Math.round(edgeAlpha * 0.45f);
        float shortestSide = Math.min(getWidth(), getHeight());
        float radius = shortestSide * 0.74f;
        canvas.save();
        canvas.scale(getWidth() / shortestSide, getHeight() / shortestSide);
        paint.setShader(new RadialGradient(
                shortestSide * 0.5f,
                shortestSide * 0.5f,
                radius,
                new int[]{
                        Color.TRANSPARENT,
                        Color.argb(middleAlpha, 0, 0, 0),
                        Color.argb(edgeAlpha, 0, 0, 0)
                },
                new float[]{0f, 0.28f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, shortestSide, shortestSide, paint);
        paint.setShader(null);
        canvas.restore();
    }
}
