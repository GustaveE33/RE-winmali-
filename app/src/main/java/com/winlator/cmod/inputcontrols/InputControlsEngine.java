package com.winlator.cmod.inputcontrols;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;

public final class InputControlsEngine {
    public static final String PREFERENCE_KEY = "touch_controls_engine";
    public static final String WINLATOR_121 = "winlator_1_2_1";
    public static final String WINLATOR_11 = "winlator_1_1";
    public static final String BANNERLATOR = "bannerlator";
    public static final String DEFAULT = WINLATOR_121;

    private InputControlsEngine() {}

    public static String getSelected(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String engine = preferences.getString(PREFERENCE_KEY, DEFAULT);
        if (WINLATOR_11.equals(engine) || BANNERLATOR.equals(engine)) return engine;
        return DEFAULT;
    }

    public static boolean usesWinlator11(Context context) {
        return WINLATOR_11.equals(getSelected(context));
    }

    public static boolean usesBannerlator(Context context) {
        return BANNERLATOR.equals(getSelected(context));
    }

    public static String valueAt(Context context, int position) {
        if (position == 1) return WINLATOR_11;
        if (position == 2) return BANNERLATOR;
        return DEFAULT;
    }

    public static int indexOf(String engine) {
        if (WINLATOR_11.equals(engine)) return 1;
        if (BANNERLATOR.equals(engine)) return 2;
        return 0;
    }

    public static String getProfileDirectoryName(String engine) {
        if (WINLATOR_11.equals(engine)) return "profiles_engine_1_1";
        if (BANNERLATOR.equals(engine)) return "profiles_engine_bannerlator";
        return "profiles";
    }

    public static ControlElement.Type[] getAvailableControlTypes(Context context) {
        if (!usesWinlator11(context)) return ControlElement.Type.values();
        return new ControlElement.Type[]{
                ControlElement.Type.BUTTON,
                ControlElement.Type.D_PAD,
                ControlElement.Type.RANGE_BUTTON,
                ControlElement.Type.STICK,
                ControlElement.Type.TRACKPAD
        };
    }

    public static String[] getAvailableControlTypeNames(Context context) {
        ControlElement.Type[] types = getAvailableControlTypes(context);
        ArrayList<String> names = new ArrayList<>(types.length);
        for (ControlElement.Type type : types) names.add(type.name().replace("_", "-"));
        return names.toArray(new String[0]);
    }

    public static int indexOfControlType(Context context, ControlElement.Type selectedType) {
        ControlElement.Type[] types = getAvailableControlTypes(context);
        for (int i = 0; i < types.length; i++) {
            if (types[i] == selectedType) return i;
        }
        return 0;
    }
}
