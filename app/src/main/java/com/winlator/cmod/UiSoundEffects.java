package com.winlator.cmod;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;
import android.widget.PopupMenu;

import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class UiSoundEffects {
    private static final String TAG = "UiSoundEffects";
    private static SoundPool soundPool;
    private static Context applicationContext;
    private static final Map<Integer, Integer> soundIds = new HashMap<>();
    private static final Map<Integer, Boolean> loadedSounds = new HashMap<>();
    private static final Set<Integer> pendingSounds = new HashSet<>();
    private static boolean launchSoundPending;

    private UiSoundEffects() {}

    public static synchronized void initialize(Context context) {
        if (soundPool != null) return;
        applicationContext = context.getApplicationContext();

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attributes)
                .build();
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            synchronized (UiSoundEffects.class) {
                if (status != 0) {
                    Log.e(TAG, "Failed to load UI sound sample " + sampleId + " (status " + status + ")");
                    return;
                }
                for (Map.Entry<Integer, Integer> sound : soundIds.entrySet()) {
                    if (sound.getValue() == sampleId) loadedSounds.put(sound.getKey(), true);
                }
                for (Integer pendingResourceId : new HashSet<>(pendingSounds)) {
                    if (isLoaded(pendingResourceId)) {
                        pendingSounds.remove(pendingResourceId);
                        if (areUiSoundEffectsEnabled()) {
                            playInternal(pendingResourceId, getUiSoundEffectsVolume());
                        }
                    }
                }
                if (launchSoundPending && isLoaded(R.raw.launch_emu)) {
                    launchSoundPending = false;
                    playInternal(R.raw.launch_emu, 1f);
                }
            }
        });

        load(context, R.raw.click_open);
        load(context, R.raw.click_close);
        load(context, R.raw.launch_emu);
    }

    private static void load(Context context, int resourceId) {
        soundIds.put(resourceId, soundPool.load(context.getApplicationContext(), resourceId, 1));
        loadedSounds.put(resourceId, false);
    }

    public static synchronized void playLaunch(Context context) {
        initialize(context);
        if (isLoaded(R.raw.launch_emu)) {
            playInternal(R.raw.launch_emu, 1f);
        } else {
            launchSoundPending = true;
        }
    }

    public static synchronized void playMenuOpened() {
        if (areUiSoundEffectsEnabled()) play(R.raw.click_open);
    }

    public static synchronized void playMenuClosed() {
        if (areUiSoundEffectsEnabled()) play(R.raw.click_close);
    }

    public static synchronized void playNavigation(Context context) {
        initialize(context);
        if (areUiSoundEffectsEnabled()) play(R.raw.click_open);
    }

    public static synchronized void showMenu(PopupMenu menu) {
        if (menu == null) return;
        menu.setOnDismissListener(dismissedMenu -> playMenuClosed());
        playMenuOpened();
        menu.show();
    }

    private static void playInternal(int resourceId, float volume) {
        Integer soundId = soundIds.get(resourceId);
        if (soundPool != null && soundId != null) {
            int streamId = soundPool.play(soundId, volume, volume, 3, 0, 1f);
            if (streamId == 0) Log.e(TAG, "Unable to play UI sound resource " + resourceId);
        }
    }

    private static synchronized void play(int resourceId) {
        Integer soundId = soundIds.get(resourceId);
        if (soundPool == null || soundId == null || !areUiSoundEffectsEnabled()) return;
        float volume = getUiSoundEffectsVolume();
        if (volume <= 0f) return;
        if (!isLoaded(resourceId)) {
            pendingSounds.add(resourceId);
            return;
        }
        playInternal(resourceId, volume);
    }

    private static boolean isLoaded(int resourceId) {
        return Boolean.TRUE.equals(loadedSounds.get(resourceId));
    }

    private static boolean areUiSoundEffectsEnabled() {
        return applicationContext == null
                || PreferenceManager.getDefaultSharedPreferences(applicationContext)
                        .getBoolean(SettingsFragment.PREF_UI_SOUND_EFFECTS_ENABLED, true);
    }

    private static float getUiSoundEffectsVolume() {
        if (applicationContext == null) return 1f;
        int volume = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .getInt(SettingsFragment.PREF_UI_SOUND_EFFECTS_VOLUME, 100);
        return Math.max(0, Math.min(100, volume)) / 100f;
    }
}
