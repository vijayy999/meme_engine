package com.example.meme_engine.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.meme_engine.BuildConfig;
import com.example.meme_engine.R;
import com.example.meme_engine.util.StorageHelper;
import com.example.meme_engine.util.UpdateManager;

public class SettingsFragment extends Fragment {

    private TextView tvSourcePath;
    private TextView tvDestinationPath;
    private TextView tvBackupPath;

    private ActivityResultLauncher<Intent> sourceFolderPickerLauncher;
    private ActivityResultLauncher<Intent> destinationFolderPickerLauncher;
    private ActivityResultLauncher<Intent> backupFolderPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sourceFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            StorageHelper.setSourceFolderUri(requireContext(), uri);
                            updatePaths();
                        }
                    }
                }
        );

        destinationFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            StorageHelper.setDestinationFolderUri(requireContext(), uri);
                            updatePaths();
                        }
                    }
                }
        );

        backupFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            StorageHelper.setBackupFolderUri(requireContext(), uri);
                            updatePaths();
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvSourcePath = view.findViewById(R.id.tvSettingSourcePath);
        tvDestinationPath = view.findViewById(R.id.tvSettingDestinationPath);
        tvBackupPath = view.findViewById(R.id.tvSettingBackupPath);
        TextView tvAppVersion = view.findViewById(R.id.tvSettingAppVersion);

        LinearLayout llSource = view.findViewById(R.id.llSettingSourceFolder);
        LinearLayout llDestination = view.findViewById(R.id.llSettingDestinationFolder);
        LinearLayout llBackup = view.findViewById(R.id.llSettingBackupFolder);
        LinearLayout llCheckUpdates = view.findViewById(R.id.llSettingCheckUpdates);

        llSource.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            sourceFolderPickerLauncher.launch(intent);
        });

        llDestination.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            destinationFolderPickerLauncher.launch(intent);
        });

        llBackup.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            backupFolderPickerLauncher.launch(intent);
        });

        llCheckUpdates.setOnClickListener(v -> {
            if (getActivity() != null) {
                UpdateManager.checkForUpdates(getActivity(), true);
            }
        });

        // Dynamic version name from BuildConfig (Issue 3 requirement)
        String versionName = BuildConfig.VERSION_NAME != null ? BuildConfig.VERSION_NAME : "1.3";
        tvAppVersion.setText("Current version: v" + versionName);

        updatePaths();
    }

    private void updatePaths() {
        if (!isAdded()) return;

        Uri sourceUri = StorageHelper.getSourceFolderUri(requireContext());
        Uri destUri = StorageHelper.getDestinationFolderUri(requireContext());
        Uri backupUri = StorageHelper.getBackupFolderUri(requireContext());

        tvSourcePath.setText(sourceUri != null ? StorageHelper.getFolderName(requireContext(), sourceUri) : "Not Selected");
        tvDestinationPath.setText(destUri != null ? StorageHelper.getFolderName(requireContext(), destUri) : "Not Selected");
        tvBackupPath.setText(backupUri != null ? StorageHelper.getFolderName(requireContext(), backupUri) : "Not set");
    }
}
