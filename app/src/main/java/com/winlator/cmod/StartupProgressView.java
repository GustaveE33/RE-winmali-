package com.winlator.cmod;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public final class StartupProgressView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackBounds = new RectF();
    private final RectF progressBounds = new RectF();
    private final Path clipPath = new Path();
    private ValueAnimator progressAnimator;
    private float progress;

    public StartupProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        trackPaint.setColor(0xB80A1020);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(dp(1));
        outlinePaint.setColor(0x5548AFFF);
        glowPaint.setColor(0x665D27C8);
        progressPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        progressAnimator = ValueAnimator.ofFloat(0f, 1f);
        progressAnimator.setDuration(1900L);
        progressAnimator.setRepeatCount(ValueAnimator.INFINITE);
        progressAnimator.setInterpolator(new LinearInterpolator());
        progressAnimator.addUpdateListener(animator -> {
            progress = (float) animator.getAnimatedValue();
            invalidate();
        });
        progressAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;

        float sideInset = dp(8);
        float trackHeight = dp(12);
        float radius = trackHeight * 0.5f;
        float centerY = getHeight() * 0.5f;
        trackBounds.set(sideInset, centerY - trackHeight * 0.5f,
                getWidth() - sideInset, centerY + trackHeight * 0.5f);
        canvas.drawRoundRect(trackBounds, radius, radius, trackPaint);
        canvas.drawRoundRect(trackBounds, radius, radius, outlinePaint);

        float innerInset = dp(2);
        float innerLeft = trackBounds.left + innerInset;
        float innerRight = trackBounds.right - innerInset;
        float innerWidth = innerRight - innerLeft;
        float segmentWidth = innerWidth * 0.36f;
        float travel = innerWidth - segmentWidth;
        float easedProgress = 0.5f - 0.5f * (float) Math.cos(progress * Math.PI * 2);
        float left = innerLeft + travel * easedProgress;
        float height = trackHeight - innerInset * 2;
        progressBounds.set(left, centerY - height * 0.5f, left + segmentWidth, centerY + height * 0.5f);

        canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(trackBounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        RectF glowBounds = new RectF(progressBounds);
        glowBounds.inset(-dp(3), -dp(3));
        glowPaint.setAlpha(70);
        canvas.drawRoundRect(glowBounds, radius, radius, glowPaint);
        progressPaint.setShader(new LinearGradient(
                progressBounds.left, centerY, progressBounds.right, centerY,
                new int[]{0x00F22FAF, 0xFFF22FAF, 0xFF8A61FF, 0xFF62D7FF, 0x0062D7FF},
                null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(progressBounds, radius, radius, progressPaint);
        progressPaint.setShader(null);
        canvas.restore();

        markerPaint.setColor(0xAA67CFFF);
        float markerY = centerY;
        canvas.drawCircle(dp(3), markerY, dp(1.5f), markerPaint);
        canvas.drawCircle(getWidth() - dp(3), markerY, dp(1.5f), markerPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
