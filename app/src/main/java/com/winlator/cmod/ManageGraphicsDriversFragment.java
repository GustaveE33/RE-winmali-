package com.winlator.cmod;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.StreamUtils;
import com.winlator.cmod.core.TarCompressorUtils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ManageGraphicsDriversFragment extends Fragment {
    private static final int REQUEST_IMPORT_WRAPPER = 41;
    private String selectedDriverFile;
    private RecyclerView recyclerView;
    private final List<DriverInfo> driverList = new ArrayList<>();

    private static class DriverInfo {
        final String fileName;
        String version = "Unknown";
        String notes = "";
        boolean hasInfo = false;
        boolean isUpdated = false;

        public DriverInfo(String fileName) {
            this.fileName = fileName;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        MenuItem importItem = menu.add(Menu.NONE, 2, Menu.NONE, R.string.import_wrapper);
        importItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        MenuItem resetAllItem = menu.add(Menu.NONE, 1, Menu.NONE, R.string.reset_all);
        resetAllItem.setIcon(R.drawable.ic_reset_all);
        resetAllItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 2) {
            openWrapperPicker();
            return true;
        }
        if (item.getItemId() == 1) {
            ContentDialog.confirm(getContext(), R.string.reset_all_drivers_confirm, () -> {
                File internalDriversDir = new File(getContext().getFilesDir(), "graphics_driver");
                if (internalDriversDir.exists()) {
                    FileUtils.delete(internalDriversDir);
                    AppUtils.showToast(getContext(), R.string.driver_reset_success);
                    loadDrivers();
                }
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openWrapperPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_WRAPPER);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(R.string.manage_graphics_drivers);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.manage_graphics_drivers_fragment, container, false);
        TextView tvDescription = view.findViewById(R.id.TVDescription);
        if (tvDescription != null) {
            String desc = "Manage and update internal graphics driver components. <b>Updated files</b> are stored in internal storage and <b>prioritized</b> over bundled versions.";
            tvDescription.setText(Html.fromHtml(desc, Html.FROM_HTML_MODE_LEGACY));
        }

        recyclerView = view.findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        View importButton = view.findViewById(R.id.BTImportWrapper);
        if (importButton != null) importButton.setOnClickListener(v -> openWrapperPicker());
        else android.util.Log.e("ManageGraphicsDrivers", "Import wrapper button is missing from the manager layout.");

        ThemeManager.applyThemeToView(view, getContext());

        loadDrivers();

        return view;
    }

    private void loadDrivers() {
        final Activity activity = getActivity();
        if (activity == null || recyclerView == null) return;

        final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
        preloaderDialog.showOnUiThread(R.string.loading);

        new Thread(() -> {
            driverList.clear();
            for (String wrapper : com.winlator.cmod.container.Container.WRAPPER_DRIVERS)
                driverList.add(new DriverInfo(wrapper + ".tzst"));
            File customDriverDirectory = new File(activity.getFilesDir(), "graphics_driver");
            File[] importedWrappers = customDriverDirectory.listFiles((dir, name) ->
                    name.startsWith("wrapper-import-") && name.endsWith(".tzst") && new File(dir, name).isFile());
            if (importedWrappers != null) {
                Arrays.sort(importedWrappers, (first, second) -> first.getName().compareToIgnoreCase(second.getName()));
                for (File wrapper : importedWrappers) driverList.add(new DriverInfo(wrapper.getName()));
            }
            driverList.add(new DriverInfo("leegao_bcn.tzst"));
            driverList.add(new DriverInfo("leegao_bcn_legacy.tzst"));
            driverList.add(new DriverInfo("gladio-1.1.tzst"));
            driverList.add(new DriverInfo("extra_libs.tzst"));

            for (DriverInfo info : driverList) {
                fetchDriverInfo(activity, info);
            }

            activity.runOnUiThread(() -> {
                preloaderDialog.closeOnUiThread();
                if (isAdded() && recyclerView != null) {
                    recyclerView.setAdapter(new DriverAdapter(new ArrayList<>(driverList)));
                }
            });
        }).start();
    }

    private void fetchDriverInfo(android.content.Context context, DriverInfo info) {
        File internalFile = new File(context.getFilesDir(), "graphics_driver/" + info.fileName);
        info.isUpdated = internalFile.exists();

        String content;
        if (info.isUpdated) {
            content = TarCompressorUtils.readTextFile(TarCompressorUtils.Type.ZSTD, internalFile, "version.txt");
        } else {
            content = TarCompressorUtils.readTextFile(TarCompressorUtils.Type.ZSTD, context,
                    "graphics_driver/" + info.fileName, "version.txt");
        }

        if (content != null) {
            info.hasInfo = true;
            String[] lines = content.split("\n");
            boolean parsingNotes = false;
            StringBuilder notesBuilder = new StringBuilder();

            for (String line : lines) {
                String trimmedLine = line.trim();
                String lowerLine = trimmedLine.toLowerCase();
                if (lowerLine.startsWith("version:")) {
                    info.version = trimmedLine.substring(8).trim();
                } else if (lowerLine.startsWith("notes:")) {
                    parsingNotes = true;
                    notesBuilder.append(trimmedLine.substring(6).trim());
                } else if (parsingNotes) {
                    if (notesBuilder.length() > 0) notesBuilder.append("\n");
                    notesBuilder.append(trimmedLine);
                }
            }
            info.notes = notesBuilder.toString().trim();
        } else {
            switch (info.fileName) {
                case "wrapper-1.1.tzst":
                    info.version = "1.1 release";
                    info.notes = "The main Vulkan wrapper shipped with Winlator Mali 1.1.";
                    break;
                case "wrapper-v2.tzst":
                    info.version = "v2 (1.1 release)";
                    info.notes = "The v2 build of the Vulkan wrapper from Winlator Mali 1.1.";
                    break;
                case "wrapper-leegao.tzst":
                    info.version = "leegao (1.1 release)";
                    info.notes = "leegao's Vulkan wrapper, as shipped with Winlator Mali 1.1.";
                    break;
                case "wrapper-gamenative.tzst":
                    info.version = "GameNative (1.1 release)";
                    info.notes = "GameNative's lightweight Vulkan wrapper, as shipped with Winlator Mali 1.1.";
                    break;
            }
        }
    }

    private class DriverAdapter extends RecyclerView.Adapter<DriverAdapter.ViewHolder> {
        private final List<DriverInfo> data;

        public DriverAdapter(List<DriverInfo> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.manage_graphics_drivers_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DriverInfo info = data.get(position);
            holder.tvName.setText(info.fileName.replace(".tzst", ""));
            
            android.content.Context ctx = holder.itemView.getContext();
            ThemeManager.applyThemeToView(holder.itemView, ctx);

            String statusStr;
            if (info.isUpdated) {
                int accentColor = ThemeManager.getAccentColor(ctx);
                statusStr = "<font color='" + String.format("#%06X", (0xFFFFFF & accentColor)) + "'><b>" + getString(R.string.updated) + "</b></font>";
            } else {
                statusStr = getString(R.string.bundled);
            }
            holder.tvVersion.setText(Html.fromHtml(getString(R.string.version) + ": " + info.version + " (" + statusStr + ")", Html.FROM_HTML_MODE_LEGACY));

            holder.btInfo.setOnClickListener(v -> {
                String details = "<b>File:</b> " + info.fileName + "<br/>" +
                                 "<b>Version:</b> " + info.version + "<br/><br/>" +
                                 "<b>Notes:</b><br/>" + (info.notes.isEmpty() ? getString(R.string.no_notes_available) : info.notes.replace("\n", "<br/>"));
                
                ContentDialog dialog = new ContentDialog(getContext());
                dialog.setTitle(R.string.driver_details);
                dialog.setIcon(R.drawable.ic_driver_info);
                dialog.setMessage(details);
                dialog.getContentView().setMinimumWidth(AppUtils.getPreferredDialogWidth(getContext()));
                dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
                dialog.show();
            });

            holder.btUpdate.setOnClickListener(v -> {
                String message = getString(R.string.update_driver_initial_confirm, info.fileName);
                ContentDialog.confirm(getContext(), message, () -> {
                    selectedDriverFile = info.fileName;
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    getActivity().startActivityFromFragment(ManageGraphicsDriversFragment.this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);
                });
            });

            holder.btRemove.setVisibility(info.isUpdated ? View.VISIBLE : View.GONE);
            holder.btRemove.setOnClickListener(v -> {
                ContentDialog.confirm(getContext(), R.string.reset_driver_confirm, () -> {
                    File file = new File(getContext().getFilesDir(), "graphics_driver/" + info.fileName);
                    if (file.exists() && file.delete()) {
                        AppUtils.showToast(getContext(), R.string.driver_reset_success);
                        loadDrivers();
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName, tvVersion;
            final ImageButton btInfo, btUpdate, btRemove;
            final ImageView ivIcon;

            ViewHolder(View view) {
                super(view);
                tvName = view.findViewById(R.id.TVName);
                tvVersion = view.findViewById(R.id.TVVersion);
                btInfo = view.findViewById(R.id.BTInfo);
                btUpdate = view.findViewById(R.id.BTUpdate);
                btRemove = view.findViewById(R.id.BTRemove);
                ivIcon = view.findViewById(R.id.IVIcon);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_WRAPPER && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            importWrapper(data.getData());
            return;
        }
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            String fileName = FileUtils.getUriFileName(getContext(), uri);
            if (fileName != null && fileName.equals(selectedDriverFile)) {
                String message = getString(R.string.update_driver_confirm, selectedDriverFile);
                ContentDialog.confirm(getContext(), message, () -> updateDriver(uri));
            } else {
                String errorMsg = fileName != null ? getString(R.string.driver_file_mismatch, fileName, selectedDriverFile) : getString(R.string.unable_to_determine_filename);
                AppUtils.showToast(getContext(), errorMsg);
            }
        }
    }

    private void importWrapper(Uri uri) {
            Activity activity = getActivity();
            if (activity == null) return;
            final PreloaderDialog preloaderDialog = new PreloaderDialog(activity);
            preloaderDialog.showOnUiThread(R.string.importing_wrapper);
            new Thread(() -> {
                File temporaryFile = null;
                String error = null;
                try {
                    String sourceName = FileUtils.getUriFileName(activity, uri);
                    if (sourceName == null) throw new IOException("Unable to determine the selected file name.");
                    String lowerName = sourceName.toLowerCase(Locale.ROOT);
                    if (!lowerName.endsWith(".tzst") && !lowerName.endsWith(".tar.zst")) {
                        throw new IOException("Select a Zstandard-compressed tar archive (.tzst or .tar.zst).");
                    }

                    String baseName = sourceName.replaceFirst("(?i)(\\.tar\\.zst|\\.tzst)$", "")
                            .toLowerCase(Locale.ROOT)
                            .replaceAll("[^a-z0-9._-]+", "-")
                            .replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
                    if (baseName.isEmpty()) baseName = "custom";
                    if (baseName.length() > 40) baseName = baseName.substring(0, 40);

                    File driverDirectory = new File(activity.getFilesDir(), "graphics_driver");
                    if (!driverDirectory.isDirectory() && !driverDirectory.mkdirs()) {
                        throw new IOException("Could not create the wrapper storage directory.");
                    }
                    String driverId = "wrapper-import-" + baseName;
                    File destination = new File(driverDirectory, driverId + ".tzst");
                    int suffix = 2;
                    while (destination.exists()) {
                        driverId = "wrapper-import-" + baseName + "-" + suffix++;
                        destination = new File(driverDirectory, driverId + ".tzst");
                    }
                    temporaryFile = new File(driverDirectory, driverId + ".tmp");
                    try (InputStream input = activity.getContentResolver().openInputStream(uri);
                         FileOutputStream output = new FileOutputStream(temporaryFile)) {
                        if (input == null) throw new IOException("Could not read the selected wrapper archive.");
                        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                        long totalBytes = 0;
                        int length;
                        while ((length = input.read(buffer)) != -1) {
                            totalBytes += length;
                            if (totalBytes > 512L * 1024 * 1024) {
                                throw new IOException("The wrapper archive exceeds the 512 MB import limit.");
                            }
                            output.write(buffer, 0, length);
                        }
                    }
                    String validationError = validateWrapperArchive(temporaryFile);
                    if (validationError != null) throw new IOException(validationError);
                    if (!temporaryFile.renameTo(destination)) {
                        throw new IOException("Could not install the imported wrapper.");
                    }
                    temporaryFile = null;
                } catch (Exception e) {
                    android.util.Log.e("ManageGraphicsDrivers", "Failed to import Vulkan wrapper", e);
                    error = e.getMessage();
                } finally {
                    if (temporaryFile != null && temporaryFile.exists() && !temporaryFile.delete()) {
                        android.util.Log.w("ManageGraphicsDrivers", "Could not remove temporary wrapper archive: " + temporaryFile);
                    }
                }

                String importError = error;
                activity.runOnUiThread(() -> {
                    preloaderDialog.closeOnUiThread();
                    if (!isAdded()) return;
                    if (importError == null) {
                        AppUtils.showToast(getContext(), R.string.wrapper_imported_success);
                        loadDrivers();
                    } else {
                        ContentDialog.alert(getContext(), getString(R.string.wrapper_import_failed, importError), null);
                    }
                });
            }).start();
        }

    private String validateWrapperArchive(File archive) {
            boolean hasEntries = false;
            boolean hasWrapperLibrary = false;
            boolean hasCompatibleIcdManifest = false;
            byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
            try (InputStream fileInput = new BufferedInputStream(new FileInputStream(archive));
                 InputStream zstdInput = new ZstdCompressorInputStream(fileInput);
                 TarArchiveInputStream tarInput = new TarArchiveInputStream(zstdInput)) {
                TarArchiveEntry entry;
                while ((entry = tarInput.getNextTarEntry()) != null) {
                    String entryName = entry.getName().replace('\\', '/');
                    if (!isSafeArchiveEntryName(entryName) || entry.isLink()) {
                        return "The archive contains an unsafe path or unsupported link.";
                    }
                    for (String part : entryName.split("/")) {
                        if ("..".equals(part)) return "The archive contains an unsafe path.";
                    }
                    hasEntries = true;
                    if (entryName.equals("usr/lib/libvulkan_wrapper.so") && entry.isFile() && entry.getSize() > 0) {
                        hasWrapperLibrary = true;
                    } else if (entryName.equals("usr/share/vulkan/icd.d/wrapper_icd.aarch64.json") && entry.isFile()) {
                        try {
                            JSONObject manifest = new JSONObject(new String(
                                    StreamUtils.copyToByteArray(tarInput), StandardCharsets.UTF_8));
                            hasCompatibleIcdManifest = "libvulkan_wrapper.so".equals(
                                    manifest.getJSONObject("ICD").getString("library_path"));
                        } catch (Exception e) {
                            android.util.Log.w("ManageGraphicsDrivers", "Invalid wrapper ICD manifest in " + archive, e);
                        }
                    }
                    while (tarInput.read(buffer) != -1) {
                        // Consume each entry so the complete archive is checked before it is installed.
                    }
                }
            } catch (IOException e) {
                android.util.Log.w("ManageGraphicsDrivers", "Invalid wrapper archive: " + archive, e);
                return "The selected file is not a readable Zstandard tar archive.";
            }
            if (!hasEntries) return "The archive is empty.";
            if (!hasWrapperLibrary || !hasCompatibleIcdManifest) {
                return "This wrapper must contain usr/lib/libvulkan_wrapper.so and "
                        + "usr/share/vulkan/icd.d/wrapper_icd.aarch64.json pointing to that library.";
            }
            return null;
    }

    private boolean isSafeArchiveEntryName(String name) {
            return !name.isEmpty() && !name.startsWith("/") && !name.matches("^[a-zA-Z]:.*");
    }

    private boolean isSafeSymlinkTarget(String entryName, String linkName) {
            if (linkName == null || linkName.isEmpty()) return false;
            String target = linkName.replace('\\', '/');
            if (target.startsWith("/") || target.matches("^[a-zA-Z]:.*")) return false;
            List<String> resolvedParts = new ArrayList<>();
            int lastSeparator = entryName.lastIndexOf('/');
            if (lastSeparator >= 0) {
                for (String part : entryName.substring(0, lastSeparator).split("/")) {
                    if (!part.isEmpty() && !".".equals(part)) resolvedParts.add(part);
                }
            }
            for (String part : target.split("/")) {
                if (part.isEmpty() || ".".equals(part)) continue;
                if ("..".equals(part)) {
                    if (resolvedParts.isEmpty()) return false;
                    resolvedParts.remove(resolvedParts.size() - 1);
                } else {
                    resolvedParts.add(part);
                }
            }
            return !resolvedParts.isEmpty();
    }

    private void updateDriver(Uri uri) {
        final PreloaderDialog preloaderDialog = new PreloaderDialog(getActivity());
        preloaderDialog.showOnUiThread(R.string.updating_system_files);

        new Thread(() -> {
            try {
                File internalDriversDir = new File(getContext().getFilesDir(), "graphics_driver");
                if (!internalDriversDir.exists()) internalDriversDir.mkdirs();

                File finalFile = new File(internalDriversDir, selectedDriverFile);
                try (InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                     FileOutputStream outputStream = new FileOutputStream(finalFile)) {
                    byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                }

                getActivity().runOnUiThread(() -> {
                    preloaderDialog.closeOnUiThread();
                    AppUtils.showToast(getContext(), R.string.driver_updated_success);
                    loadDrivers();
                });
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> {
                    preloaderDialog.closeOnUiThread();
                    AppUtils.showToast(getContext(), R.string.driver_update_failed);
                });
            }
        }).start();
    }
}
