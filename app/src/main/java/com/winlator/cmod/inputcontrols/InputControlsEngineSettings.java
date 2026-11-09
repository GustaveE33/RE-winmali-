package com.winlator.cmod.inputcontrols;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.widget.InputControlsView;

public final class InputControlsEngineSettings {
    private static final String KEY_PREFIX = "touch_controls_engine.";

    private InputControlsEngineSettings() {}

    private static String key(String engine, String setting) {
        return KEY_PREFIX + engine + "." + setting;
    }

    public static boolean getShowTouchscreenControls(Context context, String engine) {
        return getBoolean(context, engine, "show_touchscreen_controls_enabled",
                !InputControlsEngine.BANNERLATOR.equals(engine));
    }

    public static boolean getTouchscreenTimeoutEnabled(Context context, String engine) {
        return getBoolean(context, engine, "touchscreen_timeout_enabled", false);
    }

    public static boolean getTouchscreenHapticsEnabled(Context context, String engine) {
        return getBoolean(context, engine, "touchscreen_haptics_enabled",
                !InputControlsEngine.BANNERLATOR.equals(engine));
    }

    public static float getOverlayOpacity(Context context, String engine) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String scopedKey = key(engine, "overlay_opacity");
        if (preferences.contains(scopedKey)) {
            return preferences.getFloat(scopedKey, InputControlsView.DEFAULT_OVERLAY_OPACITY);
        }
        if (InputControlsEngine.DEFAULT.equals(engine) && preferences.contains("overlay_opacity")) {
            return preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY);
        }
        return InputControlsView.DEFAULT_OVERLAY_OPACITY;
    }

    public static void setShowTouchscreenControls(Context context, String engine, boolean enabled) {
        putBoolean(context, engine, "show_touchscreen_controls_enabled", enabled);
    }

    public static void setTouchscreenTimeoutEnabled(Context context, String engine, boolean enabled) {
        putBoolean(context, engine, "touchscreen_timeout_enabled", enabled);
    }

    public static void setTouchscreenHapticsEnabled(Context context, String engine, boolean enabled) {
        putBoolean(context, engine, "touchscreen_haptics_enabled", enabled);
    }

    public static void setOverlayOpacity(Context context, String engine, float opacity) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putFloat(key(engine, "overlay_opacity"), opacity).apply();
    }

    private static boolean getBoolean(Context context, String engine, String setting, boolean defaultValue) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String scopedKey = key(engine, setting);
        if (preferences.contains(scopedKey)) return preferences.getBoolean(scopedKey, defaultValue);
        if (InputControlsEngine.DEFAULT.equals(engine) && preferences.contains(setting)) {
            return preferences.getBoolean(setting, defaultValue);
        }
        return defaultValue;
    }

    private static void putBoolean(Context context, String engine, String setting, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(key(engine, setting), enabled).apply();
    }
}
