package com.winlator.cmod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.cmod.box64.Box64EditPresetDialog;
import com.winlator.cmod.box64.Box64Preset;
import com.winlator.cmod.box64.Box64PresetManager;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.fexcore.FEXCoreEditPresetDialog;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.fexcore.FEXCorePresetManager;
import com.winlator.cmod.inputcontrols.InputControlsEngine;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class SettingsFragment extends Fragment {
    public static final String DEFAULT_WINE_DEBUG_CHANNELS = "warn,err,fixme";
    public static final String DEFAULT_WINLATOR_PATH = Environment.getExternalStorageDirectory().getPath() + "/Winlator";
    public static final String DEFAULT_SHORTCUT_EXPORT_PATH = DEFAULT_WINLATOR_PATH + "/Shortcuts";
    public static final String PREF_LAUNCH_SHORTCUTS_ON_DEFAULT = "launch_shortcuts_on_default";
    public static final String PREF_STARTUP_ANIMATION_ENABLED = "startup_animation_enabled";
    public static final String PREF_UI_SOUND_EFFECTS_ENABLED = "ui_sound_effects_enabled";
    public static final String PREF_UI_SOUND_EFFECTS_VOLUME = "ui_sound_effects_volume";
    public static final String PREF_SHORTCUT_VIGNETTE_STRENGTH = "shortcut_vignette_strength";
    public static final String PREF_SHORTCUT_BLUR_STRENGTH = "shortcut_blur_strength";
    public static final String PREF_SHORTCUT_VERTICAL_SCROLL = "shortcut_vertical_scroll";
    public static final String PREF_APP_ORIENTATION = "app_orientation";
    private Callback<Uri> installSoundFontCallback;
    private PreloaderDialog preloaderDialog;
    private SharedPreferences preferences;

	// Disable or enable True Mouse Control
	private CheckBox cbCursorLock;
    // Disable or enable Xinput Processing
    private CheckBox cbXinputToggle;

    private EditText etCustomApiKey;
    private RadioGroup rgSteamGridApiKeyMode;

    boolean isDarkMode = true;

    private static final int REQUEST_CODE_WINLATOR_PATH = 1002;
    private static final int REQUEST_CODE_SHORTCUT_EXPORT_PATH = 1003;
    private static final int REQUEST_CODE_INSTALL_SOUNDFONT = 1001;
    private static final int REQUEST_CODE_IMPORT_BOX64_PRESET = 1004;
    private static final int REQUEST_CODE_IMPORT_FEXCORE_PRESET = 1005;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Apply dynamic styles to all labels
        applyDynamicStylesRecursively(view);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings_fragment, container, false);
        final Context context = getContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Check for Dark Mode preference
        isDarkMode = ThemeManager.isDarkMode(context);
        // Apply dynamic styles
        applyDynamicStyles(view, isDarkMode);
        configurePortraitSettingsLayout(view, context);

        final View[] categoryButtons = {
                view.findViewById(R.id.BTSettingsThemes),
                view.findViewById(R.id.BTSettingsCpu),
                view.findViewById(R.id.BTSettingsDisplay),
                view.findViewById(R.id.BTSettingsStorage),
                view.findViewById(R.id.BTSettingsControllers),
                view.findViewById(R.id.BTSettingsRuntime)
        };
        final View[] categoryGroups = {
                view.findViewById(R.id.LLTabAppearance),
                view.findViewById(R.id.LLTabEmulation),
                view.findViewById(R.id.LLTabInputDisplay),
                view.findViewById(R.id.LLTabStorageAudio),
                view.findViewById(R.id.LLTabPerformance),
                view.findViewById(R.id.LLTabAdvanced)
        };
        final int[] categoryTitles = {
                R.string.settings_themes_title,
                R.string.settings_cpu_title,
                R.string.settings_display_title,
                R.string.settings_storage_title,
                R.string.settings_controllers_title,
                R.string.settings_runtime_title
        };
        final int[] categorySubtitles = {
                R.string.settings_themes_subtitle,
                R.string.settings_cpu_subtitle,
                R.string.settings_display_subtitle,
                R.string.settings_storage_subtitle,
                R.string.settings_controllers_subtitle,
                R.string.settings_runtime_subtitle
        };
        if (!preferences.getBoolean("settings_shortcuts_appearance_migrated", false)) {
            SharedPreferences.Editor migration = preferences.edit()
                    .putBoolean("settings_shortcuts_appearance_migrated", true);
            if (preferences.getBoolean("settings_category_shortcuts_migrated", false)) {
                int previousCategory = preferences.getInt("settings_category_console", 0);
                migration.putInt("settings_category_console",
                        previousCategory == 1 ? 0 : previousCategory > 1 ? previousCategory - 1 : 0);
            }
            migration.apply();
        }
        TextView categoryTitle = view.findViewById(R.id.TVSettingsCategoryTitle);
        TextView categorySubtitle = view.findViewById(R.id.TVSettingsCategorySubtitle);
        for (int i = 0; i < categoryButtons.length; i++) {
            final int category = i;
            categoryButtons[i].setOnClickListener(v -> {
                preferences.edit().putInt("settings_category_console", category).apply();
                showSettingsCategory(category, categoryButtons, categoryGroups, categoryTitle, categorySubtitle,
                        categoryTitles, categorySubtitles);
            });
            categoryButtons[i].setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    preferences.edit().putInt("settings_category_console", category).apply();
                    showSettingsCategory(category, categoryButtons, categoryGroups, categoryTitle, categorySubtitle,
                            categoryTitles, categorySubtitles);
                }
            });
        }
        int selectedCategory = preferences.getInt("settings_category_console", 0);
        showSettingsCategory(selectedCategory, categoryButtons, categoryGroups, categoryTitle, categorySubtitle,
                categoryTitles, categorySubtitles);

        final CheckBox cbDisableBootAnimation = view.findViewById(R.id.CBDisableBootAnimation);
        cbDisableBootAnimation.setChecked(!preferences.getBoolean(PREF_STARTUP_ANIMATION_ENABLED, true));
        cbDisableBootAnimation.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(PREF_STARTUP_ANIMATION_ENABLED, !checked).apply());

        final CheckBox cbSoundEffects = view.findViewById(R.id.CBSoundEffects);
        cbSoundEffects.setChecked(preferences.getBoolean(PREF_UI_SOUND_EFFECTS_ENABLED, true));
        cbSoundEffects.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(PREF_UI_SOUND_EFFECTS_ENABLED, checked).apply());

        final SeekBar sbSoundEffectsVolume = view.findViewById(R.id.SBSoundEffectsVolume);
        final TextView tvSoundEffectsVolume = view.findViewById(R.id.TVSoundEffectsVolume);
        sbSoundEffectsVolume.setProgress(preferences.getInt(PREF_UI_SOUND_EFFECTS_VOLUME, 100));
        tvSoundEffectsVolume.setText(sbSoundEffectsVolume.getProgress() + "%");
        sbSoundEffectsVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSoundEffectsVolume.setText(progress + "%");
                if (fromUser) preferences.edit().putInt(PREF_UI_SOUND_EFFECTS_VOLUME, progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        final CheckBox cbLaunchOnDefault = view.findViewById(R.id.CBLaunchOnDefault);
        cbLaunchOnDefault.setChecked(preferences.getBoolean(PREF_LAUNCH_SHORTCUTS_ON_DEFAULT, false));
        cbLaunchOnDefault.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(PREF_LAUNCH_SHORTCUTS_ON_DEFAULT, checked).apply());

        RadioGroup rgShortcutScrollDirection = view.findViewById(R.id.RGShortcutScrollDirection);
        rgShortcutScrollDirection.check(preferences.getBoolean(PREF_SHORTCUT_VERTICAL_SCROLL, false)
                ? R.id.RBShortcutScrollVertical
                : R.id.RBShortcutScrollHorizontal);
        rgShortcutScrollDirection.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.RBShortcutScrollHorizontal || checkedId == R.id.RBShortcutScrollVertical) {
                preferences.edit()
                        .putBoolean(PREF_SHORTCUT_VERTICAL_SCROLL, checkedId == R.id.RBShortcutScrollVertical)
                        .apply();
            }
        });

        final SeekBar sbShortcutVignetteStrength = view.findViewById(R.id.SBShortcutVignetteStrength);
        final TextView tvShortcutVignetteStrength = view.findViewById(R.id.TVShortcutVignetteStrength);
        sbShortcutVignetteStrength.setProgress(preferences.getInt(PREF_SHORTCUT_VIGNETTE_STRENGTH, 30));
        tvShortcutVignetteStrength.setText(sbShortcutVignetteStrength.getProgress() + "%");
        sbShortcutVignetteStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvShortcutVignetteStrength.setText(progress + "%");
                if (fromUser) preferences.edit().putInt(PREF_SHORTCUT_VIGNETTE_STRENGTH, progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        final SeekBar sbShortcutBlurStrength = view.findViewById(R.id.SBShortcutBlurStrength);
        final TextView tvShortcutBlurStrength = view.findViewById(R.id.TVShortcutBlurStrength);
        sbShortcutBlurStrength.setProgress(preferences.getInt(PREF_SHORTCUT_BLUR_STRENGTH, 0));
        tvShortcutBlurStrength.setText(sbShortcutBlurStrength.getProgress() + "%");
        sbShortcutBlurStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvShortcutBlurStrength.setText(progress + "%");
                if (fromUser) preferences.edit().putInt(PREF_SHORTCUT_BLUR_STRENGTH, progress).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        Spinner sAppOrientation = view.findViewById(R.id.SAppOrientation);
        String[] orientationEntries = getResources().getStringArray(R.array.app_orientation_entries);
        String[] orientationValues = getResources().getStringArray(R.array.app_orientation_values);
        android.widget.ArrayAdapter<String> orientationAdapter = new android.widget.ArrayAdapter<>(
                context, R.layout.custom_spinner_item, orientationEntries);
        orientationAdapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        sAppOrientation.setAdapter(orientationAdapter);
        sAppOrientation.setPopupBackgroundResource(
                isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        String selectedOrientation = preferences.getString(PREF_APP_ORIENTATION, "auto");
        int selectedOrientationIndex = 0;
        for (int i = 0; i < orientationValues.length; i++) {
            if (orientationValues[i].equals(selectedOrientation)) {
                selectedOrientationIndex = i;
                break;
            }
        }
        sAppOrientation.setSelection(selectedOrientationIndex);
        sAppOrientation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selectedView, int position, long id) {
                String orientation = orientationValues[position];
                if (!orientation.equals(preferences.getString(PREF_APP_ORIENTATION, "auto"))) {
                    preferences.edit().putString(PREF_APP_ORIENTATION, orientation).apply();
                    if (getActivity() instanceof MainActivity activity) {
                        activity.applyConfiguredOrientation();
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        initCustomApiKeySettings(view);

        // Initialize the cursor lock checkbox
        cbCursorLock = view.findViewById(R.id.CBCursorLock);
        cbCursorLock.setChecked(preferences.getBoolean("cursor_lock", true));
        CheckBox cbMoveCursorToTouchpoint = view.findViewById(R.id.CBMoveCursorToTouchpoint);
        cbMoveCursorToTouchpoint.setChecked(preferences.getBoolean("move_cursor_to_touchpoint", false));
        cbMoveCursorToTouchpoint.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean("move_cursor_to_touchpoint", checked).apply());

        // Initialize the xinput toggle checkbox
        cbXinputToggle = view.findViewById(R.id.CBXinputToggle);
        cbXinputToggle.setChecked(preferences.getBoolean("xinput_toggle", false));

        Spinner sTouchControlsEngine = view.findViewById(R.id.STouchControlsEngine);
        sTouchControlsEngine.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        String initialEngine = InputControlsEngine.getSelected(context);
        sTouchControlsEngine.setSelection(InputControlsEngine.indexOf(initialEngine), false);
        sTouchControlsEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private String selectedEngine = initialEngine;

            @Override
            public void onItemSelected(AdapterView<?> parent, View itemView, int position, long id) {
                String engine = InputControlsEngine.valueAt(context, position);
                if (engine.equals(selectedEngine)) return;
                if (!InputControlsManager.copyProfilesForEngine(context, engine)) {
                    sTouchControlsEngine.setSelection(InputControlsEngine.indexOf(selectedEngine), false);
                    AppUtils.showToast(context, "Could not copy control profiles; engine was not changed.");
                    return;
                }
                selectedEngine = engine;
                preferences.edit().putString(InputControlsEngine.PREFERENCE_KEY, engine).apply();
                AppUtils.showToast(context, R.string.touch_controls_engine_restart);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        Button btnChooseWinlatorPath = view.findViewById(R.id.BTChooseWinlatorPath);
        TextView tvWinlatorPath = view.findViewById(R.id.TVWinlatorPath);

        String savedUriString = preferences.getString("winlator_path_uri", null);
        if (savedUriString == null) {
            // No saved path, set default path
            tvWinlatorPath.setText(DEFAULT_WINLATOR_PATH);
        } else {
            // Parse and display the saved URI path
            Uri savedUri = Uri.parse(savedUriString);
            String displayPath = FileUtils.getFilePathFromUri(getContext(), savedUri);
            tvWinlatorPath.setText(displayPath != null ? displayPath : savedUriString);
        }

        btnChooseWinlatorPath.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); // Launch File Picker for directory selection
            startActivityForResult(intent, REQUEST_CODE_WINLATOR_PATH);
        });

        Button btChooseShortcutExportPath = view.findViewById(R.id.BTChooseShortcutExportPath);
        TextView tvShortcutExportPath = view.findViewById(R.id.TVShortcutExportPath);

        savedUriString = preferences.getString("shortcuts_export_path_uri", null);

        if (savedUriString != null) {
            Uri savedUri = Uri.parse(savedUriString);
            String displayPath = FileUtils.getFilePathFromUri(context, savedUri);
            tvShortcutExportPath.setText(displayPath != null ? displayPath : savedUriString);
        }
        else {
            tvShortcutExportPath.setText(DEFAULT_SHORTCUT_EXPORT_PATH);
        }

        btChooseShortcutExportPath.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(intent, REQUEST_CODE_SHORTCUT_EXPORT_PATH);
        });

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        loadBox64PresetSpinners(view, sBox64Preset);

        final Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        loadFEXCorePresetSpinners(view, sFEXCorePreset);

        final Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);

        sMIDISoundFont.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        final View btInstallSF = view.findViewById(R.id.BTInstallSF);
        final View btRemoveSF = view.findViewById(R.id.BTRemoveSF);

        MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
        btInstallSF.setOnClickListener(v -> {
            installSoundFontCallback = uri -> {
                PreloaderDialog dialog = new PreloaderDialog(requireActivity());
                dialog.showOnUiThread(R.string.installing_content);
                MidiManager.installSF2File(context, uri, new MidiManager.OnSoundFontInstalledCallback() {
                    @Override
                    public void onSuccess() {
                        dialog.closeOnUiThread();
                        requireActivity().runOnUiThread(() -> {
                            ContentDialog.alert(context, R.string.sound_font_installed_success, null);
                            MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
                        });
                    }

                    @Override
                    public void onFailed(int reason) {
                        dialog.closeOnUiThread();
                        int resId = switch (reason) {
                            case MidiManager.ERROR_BADFORMAT -> R.string.sound_font_bad_format;
                            case MidiManager.ERROR_EXIST -> R.string.sound_font_already_exist;
                            default -> R.string.sound_font_installed_failed;
                        };
                        requireActivity().runOnUiThread(() -> ContentDialog.alert(context, resId, null));
                    }
                });
            };

            // Open the file picker with the request code for SoundFont installation
            openFile(REQUEST_CODE_INSTALL_SOUNDFONT);
        });

        btRemoveSF.setOnClickListener(v -> {
            if (sMIDISoundFont.getSelectedItemPosition() != 0) {
                ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_sound_font, () -> {
                    if (MidiManager.removeSF2File(context, sMIDISoundFont.getSelectedItem().toString())) {
                        AppUtils.showToast(context, R.string.sound_font_removed_success);
                        MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
                    } else
                        AppUtils.showToast(context, R.string.sound_font_removed_failed);
                });
            } else
                AppUtils.showToast(context, R.string.cannot_remove_default_sound_font);
        });

        final CheckBox cbUseDRI3 = view.findViewById(R.id.CBUseDRI3);
        cbUseDRI3.setChecked(preferences.getBoolean("use_dri3", true));

        View openLogManager = view.findViewById(R.id.LLOpenLogManager);
        openLogManager.setOnClickListener(v -> getParentFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_up, R.anim.slide_out_down, R.anim.slide_in_down, R.anim.slide_out_up)
                .addToBackStack(null)
                .replace(R.id.FLFragmentContainer, new LogManagerFragment())
                .commit());
        TextView tvLogSummary = view.findViewById(R.id.TVLogManagerSummary);
        boolean recording = com.winlator.cmod.logs.LogFiles.isOn(context, "enable_wine_debug")
                || com.winlator.cmod.logs.LogFiles.isOn(context, "enable_box64_logs");
        tvLogSummary.setText((!com.winlator.cmod.logs.LogFiles.isOn(context, com.winlator.cmod.logs.LogFiles.PREF_ENABLED)
                ? "Logs off" : recording ? "Recording game logs" : "Game logs off")
                + "  |  " + com.winlator.cmod.logs.LogFiles.root(context).getAbsolutePath());

        final TextView tvCursorSpeed = view.findViewById(R.id.TVCursorSpeed);
        final SeekBar sbCursorSpeed = view.findViewById(R.id.SBCursorSpeed);
        sbCursorSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCursorSpeed.setText(progress+"%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbCursorSpeed.setProgress((int)(preferences.getFloat("cursor_speed", 1.0f) * 100));

        final CheckBox cbEnableFileProvider = view.findViewById(R.id.CBEnableFileProvider);
        final View btHelpFileProvider = view.findViewById(R.id.BTHelpFileProvider);

        cbEnableFileProvider.setChecked(preferences.getBoolean("enable_file_provider", true));
        cbEnableFileProvider.setOnClickListener(v -> AppUtils.showToast(context, R.string.take_effect_next_startup));
        btHelpFileProvider.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_file_provider));

        final CheckBox cbOpenInBrowser = view.findViewById(R.id.CBOpenWithAndroidBrowser);
        cbOpenInBrowser.setChecked(preferences.getBoolean("open_with_android_browser", false));

        final CheckBox cbShareClipboard = view.findViewById(R.id.CBShareAndroidClipboard);
        cbShareClipboard.setChecked(preferences.getBoolean("share_android_clipboard", false));

        final EditText etDownloadableContentsURL = view.findViewById(R.id.ETDownloadableContentsURL);
        String savedCatalogUrl = preferences.getString("downloadable_contents_url", ContentsManager.BANNERLATOR_PROFILES);
        if (savedCatalogUrl.contains("gitlab") || savedCatalogUrl.contains("Sd/")) {
            savedCatalogUrl = ContentsManager.BANNERLATOR_PROFILES;
        }
        etDownloadableContentsURL.setText(savedCatalogUrl);

        final Spinner sCatalogPreset = view.findViewById(R.id.SCatalogPreset);
        if (sCatalogPreset != null) {
            String[] presets = {
                "☁️ Bannerlator (The412Banner)",
                "☁️ WinNative (nicholasx417)",
                "☁️ StevenMXZ Contents",
                "🌐 Custom Catalog URL"
            };
            boolean isDarkMode = ThemeManager.isDarkMode(context);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, presets) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);
                    }
                    return v;
                }

                @Override
                public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = super.getDropDownView(position, convertView, parent);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(isDarkMode ? Color.WHITE : Color.BLACK);
                        v.setBackgroundColor(isDarkMode ? 0xFF2A2A2A : 0xFFFFFFFF);
                    }
                    return v;
                }
            };
            sCatalogPreset.setAdapter(adapter);
            sCatalogPreset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

            if (savedCatalogUrl.equals(ContentsManager.BANNERLATOR_PROFILES)) {
                sCatalogPreset.setSelection(0, false);
            } else if (savedCatalogUrl.equals(ContentsManager.WINNATIVE_PROFILES)) {
                sCatalogPreset.setSelection(1, false);
            } else if (savedCatalogUrl.equals(ContentsManager.STEVENMXZ_PROFILES)) {
                sCatalogPreset.setSelection(2, false);
            } else {
                sCatalogPreset.setSelection(3, false);
            }

            sCatalogPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                    switch (position) {
                        case 0 -> etDownloadableContentsURL.setText(ContentsManager.BANNERLATOR_PROFILES);
                        case 1 -> etDownloadableContentsURL.setText(ContentsManager.WINNATIVE_PROFILES);
                        case 2 -> etDownloadableContentsURL.setText(ContentsManager.STEVENMXZ_PROFILES);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        final CheckBox cbGameModeSignal = view.findViewById(R.id.CBGameModeSignal);
        final CheckBox cbThreadPriorityBoost = view.findViewById(R.id.CBThreadPriorityBoost);
        final CheckBox cbPreferBigCores = view.findViewById(R.id.CBPreferBigCores);
        final CheckBox cbHighRefreshRate = view.findViewById(R.id.CBHighRefreshRate);
        final CheckBox cbSustainedPerformance = view.findViewById(R.id.CBSustainedPerformance);
        final CheckBox cbSamsungBoost = view.findViewById(R.id.CBSamsungBoost);
        final View llSamsungBoost = view.findViewById(R.id.LLSamsungBoost);

        if (cbGameModeSignal != null) cbGameModeSignal.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_GAME_MODE_SIGNAL, true));
        if (cbThreadPriorityBoost != null) cbThreadPriorityBoost.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_THREAD_PRIORITY_BOOST, true));
        if (cbPreferBigCores != null) cbPreferBigCores.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_PREFER_BIG_CORES, false));
        if (cbHighRefreshRate != null) cbHighRefreshRate.setChecked(preferences.getBoolean("high_refresh_rate_mode", false));
        if (cbSustainedPerformance != null) cbSustainedPerformance.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_SUSTAINED_PERFORMANCE, false));
        if (cbSamsungBoost != null) cbSamsungBoost.setChecked(preferences.getBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_SAMSUNG_PERF_BOOST, true));
        if (llSamsungBoost != null) {
            llSamsungBoost.setVisibility(com.winlator.cmod.perf.SamsungSPerfDriver.isSamsungDevice() ? View.VISIBLE : View.GONE);
        }

        view.findViewById(R.id.BTReInstallImagefs).setOnClickListener(v -> {
            ContentDialog.confirm(context, R.string.do_you_want_to_reinstall_imagefs, () -> ImageFsInstaller.installFromAssets((MainActivity) getActivity()));
        });

        view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
            if (rgSteamGridApiKeyMode.getCheckedRadioButtonId() == R.id.RBSteamGridApiKeyManual
                    && etCustomApiKey.getText().toString().trim().isEmpty()) {
                AppUtils.showToast(context, "Enter your SteamGridDB API key or choose Default.");
                return;
            }
            SharedPreferences.Editor editor = preferences.edit();

            editor.putBoolean("dark_mode", true);
            editor.putString("box64_preset", Box64PresetManager.getSpinnerSelectedId(sBox64Preset));
            editor.putString("fexcore_preset", FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
            editor.putBoolean("use_dri3", cbUseDRI3.isChecked());
            editor.putFloat("cursor_speed", sbCursorSpeed.getProgress() / 100.0f);
            editor.putBoolean("cursor_lock", cbCursorLock.isChecked()); // Save cursor lock state
            editor.putBoolean("move_cursor_to_touchpoint", cbMoveCursorToTouchpoint.isChecked());
            editor.putBoolean("xinput_toggle", cbXinputToggle.isChecked()); // Save xinput toggle state
            editor.putBoolean("enable_file_provider", cbEnableFileProvider.isChecked());
            editor.putBoolean("open_with_android_browser", cbOpenInBrowser.isChecked());
            editor.putBoolean("share_android_clipboard", cbShareClipboard.isChecked());
            editor.putBoolean(PREF_STARTUP_ANIMATION_ENABLED, !cbDisableBootAnimation.isChecked());
            editor.putBoolean(PREF_UI_SOUND_EFFECTS_ENABLED, cbSoundEffects.isChecked());
            editor.putInt(PREF_UI_SOUND_EFFECTS_VOLUME, sbSoundEffectsVolume.getProgress());
            editor.putBoolean(PREF_LAUNCH_SHORTCUTS_ON_DEFAULT, cbLaunchOnDefault.isChecked());
            editor.putBoolean(PREF_SHORTCUT_VERTICAL_SCROLL,
                    rgShortcutScrollDirection.getCheckedRadioButtonId() == R.id.RBShortcutScrollVertical);
            editor.putInt(PREF_SHORTCUT_VIGNETTE_STRENGTH, sbShortcutVignetteStrength.getProgress());
            editor.putInt(PREF_SHORTCUT_BLUR_STRENGTH, sbShortcutBlurStrength.getProgress());
            if (cbGameModeSignal != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_GAME_MODE_SIGNAL, cbGameModeSignal.isChecked());
            if (cbThreadPriorityBoost != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_THREAD_PRIORITY_BOOST, cbThreadPriorityBoost.isChecked());
            if (cbPreferBigCores != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_PREFER_BIG_CORES, cbPreferBigCores.isChecked());
            if (cbHighRefreshRate != null) editor.putBoolean("high_refresh_rate_mode", cbHighRefreshRate.isChecked());
            if (cbSustainedPerformance != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_SUSTAINED_PERFORMANCE, cbSustainedPerformance.isChecked());
            if (cbSamsungBoost != null) editor.putBoolean(com.winlator.cmod.perf.PerformanceManager.PREF_SAMSUNG_PERF_BOOST, cbSamsungBoost.isChecked());

            editor.putString("downloadable_contents_url", etDownloadableContentsURL.getText().toString());

            saveCustomApiKeySettings(editor);

            if (editor.commit()) {
                ((MainActivity) requireActivity()).openNavigationItem(R.id.main_menu_home);
            }
        });

        return view;
    }

    private void configurePortraitSettingsLayout(View root, Context context) {
        if (getResources().getConfiguration().orientation != android.content.res.Configuration.ORIENTATION_PORTRAIT) return;

        LinearLayout columns = root.findViewById(R.id.LLSettingsColumns);
        FrameLayout navigation = root.findViewById(R.id.FLSettingsCategoryNavigation);
        ScrollView verticalScroll = root.findViewById(R.id.SVSettingsCategoryList);
        LinearLayout categories = root.findViewById(R.id.LLSettingsCategoryList);
        View divider = root.findViewById(R.id.VSettingsCategoryDivider);
        LinearLayout content = root.findViewById(R.id.LLSettingsContent);
        if (columns == null || navigation == null || verticalScroll == null
                || categories == null || divider == null || content == null) return;

        columns.setOrientation(LinearLayout.VERTICAL);
        navigation.removeView(verticalScroll);
        verticalScroll.removeView(categories);
        HorizontalScrollView horizontalScroll = new HorizontalScrollView(context);
        horizontalScroll.setHorizontalScrollBarEnabled(false);
        horizontalScroll.setFillViewport(false);
        horizontalScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        categories.setOrientation(LinearLayout.HORIZONTAL);
        categories.setPadding(dp(context, 8), 0, dp(context, 8), 0);

        int[] categoryIds = {
                R.id.BTSettingsRuntime, R.id.BTSettingsCpu, R.id.BTSettingsDisplay,
                R.id.BTSettingsStorage, R.id.BTSettingsThemes, R.id.BTSettingsControllers
        };
        for (int id : categoryIds) {
            View category = categories.findViewById(id);
            if (category == null) continue;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 48));
            params.setMarginEnd(dp(context, 6));
            category.setLayoutParams(params);
            category.setMinimumWidth(dp(context, 108));
            if (category instanceof TextView) {
                ((TextView) category).setSingleLine(true);
                ((TextView) category).setEllipsize(android.text.TextUtils.TruncateAt.END);
                ((TextView) category).setPadding(dp(context, 12), 0, dp(context, 12), 0);
            }
        }

        horizontalScroll.addView(categories, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        navigation.addView(horizontalScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        navigation.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 58)));
        divider.setVisibility(View.GONE);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        content.setLayoutParams(contentParams);
        content.setPaddingRelative(dp(context, 4), 0, 0, 0);
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void showSettingsCategory(int selectedIndex, View[] categoryButtons, View[] categoryGroups,
                                      TextView categoryTitle, TextView categorySubtitle,
                                      int[] categoryTitles, int[] categorySubtitles) {
        int index = Math.max(0, Math.min(categoryButtons.length - 1, selectedIndex));
        for (int i = 0; i < categoryButtons.length; i++) {
            boolean selected = i == index;
            categoryButtons[i].setSelected(selected);
            ((TextView) categoryButtons[i]).setTextColor(selected ? Color.WHITE : 0xFFC4D1E4);
            categoryGroups[i].setVisibility(selected ? View.VISIBLE : View.GONE);
        }
        categoryTitle.setText(categoryTitles[index]);
        categorySubtitle.setText(categorySubtitles[index]);
    }

    private void updateTheme(boolean isDarkMode) {
        getActivity().setTheme(R.style.AppTheme_Dark);
        ThemeManager.applyTheme(getActivity());
        getActivity().recreate();
    }


    private void applyDynamicStyles(View view, boolean isDarkMode) {

        Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        sBox64Preset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        if (sFEXCorePreset != null) sFEXCorePreset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        com.winlator.cmod.ThemeManager.applyThemeToView(view, getContext());
    }

    private void applyDynamicStylesRecursively(View view) {
        TextView box64Label = view.findViewById(R.id.TVBox64);
        applyFieldSetLabelStyle(box64Label, isDarkMode);

        TextView fexcoreLabel = view.findViewById(R.id.TVFEXCore);
        applyFieldSetLabelStyle(fexcoreLabel, isDarkMode);

        TextView soundLabel = view.findViewById(R.id.TVSound);
        applyFieldSetLabelStyle(soundLabel, isDarkMode);

        TextView homeScreenLabel = view.findViewById(R.id.TVHomeScreen);
        applyFieldSetLabelStyle(homeScreenLabel, isDarkMode);

        TextView shortcutVisualsLabel = view.findViewById(R.id.TVShortcutVisuals);
        applyFieldSetLabelStyle(shortcutVisualsLabel, isDarkMode);

        TextView shortcutSettingsLabel = view.findViewById(R.id.TVShortcutSettings);
        applyFieldSetLabelStyle(shortcutSettingsLabel, isDarkMode);

        TextView tvCustomApiKey = view.findViewById(R.id.TVCustomApiKey);
        applyFieldSetLabelStyle(tvCustomApiKey, isDarkMode);

        // Inputs tab labels
        TextView xServerLabel = view.findViewById(R.id.TVXServer);
        applyFieldSetLabelStyle(xServerLabel, isDarkMode);

        // Advanced tab labels
        TextView logsLabel = view.findViewById(R.id.TVLogs);
        applyFieldSetLabelStyle(logsLabel, isDarkMode);

        TextView experimentalLabel = view.findViewById(R.id.TVExperimental);
        applyFieldSetLabelStyle(experimentalLabel, isDarkMode);

        TextView systemLabel = view.findViewById(R.id.TVSystem);
        applyFieldSetLabelStyle(systemLabel, isDarkMode);

        TextView ImageFsLabel = view.findViewById(R.id.TVImageFs);
        applyFieldSetLabelStyle(ImageFsLabel, isDarkMode);

    }

    private void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
        ThemeManager.applyFieldSetLabelStyle(textView, isDarkMode);
    }

    private void initCustomApiKeySettings(View view) {
        rgSteamGridApiKeyMode = view.findViewById(R.id.RGSteamGridApiKeyMode);
        etCustomApiKey = view.findViewById(R.id.ETCustomApiKey);

        String mode = preferences.getString("steamgrid_api_key_mode", null);
        if (mode == null) {
            mode = preferences.getBoolean("enable_custom_api_key", false) ? "manual" : "default";
        }
        String customApiKey = preferences.getString("custom_api_key", "");

        etCustomApiKey.setText(customApiKey);
        rgSteamGridApiKeyMode.check("manual".equals(mode)
                ? R.id.RBSteamGridApiKeyManual : R.id.RBSteamGridApiKeyDefault);
        updateSteamGridKeyVisibility();
        rgSteamGridApiKeyMode.setOnCheckedChangeListener((group, checkedId) -> updateSteamGridKeyVisibility());

        // Help button listener to open API documentation
        view.findViewById(R.id.BTHelpApiKey).setOnClickListener(v -> {
            String url = "https://www.steamgriddb.com/profile/preferences/api";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
    }

    private void saveCustomApiKeySettings(SharedPreferences.Editor editor) {
        boolean useManualKey = rgSteamGridApiKeyMode.getCheckedRadioButtonId() == R.id.RBSteamGridApiKeyManual;
        editor.putString("steamgrid_api_key_mode", useManualKey ? "manual" : "default");
        editor.putBoolean("enable_custom_api_key", useManualKey);
        editor.putString("custom_api_key", etCustomApiKey.getText().toString().trim());
    }

    private void updateSteamGridKeyVisibility() {
        boolean useManualKey = rgSteamGridApiKeyMode.getCheckedRadioButtonId() == R.id.RBSteamGridApiKeyManual;
        etCustomApiKey.setVisibility(useManualKey ? View.VISIBLE : View.GONE);
    }

    private void loadBox64PresetSpinners(View view, final Spinner sBox64Preset) {
        final ArrayMap<String, Spinner> spinners = new ArrayMap<String, Spinner>() {{
            put("box64", sBox64Preset);
        }};
        final Context context = getContext();

        Callback<String> updateSpinner = (prefix) -> {
            Box64PresetManager.loadSpinner(prefix, spinners.get(prefix), preferences.getString(prefix+"_preset", Box64Preset.COMPATIBILITY));
        };

        Callback<String> onAddPreset = (prefix) -> {
            Box64EditPresetDialog dialog = new Box64EditPresetDialog(context, prefix, null);
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onEditPreset = (prefix) -> {
            Box64EditPresetDialog dialog = new Box64EditPresetDialog(context, prefix, Box64PresetManager.getSpinnerSelectedId(spinners.get(prefix)));
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onDuplicatePreset = (prefix) -> ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset, () -> {
            Spinner spinner = spinners.get(prefix);
            Box64PresetManager.duplicatePreset(prefix, context, Box64PresetManager.getSpinnerSelectedId(spinner));
            updateSpinner.call(prefix);
            spinner.setSelection(spinner.getCount()-1);
        });

        Callback<String> onRemovePreset = (prefix) -> {
            final String presetId = Box64PresetManager.getSpinnerSelectedId(spinners.get(prefix));
            if (!presetId.startsWith(Box64Preset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                Box64PresetManager.removePreset(prefix, context, presetId);
                updateSpinner.call(prefix);
            });
        };

        Callback<String> onExportPreset = (String prefix) -> {
            final String presetId = Box64PresetManager.getSpinnerSelectedId(spinners.get(prefix));
            if (!presetId.startsWith(Box64Preset.CUSTOM)) {
                AppUtils.showToast(context, "Cannot export this preset");
                return;
            }
            getActivity().runOnUiThread(() ->  {
                Box64PresetManager.exportPreset(prefix, context, presetId);
            });
        };

        Callback<String> onImportPreset = (String prefix) -> {
          openFile(REQUEST_CODE_IMPORT_BOX64_PRESET);
        };

        updateSpinner.call("box64");

        view.findViewById(R.id.BTAddBox64Preset).setOnClickListener((v) -> onAddPreset.call("box64"));
        view.findViewById(R.id.BTEditBox64Preset).setOnClickListener((v) -> onEditPreset.call("box64"));
        view.findViewById(R.id.BTDuplicateBox64Preset).setOnClickListener((v) -> onDuplicatePreset.call("box64"));
        view.findViewById(R.id.BTRemoveBox64Preset).setOnClickListener((v) -> onRemovePreset.call("box64"));
        view.findViewById(R.id.BTExportBox64Preset).setOnClickListener((v) -> onExportPreset.call("box64"));
        view.findViewById(R.id.BTImportBox64Preset).setOnClickListener((v) -> onImportPreset.call("box64"));
    }

    private void loadFEXCorePresetSpinners(View view, final Spinner sFEXCorePreset) {
        final Context context = getContext();

        Callback<String> updateSpinner = (String prefix) -> {
            FEXCorePresetManager.loadSpinner(sFEXCorePreset, preferences.getString(prefix + "_preset", FEXCorePreset.COMPATIBILITY));
        };

        Callback<String> onAddPreset = (String prefix) -> {
            FEXCoreEditPresetDialog dialog = new FEXCoreEditPresetDialog(context, null);
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onEditPreset = (prefix) -> {
            FEXCoreEditPresetDialog dialog = new FEXCoreEditPresetDialog(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onDuplicatePreset = (String prefix) -> ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset, () -> {
            FEXCorePresetManager.duplicatePreset(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
            updateSpinner.call(prefix);
            sFEXCorePreset.setSelection(sFEXCorePreset.getCount()-1);
        });

        Callback<String> onRemovePreset = (prefix) -> {
            final String presetId = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
            if (!presetId.startsWith(FEXCorePreset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                FEXCorePresetManager.removePreset(context, presetId);
                updateSpinner.call(prefix);
            });
        };

        Callback<String> onExportPreset = (String prefix) -> {
            final String presetId = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
            if (!presetId.startsWith(FEXCorePreset.CUSTOM)) {
                AppUtils.showToast(context, "Cannot export this preset");
                return;
            }
            getActivity().runOnUiThread(() ->  {
                FEXCorePresetManager.exportPreset(context, presetId);
            });
        };

        Callback<String> onImportPreset = (String prefix) -> {
            openFile(REQUEST_CODE_IMPORT_FEXCORE_PRESET);
        };

        updateSpinner.call("fexcore");

        view.findViewById(R.id.BTAddFEXCorePreset).setOnClickListener((v) -> onAddPreset.call("fexcore"));
        view.findViewById(R.id.BTEditFEXCorePreset).setOnClickListener((v) -> onEditPreset.call("fexcore"));
        view.findViewById(R.id.BTDuplicateFEXCorePreset).setOnClickListener((v) -> onDuplicatePreset.call("fexcore"));
        view.findViewById(R.id.BTRemoveFEXCorePreset).setOnClickListener((v) -> onRemovePreset.call("fexcore"));
        view.findViewById(R.id.BTExportFEXCorePreset).setOnClickListener((v) -> onExportPreset.call("fexcore"));
        view.findViewById(R.id.BTImportFEXCorePreset).setOnClickListener((v) -> onImportPreset.call("fexcore"));
    }


    private void openFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        // Start activity for result based on the provided request code
        getActivity().startActivityFromFragment(this, intent, requestCode);
    }


    public static void resetEmulatorsVersion(AppCompatActivity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("current_box64_version");
        editor.remove("current_wowbox64_version");
        editor.remove("current_fexcore_version");
        editor.apply();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();

            if (uri != null) {

                SharedPreferences.Editor editor = preferences.edit();

                switch (requestCode) {

                    case REQUEST_CODE_WINLATOR_PATH:
                        // Save the selected URI as a string in SharedPreferences
                        editor.putString("winlator_path_uri", uri.toString());
                        editor.apply();

                        // Take persistable URI permission
                        try {
                            // Take persistable URI permission with explicit flags
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            AppUtils.showToast(getContext(), "Unable to take persistable permissions: " + e.getMessage());
                        }

                        // Convert the URI to an absolute path and display it
                        String fullPath = FileUtils.getFilePathFromUri(getContext(), uri);

                        // Update the TextView with the absolute path or URI string if conversion fails
                        TextView tvWinlatorPath = getView().findViewById(R.id.TVWinlatorPath);
                        tvWinlatorPath.setText(fullPath != null ? fullPath : uri.toString());
                        break;

                    case REQUEST_CODE_SHORTCUT_EXPORT_PATH:
                        editor.putString("shortcuts_export_path_uri", uri.toString());
                        editor.apply();

                        // Take persistable URI permission
                        try {
                            // Take persistable URI permission with explicit flags
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            AppUtils.showToast(getContext(), "Unable to take persistable permissions: " + e.getMessage());
                        }

                        // Convert the URI to an absolute path and display it
                        String path = FileUtils.getFilePathFromUri(getContext(), uri);

                        // Update the TextView with the absolute path or URI string if conversion fails
                        TextView tvShortcutExportPath = getView().findViewById(R.id.TVShortcutExportPath);
                        tvShortcutExportPath.setText(path != null ? path : uri.toString());
                        break;

                    // Case for installing a SoundFont
                    case REQUEST_CODE_INSTALL_SOUNDFONT:
                        if (installSoundFontCallback != null) {
                            try {
                                installSoundFontCallback.call(uri);
                            } catch (Exception e) {
                                AppUtils.showToast(getContext(), R.string.unable_to_install_soundfont);
                            } finally {
                                installSoundFontCallback = null;
                            }
                        }
                        break;

                    case REQUEST_CODE_IMPORT_BOX64_PRESET:
                        try {
                            Spinner sBox64Preset = getView().findViewById(R.id.SBox64Preset);
                            InputStream is = getActivity().getContentResolver().openInputStream(uri);
                            Box64PresetManager.importPreset("box64", getContext(), is);
                            Box64PresetManager.loadSpinner("box64", sBox64Preset, preferences.getString("box64_preset", Box64Preset.COMPATIBILITY));
                        } catch (FileNotFoundException e) {
                        }
                        break;
                    case REQUEST_CODE_IMPORT_FEXCORE_PRESET:
                        try {
                            Spinner sFEXCorePreset = getView().findViewById(R.id.SFEXCorePreset);
                            InputStream is = getActivity().getContentResolver().openInputStream(uri);
                            FEXCorePresetManager.importPreset( getContext(), is);
                            FEXCorePresetManager.loadSpinner(sFEXCorePreset, preferences.getString("fexcore_preset", FEXCorePreset.INTERMEDIATE));
                        } catch (FileNotFoundException e) {
                        }
                        break;
                        // Add future cases here for other request codes...
                    default:
                        break;
                }
            }
        }
    }

    private void moveFiles(File sourceDir, File targetDir) throws IOException {
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(targetDir, file.getName());
                if (file.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                    moveFiles(file, targetFile); // Recursively move directory contents
                } else {
                    if (!file.renameTo(targetFile)) {
                        throw new IOException("Failed to move file: " + file.getAbsolutePath());
                    }
                }
            }
        }
        // Clear the temporary directory after moving
        FileUtils.clear(sourceDir);
    }
}
