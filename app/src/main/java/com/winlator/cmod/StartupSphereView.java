package com.winlator.cmod;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public final class StartupSphereView extends View {
    private static final int LATITUDE_LINES = 13;
    private static final int LONGITUDE_LINES = 28;
    private static final int LINE_SEGMENTS = 36;
    private static final int DOT_SEGMENTS = 2;

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spherePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint meshPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] pointBuffer = new float[3];
    private ValueAnimator rotationAnimator;
    private float rotation;
    private float density;

    public StartupSphereView(Context context) {
        this(context, null);
    }

    public StartupSphereView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StartupSphereView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        rotationAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        rotationAnimator.setDuration(8200L);
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.addUpdateListener(animator -> {
            rotation = (float) animator.getAnimatedValue();
            invalidate();
        });
        rotationAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
            rotationAnimator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.5f;
        float radius = Math.min(getWidth(), getHeight()) * 0.37f;
        if (radius <= 0f) return;

        glowPaint.setShader(new RadialGradient(
                cx, cy, radius * 1.45f,
                new int[]{0x663B9DFF, 0x22245BE0, 0x00102150},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius * 1.45f, glowPaint);
        spherePaint.setShader(new RadialGradient(
                cx - radius * 0.32f, cy - radius * 0.38f, radius * 1.5f,
                new int[]{0xFF53C8FF, 0xFF176DCE, 0xFF092C72, 0xFF05132F},
                new float[]{0f, 0.36f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, spherePaint);
        spherePaint.setShader(null);

        drawLatitudeMesh(canvas, cx, cy, radius);
        drawLongitudeMesh(canvas, cx, cy, radius);
        drawParticleGrid(canvas, cx, cy, radius);

        glowPaint.setShader(null);
        meshPaint.setShader(null);
        dotPaint.setShader(null);
    }

    private void drawLatitudeMesh(Canvas canvas, float cx, float cy, float radius) {
        for (int latitude = 1; latitude < LATITUDE_LINES; latitude++) {
            float phi = (float) (-Math.PI / 2 + Math.PI * latitude / LATITUDE_LINES);
            float previousZ = 0f;
            float previousX = 0f;
            float previousY = 0f;
            for (int segment = 0; segment <= LINE_SEGMENTS; segment++) {
                float theta = (float) (Math.PI * 2 * segment / LINE_SEGMENTS);
                project(phi, theta, radius, pointBuffer);
                float x = cx + pointBuffer[0];
                float y = cy + pointBuffer[1];
                if (segment > 0) {
                    setMeshColor((previousZ + pointBuffer[2]) * 0.5f, radius, 0.34f);
                    canvas.drawLine(previousX, previousY, x, y, meshPaint);
                }
                previousX = x;
                previousY = y;
                previousZ = pointBuffer[2];
            }
        }
    }

    private void drawLongitudeMesh(Canvas canvas, float cx, float cy, float radius) {
        for (int longitude = 0; longitude < LONGITUDE_LINES; longitude++) {
            float theta = (float) (Math.PI * 2 * longitude / LONGITUDE_LINES) + rotation;
            float previousZ = 0f;
            float previousX = 0f;
            float previousY = 0f;
            for (int segment = 0; segment <= LINE_SEGMENTS; segment++) {
                float phi = (float) (-Math.PI / 2 + Math.PI * segment / LINE_SEGMENTS);
                project(phi, theta, radius, pointBuffer);
                float x = cx + pointBuffer[0];
                float y = cy + pointBuffer[1];
                if (segment > 0) {
                    setMeshColor((previousZ + pointBuffer[2]) * 0.5f, radius, 0.42f);
                    canvas.drawLine(previousX, previousY, x, y, meshPaint);
                }
                previousX = x;
                previousY = y;
                previousZ = pointBuffer[2];
            }
        }
    }

    private void drawParticleGrid(Canvas canvas, float cx, float cy, float radius) {
        for (int latitude = 1; latitude < LATITUDE_LINES; latitude++) {
            float phi = (float) (-Math.PI / 2 + Math.PI * latitude / LATITUDE_LINES);
            for (int longitude = 0; longitude < LONGITUDE_LINES * DOT_SEGMENTS; longitude++) {
                float theta = (float) (Math.PI * 2 * longitude / (LONGITUDE_LINES * DOT_SEGMENTS)) + rotation;
                project(phi, theta, radius, pointBuffer);
                float depth = (pointBuffer[2] / radius + 1f) * 0.5f;
                dotPaint.setColor(0xFF9EDFFF);
                dotPaint.setAlpha((int) (38 + depth * 155));
                float dotRadius = density * (0.55f + depth * 0.5f);
                canvas.drawCircle(cx + pointBuffer[0], cy + pointBuffer[1], dotRadius, dotPaint);
            }
        }
    }

    private void project(float latitude, float longitude, float radius, float[] out) {
        float wave = 1f
                + 0.105f * (float) Math.sin(longitude * 3f + rotation * 1.7f) * (float) Math.cos(latitude * 2.4f)
                + 0.055f * (float) Math.cos(latitude * 5f - rotation) * (float) Math.sin(longitude * 2f);
        float ringRadius = radius * (float) Math.cos(latitude) * wave;
        float x = ringRadius * (float) Math.sin(longitude);
        float z = ringRadius * (float) Math.cos(longitude);
        float y = radius * (float) Math.sin(latitude) * wave;
        float perspective = radius / (radius * 2.8f - z);
        out[0] = x * perspective;
        out[1] = y * perspective;
        out[2] = z;
    }

    private void setMeshColor(float depth, float radius, float opacity) {
        float light = Math.max(0f, Math.min(1f, (depth / radius + 1f) * 0.5f));
        meshPaint.setColor(light > 0.5f ? 0xFF9BDFFF : 0xFF347FEA);
        meshPaint.setAlpha((int) ((38f + light * 120f) * opacity));
        meshPaint.setStrokeWidth(density * (0.45f + light * 0.45f));
        meshPaint.setStrokeCap(Paint.Cap.ROUND);
    }
}
