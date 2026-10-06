package com.example.meme_engine;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.meme_engine.data.Meme;
import com.example.meme_engine.data.MemeDatabase;
import com.example.meme_engine.ui.DestinationAdapter;
import com.example.meme_engine.ui.MemeAdapter;
import com.example.meme_engine.ui.SearchActivity;
import com.example.meme_engine.ui.TagMemeDialogFragment;
import com.example.meme_engine.util.FileUtil;
import com.example.meme_engine.util.MemeScanner;
import com.example.meme_engine.util.StorageHelper;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainGridActivity extends AppCompatActivity {

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
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String action = getIntent().getAction();
        if (Intent.ACTION_GET_CONTENT.equals(action) || Intent.ACTION_PICK.equals(action)) {
            Intent searchIntent = new Intent(this, SearchActivity.class);
            searchIntent.setAction(action);
            searchIntent.setType(getIntent().getType());
            searchIntent.putExtras(getIntent());
            startActivityForResult(searchIntent, 1001);
        }

        setSupportActionBar(findViewById(R.id.toolbar));

        sourceFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            StorageHelper.setSourceFolderUri(this, uri);
                            loadSourceTab();
                        }
                    }
                }
        );

        destinationFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            StorageHelper.setDestinationFolderUri(this, uri);
                            loadDestinationTab();
                        }
                    }
                }
        );

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        loadSourceTab();
                    } else {
                        Toast.makeText(this, "Permission denied. Cannot scan memes.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_source));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_destination));

        llSourceTab = findViewById(R.id.llSourceTab);
        tvSourceFolderName = findViewById(R.id.tvSourceFolderName);
        Button btnSelectSourceFolder = findViewById(R.id.btnSelectSourceFolder);
        etSourceSearch = findViewById(R.id.etSourceSearch);
        rvSourceGrid = findViewById(R.id.rvSourceGrid);
        tvSourceEmpty = findViewById(R.id.tvSourceEmpty);

        llDestinationTab = findViewById(R.id.llDestinationTab);
        tvDestinationFolderName = findViewById(R.id.tvDestinationFolderName);
        Button btnSelectDestinationFolder = findViewById(R.id.btnSelectDestinationFolder);
        rvDestinationGrid = findViewById(R.id.rvDestinationGrid);
        tvDestinationEmpty = findViewById(R.id.tvDestinationEmpty);
        llMultiSelectBar = findViewById(R.id.llMultiSelectBar);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        Button btnDeleteSelected = findViewById(R.id.btnDeleteSelected);
        Button btnCancelSelection = findViewById(R.id.btnCancelSelection);

        // Source Adapter
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

        // Destination Adapter
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

        checkPermissionAndLoad();
    }

    private void checkPermissionAndLoad() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadSourceTab();
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private void loadSourceTab() {
        Uri sourceFolder = StorageHelper.getSourceFolderUri(this);
        if (sourceFolder == null) {
            tvSourceFolderName.setText(R.string.no_destination_folder);
            return;
        }

        tvSourceFolderName.setText("Source: " + StorageHelper.getFolderName(this, sourceFolder));

        MemeScanner.scanFolder(this, sourceFolder, imageUris -> {
            runOnUiThread(() -> {
                currentSourceScannedUris = imageUris;
                performSourceSearch(etSourceSearch.getText().toString());
            });
        });
    }

    private void performSourceSearch(String query) {
        executorService.execute(() -> {
            List<Uri> displayUris = new ArrayList<>();
            Map<String, String> tagsMap = new HashMap<>();

            if (currentSourceScannedUris == null || currentSourceScannedUris.isEmpty()) {
                runOnUiThread(() -> {
                    sourceAdapter.setImages(new ArrayList<>());
                    sourceAdapter.setTags(new HashMap<>());
                    tvSourceEmpty.setVisibility(View.VISIBLE);
                });
                return;
            }

            String q = query != null ? query.trim() : "";

            for (Uri uri : currentSourceScannedUris) {
                Meme meme = MemeDatabase.getDatabase(this).memeDao().getByUri(uri.toString());
                String memeTags = meme != null ? meme.tags : null;

                if (q.isEmpty()) {
                    displayUris.add(uri);
                    if (memeTags != null) {
                        tagsMap.put(uri.toString(), memeTags);
                    }
                } else {
                    if (matchesSearch(memeTags, q)) {
                        displayUris.add(uri);
                        if (memeTags != null) {
                            tagsMap.put(uri.toString(), memeTags);
                        }
                    }
                }
            }

            runOnUiThread(() -> {
                sourceAdapter.setImages(displayUris);
                sourceAdapter.setTags(tagsMap);
                tvSourceEmpty.setVisibility(displayUris.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private boolean matchesSearch(String tagsStr, String query) {
        if (tagsStr == null || tagsStr.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return false;
        }

        String q = query.trim().toLowerCase();
        String[] tokens = tagsStr.trim().split("\\s+");

        if (q.startsWith("#")) {
            for (String token : tokens) {
                if (token.startsWith("#")) {
                    String t = token.toLowerCase();
                    if (t.equals(q) || t.contains(q)) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            for (String token : tokens) {
                if (!token.startsWith("#")) {
                    String t = token.toLowerCase();
                    if (t.equals(q) || t.contains(q)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private void openTagDialogForUri(Uri uri, String existingTags) {
        TagMemeDialogFragment dialog = TagMemeDialogFragment.newInstance(uri, existingTags);
        dialog.setOnTagSavedListener((memeUri, newTags) -> {
            executorService.execute(() -> {
                Meme meme = new Meme(memeUri.toString(), newTags, System.currentTimeMillis());
                MemeDatabase.getDatabase(this).memeDao().insert(meme);
                runOnUiThread(() -> performSourceSearch(etSourceSearch.getText().toString()));
            });
        });
        dialog.show(getSupportFragmentManager(), "TagMemeDialog");
    }

    private void loadDestinationTab() {
        Uri destFolder = StorageHelper.getDestinationFolderUri(this);
        if (destFolder == null) {
            tvDestinationFolderName.setText(R.string.no_destination_folder);
            destinationAdapter.setImages(new ArrayList<>());
            tvDestinationEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvDestinationFolderName.setText("Destination: " + StorageHelper.getFolderName(this, destFolder));

        MemeScanner.scanFolder(this, destFolder, imageUris -> {
            runOnUiThread(() -> {
                destinationAdapter.setImages(imageUris);
                tvDestinationEmpty.setVisibility(imageUris.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void deleteSingleDestinationFile(Uri uri) {
        executorService.execute(() -> {
            boolean deleted = FileUtil.deleteFile(this, uri);
            runOnUiThread(() -> {
                if (deleted) {
                    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show();
                }
                loadDestinationTab();
            });
        });
    }

    private void deleteSelectedDestinationFiles() {
        Set<Uri> selected = destinationAdapter.getSelectedUris();
        if (selected.isEmpty()) return;

        executorService.execute(() -> {
            int count = 0;
            for (Uri uri : selected) {
                if (FileUtil.deleteFile(this, uri)) {
                    count++;
                }
            }
            int finalCount = count;
            runOnUiThread(() -> {
                Toast.makeText(this, finalCount + " file(s) deleted", Toast.LENGTH_SHORT).show();
                destinationAdapter.clearSelection();
                loadDestinationTab();
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            setResult(RESULT_OK, data);
            finish();
        }
    }
}
