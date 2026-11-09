package com.winlator.cmod;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public final class ShortcutBlurTransformation extends BitmapTransformation {
    private static final String ID = "com.winlator.cmod.ShortcutBlurTransformation.v1";
    private final int radius;

    public ShortcutBlurTransformation(int strength) {
        radius = Math.max(1, Math.min(25, Math.round(strength * 0.25f)));
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap source, int outWidth, int outHeight) {
        int width = source.getWidth();
        int height = source.getHeight();
        int count = width * height;
        int[] pixels = new int[count];
        int[] intermediate = new int[count];
        int[] blurred = new int[count];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        blurPass(pixels, intermediate, width, height, radius, true);
        blurPass(intermediate, blurred, width, height, radius, false);

        Bitmap result = pool.get(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(blurred, 0, width, 0, 0, width, height);
        return result;
    }

    private static void blurPass(int[] source, int[] target, int width, int height, int radius, boolean horizontal) {
        int lineCount = horizontal ? height : width;
        int lineLength = horizontal ? width : height;
        int divisor = radius * 2 + 1;

        for (int line = 0; line < lineCount; line++) {
            int alpha = 0;
            int red = 0;
            int green = 0;
            int blue = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int position = Math.max(0, Math.min(lineLength - 1, offset));
                int color = source[index(line, position, width, horizontal)];
                alpha += ColorChannel.alpha(color);
                red += ColorChannel.red(color);
                green += ColorChannel.green(color);
                blue += ColorChannel.blue(color);
            }

            for (int position = 0; position < lineLength; position++) {
                target[index(line, position, width, horizontal)] =
                        (alpha / divisor << 24) | (red / divisor << 16) | (green / divisor << 8) | blue / divisor;
                if (position + 1 == lineLength) continue;

                int removePosition = Math.max(0, Math.min(lineLength - 1, position - radius));
                int addPosition = Math.max(0, Math.min(lineLength - 1, position + radius + 1));
                int removeColor = source[index(line, removePosition, width, horizontal)];
                int addColor = source[index(line, addPosition, width, horizontal)];
                alpha += ColorChannel.alpha(addColor) - ColorChannel.alpha(removeColor);
                red += ColorChannel.red(addColor) - ColorChannel.red(removeColor);
                green += ColorChannel.green(addColor) - ColorChannel.green(removeColor);
                blue += ColorChannel.blue(addColor) - ColorChannel.blue(removeColor);
            }
        }
    }

    private static int index(int line, int position, int width, boolean horizontal) {
        return horizontal ? line * width + position : position * width + line;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID.getBytes(StandardCharsets.UTF_8));
        messageDigest.update(ByteBuffer.allocate(Integer.BYTES).putInt(radius).array());
    }

    private static final class ColorChannel {
        static int alpha(int color) {
            return color >>> 24;
        }

        static int red(int color) {
            return (color >> 16) & 0xFF;
        }

        static int green(int color) {
            return (color >> 8) & 0xFF;
        }

        static int blue(int color) {
            return color & 0xFF;
        }
    }
}
