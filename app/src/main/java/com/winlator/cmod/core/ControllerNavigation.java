package com.winlator.cmod.core;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.ScrollView;

import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.ExternalControllerBindingsActivity;
import com.winlator.cmod.XServerDisplayActivity;

import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Makes the regular touch UI usable with a gamepad. The D-pad / left stick already move focus natively;
 * this adds what's missing: a visible highlight on the focused view (our custom backgrounds have no
 * focused state, so focus was invisible), A = select, B = back, X = long-press (item menus),
 * LT/RT = page up/down. Screen-level buttons (Y, START, SELECT, LB/RB) are handled by MainActivity.
 */
public final class ControllerNavigation {
    public interface Dispatcher {
        boolean dispatch(KeyEvent event);
    }

    private ControllerNavigation() {}
    private static final long MIN_CONTROLLER_REPEAT_INTERVAL_MS = 180L;
    private static final Map<Integer, Long> lastControllerRepeatTimes = new HashMap<>();

    /** Off for screens that need raw gamepad buttons: the binding editor, and the game itself. */
    public static boolean isEnabledFor(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof ExternalControllerBindingsActivity || context instanceof XServerDisplayActivity) return false;
            if (context instanceof android.app.Activity) return true;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return true;
    }

    public static boolean dispatch(KeyEvent event, Window window, Dispatcher fallback) {
        if (isControllerEvent(event) && isDirectionalKey(event.getKeyCode())
                && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() > 0) {
            int key = event.getDeviceId() * 31 + event.getKeyCode();
            long now = android.os.SystemClock.uptimeMillis();
            synchronized (lastControllerRepeatTimes) {
                Long previous = lastControllerRepeatTimes.get(key);
                if (previous != null && now - previous < MIN_CONTROLLER_REPEAT_INTERVAL_MS) return true;
                lastControllerRepeatTimes.put(key, now);
            }
        } else if (isControllerEvent(event) && isDirectionalKey(event.getKeyCode())
                && event.getAction() == KeyEvent.ACTION_UP) {
            int key = event.getDeviceId() * 31 + event.getKeyCode();
            synchronized (lastControllerRepeatTimes) {
                lastControllerRepeatTimes.remove(key);
            }
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_A:
                return fallback.dispatch(remap(event, KeyEvent.KEYCODE_DPAD_CENTER));
            case KeyEvent.KEYCODE_BUTTON_B:
                return fallback.dispatch(remap(event, KeyEvent.KEYCODE_BACK));
            case KeyEvent.KEYCODE_BUTTON_X:
                if (isPlayStationConfirmButton(event)) {
                    return fallback.dispatch(remap(event, KeyEvent.KEYCODE_DPAD_CENTER));
                }
                if (event.getAction() == KeyEvent.ACTION_UP && window != null) {
                    View focused = window.getCurrentFocus();
                    if (focused != null) focused.performLongClick();
                }
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                // Used by MainActivity (jump to top bar); swallowed elsewhere so it can't fall back to BACK.
                return true;
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R2:
                // Page through long lists: a burst of D-pad steps keeps focus and scrolling in sync.
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int dpad = event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_L2 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
                    for (int i = 0; i < 5; i++) {
                        fallback.dispatch(remap(event, dpad, KeyEvent.ACTION_DOWN));
                        fallback.dispatch(remap(event, dpad, KeyEvent.ACTION_UP));
                    }
                }
                return true;
            default:
                return fallback.dispatch(event);
        }
    }

    private static boolean isDirectionalKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private static boolean isControllerEvent(KeyEvent event) {
        android.view.InputDevice device = android.view.InputDevice.getDevice(event.getDeviceId());
        if (device == null) return false;
        int sources = device.getSources();
        return (sources & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD
                || (sources & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK;
    }

    private static boolean isPlayStationConfirmButton(KeyEvent event) {
        android.view.InputDevice device = android.view.InputDevice.getDevice(event.getDeviceId());
        if (device == null) return false;
        String name = device.getName().toLowerCase(java.util.Locale.ROOT);
        return device.getVendorId() == 0x054c || name.contains("playstation") || name.contains("dualsense")
                || name.contains("dualshock") || name.contains("sony");
    }

    private static KeyEvent remap(KeyEvent e, int keyCode, int action) {
        return new KeyEvent(e.getDownTime(), e.getEventTime(), action, keyCode, 0,
                e.getMetaState(), e.getDeviceId(), e.getScanCode(), e.getFlags(), e.getSource());
    }

    public static boolean isControllerConnected() {
        for (int id : android.view.InputDevice.getDeviceIds()) {
            android.view.InputDevice device = android.view.InputDevice.getDevice(id);
            if (device == null || device.isVirtual()) continue;
            int sources = device.getSources();
            if ((sources & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD ||
                (sources & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK) return true;
        }
        return false;
    }

    /** Fills the hint bar with button chips: {glyph, label, color} triples. */
    public static void buildHints(android.widget.LinearLayout bar, String[][] hints) {
        Context context = bar.getContext();
        bar.removeAllViews();
        int size = (int) UnitUtils.dpToPx(22);
        for (String[] hint : hints) {
            android.widget.TextView glyph = new android.widget.TextView(context);
            glyph.setText(hint[0]);
            glyph.setTextColor(0xFFFFFFFF);
            glyph.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, hint[0].length() > 1 ? 9 : 11);
            glyph.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            glyph.setGravity(android.view.Gravity.CENTER);
            GradientDrawable chip = new GradientDrawable();
            chip.setColor(android.graphics.Color.parseColor(hint[2]));
            chip.setCornerRadius(size / 2f);
            glyph.setBackground(chip);
            glyph.setMinWidth(size);
            glyph.setPadding((int) UnitUtils.dpToPx(5), 0, (int) UnitUtils.dpToPx(5), 0);
            android.widget.LinearLayout.LayoutParams glyphParams = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, size);
            glyphParams.setMarginStart((int) UnitUtils.dpToPx(10));
            bar.addView(glyph, glyphParams);

            android.widget.TextView label = new android.widget.TextView(context);
            label.setText(hint[1]);
            label.setTextColor(0xDDFFFFFF);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            android.widget.LinearLayout.LayoutParams labelParams = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.setMarginStart((int) UnitUtils.dpToPx(5));
            bar.addView(label, labelParams);
        }
    }

    private static KeyEvent remap(KeyEvent e, int keyCode) {
        return new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), keyCode, e.getRepeatCount(),
                e.getMetaState(), e.getDeviceId(), e.getScanCode(), e.getFlags(), e.getSource());
    }

    /** Gives controller-focused views a subtle lift and scale instead of a rectangular focus outline. */
    public static void attach(Window window) {
        if (window == null || !(window.getDecorView() instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup decor = (android.view.ViewGroup) window.getDecorView();
        FocusFeedback focusFeedback = new FocusFeedback();
        ViewTreeObserver observer = decor.getViewTreeObserver();
        observer.addOnGlobalFocusChangeListener((oldFocus, newFocus) ->
                focusFeedback.setTarget(newFocus != null && !newFocus.isInTouchMode() ? newFocus : null));
        observer.addOnTouchModeChangeListener(inTouchMode ->
                focusFeedback.setTarget(inTouchMode ? null : window.getCurrentFocus()));
        observer.addOnPreDrawListener(() -> {
            focusFeedback.update();
            return true;
        });
    }

    private static final class FocusFeedback {
        private final android.graphics.Rect rect = new android.graphics.Rect();
        private View target;
        private float originalScaleX;
        private float originalScaleY;
        private float originalElevation;

        void setTarget(View view) {
            // Scroll containers only get focus when they have nothing focusable inside; outlining the whole
            // list would just look broken.
            if (view instanceof RecyclerView || view instanceof AbsListView || view instanceof ScrollView
                    || hasCarouselAncestor(view)) view = null;
            if (target == view) return;
            clearTarget();
            target = view;
            if (target != null) {
                originalScaleX = target.getScaleX();
                originalScaleY = target.getScaleY();
                originalElevation = target.getElevation();
                target.animate().cancel();
                target.animate()
                        .scaleX(originalScaleX * 1.035f)
                        .scaleY(originalScaleY * 1.035f)
                        .setDuration(100)
                        .start();
                target.setElevation(originalElevation + UnitUtils.dpToPx(8));
            }
            update();
        }

        private boolean hasCarouselAncestor(View view) {
            for (android.view.ViewParent parent = view != null ? view.getParent() : null;
                 parent instanceof View;
                 parent = parent.getParent()) {
                if (parent instanceof RecyclerView) return true;
            }
            return false;
        }

        void update() {
            if (target == null || !target.isAttachedToWindow() || !target.isShown() || !target.getGlobalVisibleRect(rect)) {
                clearTarget();
            }
        }

        private void clearTarget() {
            if (target == null) return;
            target.animate().cancel();
            target.setScaleX(originalScaleX);
            target.setScaleY(originalScaleY);
            target.setElevation(originalElevation);
            target = null;
        }
    }
}
