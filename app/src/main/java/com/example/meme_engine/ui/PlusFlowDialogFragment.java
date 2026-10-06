package com.example.meme_engine.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import com.example.meme_engine.R;
import com.example.meme_engine.data.Meme;
import com.example.meme_engine.data.MemeDatabase;
import com.example.meme_engine.util.FileUtil;
import com.example.meme_engine.util.MemeScanner;
import com.example.meme_engine.util.StorageHelper;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlusFlowDialogFragment extends DialogFragment {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Source Tab fields
    private LinearLayout llSourceTab;
    private TextView tvSourceFolderName;
    private EditText etSourceSearch;
    private RecyclerView rvSourceGrid;
    private TextView tvSourceEmpty;
    private MemeAdapter sourceAdapter;
    private List<Uri> currentSourceScannedUris = new ArrayList<>();

    // Destination Tab fields
    private LinearLayout llDestinationTab;
    private TextView tvDestinationFolderName;
    private RecyclerView rvDestinationGrid;
    private TextView tvDestinationEmpty;
    private DestinationAdapter destinationAdapter;
    private LinearLayout llMultiSelectBar;
    private TextView tvSelectedCount;

    private ActivityResultLauncher<Intent> sourceFolderPickerLauncher;
    private ActivityResultLauncher<Intent> destinationFolderPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Meme_engine);

        sourceFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            StorageHelper.setSourceFolderUri(requireContext(), uri);
                            loadSourceTab();
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
                            loadDestinationTab();
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_plus_flow, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View ivCloseDialog = view.findViewById(R.id.ivCloseDialog);
        if (ivCloseDialog != null) {
            ivCloseDialog.setOnClickListener(v -> dismiss());
        }

        TabLayout tabLayout = view.findViewById(R.id.tabLayout);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_source));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_destination));

        llSourceTab = view.findViewById(R.id.llSourceTab);
        tvSourceFolderName = view.findViewById(R.id.tvSourceFolderName);
        Button btnSelectSourceFolder = view.findViewById(R.id.btnSelectSourceFolder);
        etSourceSearch = view.findViewById(R.id.etSourceSearch);
        rvSourceGrid = view.findViewById(R.id.rvSourceGrid);
        tvSourceEmpty = view.findViewById(R.id.tvSourceEmpty);

        llDestinationTab = view.findViewById(R.id.llDestinationTab);
        tvDestinationFolderName = view.findViewById(R.id.tvDestinationFolderName);
        Button btnSelectDestinationFolder = view.findViewById(R.id.btnSelectDestinationFolder);
        rvDestinationGrid = view.findViewById(R.id.rvDestinationGrid);
        tvDestinationEmpty = view.findViewById(R.id.tvDestinationEmpty);
        llMultiSelectBar = view.findViewById(R.id.llMultiSelectBar);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount);
        Button btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected);
        Button btnCancelSelection = view.findViewById(R.id.btnCancelSelection);

        // Set up Source Adapter
        sourceAdapter = new MemeAdapter(this::openTagDialogForUri);
        rvSourceGrid.setAdapter(sourceAdapter);

        btnSelectSourceFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            sourceFolderPickerLauncher.launch(intent);
        });

        etSourceSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSourceSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Set up Destination Adapter
        destinationAdapter = new DestinationAdapter(new DestinationAdapter.DestinationListener() {
            @Override
            public void onDeleteSingle(Uri uri) {
                deleteSingleDestinationFile(uri);
            }

            @Override
            public void onSelectionChanged(int count) {
                if (count > 0) {
                    llMultiSelectBar.setVisibility(View.VISIBLE);
                    tvSelectedCount.setText(count + " Selected");
                } else {
                    llMultiSelectBar.setVisibility(View.GONE);
                }
            }
        });
        rvDestinationGrid.setAdapter(destinationAdapter);

        btnSelectDestinationFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            destinationFolderPickerLauncher.launch(intent);
        });

        btnCancelSelection.setOnClickListener(v -> destinationAdapter.clearSelection());

        btnDeleteSelected.setOnClickListener(v -> deleteSelectedDestinationFiles());

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    llSourceTab.setVisibility(View.VISIBLE);
                    llDestinationTab.setVisibility(View.GONE);
                } else {
                    llSourceTab.setVisibility(View.GONE);
                    llDestinationTab.setVisibility(View.VISIBLE);
                    loadDestinationTab();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadSourceTab();
    }

    private void loadSourceTab() {
        Uri sourceFolder = StorageHelper.getSourceFolderUri(requireContext());
        if (sourceFolder == null) {
            tvSourceFolderName.setText(R.string.no_destination_folder);
            return;
        }

        tvSourceFolderName.setText("Source: " + StorageHelper.getFolderName(requireContext(), sourceFolder));

        MemeScanner.scanFolder(requireContext(), sourceFolder, imageUris -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    currentSourceScannedUris = imageUris;
                    performSourceSearch(etSourceSearch.getText().toString());
                });
            }
        });
    }

    private void performSourceSearch(String query) {
        executorService.execute(() -> {
            List<Uri> displayUris = new ArrayList<>();
            Map<String, String> tagsMap = new HashMap<>();

            Set<String> currentFolderUriStrings = new HashSet<>();
            for (Uri uri : currentSourceScannedUris) {
                currentFolderUriStrings.add(uri.toString());
            }

            if (query == null || query.trim().isEmpty()) {
                // Show all scanned memes in current folder
                for (Uri uri : currentSourceScannedUris) {
                    displayUris.add(uri);
                    Meme meme = MemeDatabase.getDatabase(requireContext()).memeDao().getByUri(uri.toString());
                    if (meme != null && meme.tags != null) {
                        tagsMap.put(uri.toString(), meme.tags);
                    }
                }
            } else {
                // Query database and FILTER results by current folder's scanned URIs
                List<Meme> searchResults = MemeDatabase.getDatabase(requireContext()).memeDao().searchByTag(query.trim());
                if (searchResults != null) {
                    for (Meme meme : searchResults) {
                        if (currentFolderUriStrings.contains(meme.imageUri)) {
                            Uri uri = Uri.parse(meme.imageUri);
                            displayUris.add(uri);
                            tagsMap.put(meme.imageUri, meme.tags);
                        }
                    }
                }
            }

            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    sourceAdapter.setImages(displayUris);
                    sourceAdapter.setTags(tagsMap);
                    tvSourceEmpty.setVisibility(displayUris.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        });
    }

    private void openTagDialogForUri(Uri uri, String existingTags) {
        TagMemeDialogFragment dialog = TagMemeDialogFragment.newInstance(uri, existingTags);
        dialog.setOnTagSavedListener((memeUri, newTags) -> {
            executorService.execute(() -> {
                Meme meme = new Meme(memeUri.toString(), newTags, System.currentTimeMillis());
                MemeDatabase.getDatabase(requireContext()).memeDao().insert(meme);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> performSourceSearch(etSourceSearch.getText().toString()));
                }
            });
        });
        dialog.show(getChildFragmentManager(), "TagMemeDialog");
    }

    private void loadDestinationTab() {
        Uri destFolder = StorageHelper.getDestinationFolderUri(requireContext());
        if (destFolder == null) {
            tvDestinationFolderName.setText(R.string.no_destination_folder);
            destinationAdapter.setImages(new ArrayList<>());
            tvDestinationEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvDestinationFolderName.setText("Destination: " + StorageHelper.getFolderName(requireContext(), destFolder));

        MemeScanner.scanFolder(requireContext(), destFolder, imageUris -> {
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    destinationAdapter.setImages(imageUris);
                    tvDestinationEmpty.setVisibility(imageUris.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        });
    }

    private void deleteSingleDestinationFile(Uri uri) {
        executorService.execute(() -> {
            boolean deleted = FileUtil.deleteFile(requireContext(), uri);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (deleted) {
                        Toast.makeText(requireContext(), "File deleted", Toast.LENGTH_SHORT).show();
                    }
                    loadDestinationTab();
                });
            }
        });
    }

    private void deleteSelectedDestinationFiles() {
        Set<Uri> selected = destinationAdapter.getSelectedUris();
        if (selected.isEmpty()) return;

        executorService.execute(() -> {
            int count = 0;
            for (Uri uri : selected) {
                if (FileUtil.deleteFile(requireContext(), uri)) {
                    count++;
                }
            }
            int finalCount = count;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), finalCount + " file(s) deleted", Toast.LENGTH_SHORT).show();
                    destinationAdapter.clearSelection();
                    loadDestinationTab();
                });
            }
        });
    }
}
