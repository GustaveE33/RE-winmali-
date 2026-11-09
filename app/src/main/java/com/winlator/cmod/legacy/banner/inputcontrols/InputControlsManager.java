package com.winlator.cmod.legacy.banner.inputcontrols;

import android.content.Context;
import android.util.AtomicFile;

import com.winlator.cmod.inputcontrols.InputControlsEngine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class InputControlsManager {
    private InputControlsManager() {}

    static File getProfilesDir(Context context) {
        return com.winlator.cmod.inputcontrols.InputControlsManager.getProfilesDir(
                context, InputControlsEngine.BANNERLATOR);
    }

    static int normalizeLegacyIconId(int iconId) {
        return iconId >= Byte.MIN_VALUE && iconId < 0 ? iconId & 0xFF : iconId;
    }

    static int countCustomIconReferences(Context context, int iconId) {
        File[] files = getProfilesDir(context).listFiles(
                (dir, name) -> name.startsWith("controls-") && name.endsWith(".icp"));
        if (files == null) return -1;

        int references = 0;
        for (File file : files) {
            try {
                JSONObject profile = new JSONObject(readStringAtomically(file));
                JSONArray elements = profile.optJSONArray("elements");
                if (elements == null) return -1;
                for (int i = 0; i < elements.length(); i++) {
                    JSONObject element = elements.optJSONObject(i);
                    if (element == null) return -1;
                    if (element.has("iconId")) {
                        int storedId = element.getInt("iconId");
                        if (normalizeLegacyIconId(storedId) == iconId) references++;
                    }
                }
            }
            catch (IOException | JSONException e) {
                return -1;
            }
        }
        return references;
    }

    static void truncateProfileCombos(JSONObject data) {
        JSONArray elements = data.optJSONArray("elements");
        if (elements == null) return;
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.optJSONObject(i);
            JSONArray combos = element != null ? element.optJSONArray("combos") : null;
            if (combos == null) continue;
            for (int j = 0; j < combos.length(); j++) {
                JSONArray entry = combos.optJSONArray(j);
                JSONArray keys = entry != null ? entry.optJSONArray(1) : null;
                if (keys == null || keys.length() <= ControlElement.MAX_COMBO_BINDINGS) continue;
                JSONArray truncatedKeys = new JSONArray();
                for (int k = 0; k < ControlElement.MAX_COMBO_BINDINGS; k++) {
                    truncatedKeys.put(keys.opt(k));
                }
                try {
                    entry.put(1, truncatedKeys);
                }
                catch (JSONException ignored) {}
            }
        }
    }

    static boolean writeStringAtomically(File file, String value) {
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream outputStream = null;
        try {
            outputStream = atomicFile.startWrite();
            outputStream.write(value.getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(outputStream);
            return true;
        }
        catch (IOException e) {
            if (outputStream != null) atomicFile.failWrite(outputStream);
            return false;
        }
    }

    static String readStringAtomically(File file) throws IOException {
        AtomicFile atomicFile = new AtomicFile(file);
        try (InputStream inputStream = atomicFile.openRead();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, count);
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }
}
