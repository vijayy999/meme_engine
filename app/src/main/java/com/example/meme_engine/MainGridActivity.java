package com.example.meme_engine;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
import com.example.meme_engine.util.FuzzySearchMatcher;
import com.example.meme_engine.util.MemeScanner;
import com.example.meme_engine.util.StorageHelper;
import com.example.meme_engine.util.UpdateManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainGridActivity extends AppCompatActivity {

    private static final String PERF_TAG = "PerformanceTest";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Source Tab fields
    private LinearLayout llSourceTab;
    private TextView tvSourceFolderName;
    private EditText etSourceSearch;
    private ChipGroup chipGroupFilter;
    private Chip chipRefresh;
    private RecyclerView rvSourceGrid;
    private TextView tvSourceEmpty;
    private MemeAdapter sourceAdapter;
    private List<Uri> currentSourceScannedUris = new ArrayList<>();
    private String currentChipFilter = "All"; // Default is "All" every time app opens (not persisted)
    private int latestRefreshScanId = 0;

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

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_check_updates) {
                UpdateManager.checkForUpdates(this, true);
                return true;
            }
            return false;
        });

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
                        // Trigger automatic 24h throttled update check after permission flow
                        UpdateManager.checkAutomaticUpdate(this);
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
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        chipRefresh = findViewById(R.id.chipRefresh);
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

        // WHY: Re-filter instantly on search text change while keeping full memory list intact
        etSourceSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // WHY: Handle chip selection changes ("All", "Tagged", "Untagged") ensuring single selection and applying filters immediately
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipTagged)) {
                currentChipFilter = "Tagged";
            } else if (checkedIds.contains(R.id.chipUntagged)) {
                currentChipFilter = "Untagged";
            } else {
                currentChipFilter = "All";
            }
            applyFilters();
        });

        if (chipRefresh != null) {
            chipRefresh.setOnClickListener(v -> performRefresh());
        }

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    private void checkPermissionAndLoad() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadSourceTab();
            // Trigger automatic background update check after permission & load flow
            UpdateManager.checkAutomaticUpdate(this);
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

        final long totalStartTime = System.nanoTime();

        MemeScanner.scanFolder(this, sourceFolder, imageUris -> {
            executorService.execute(() -> {
                // (b) Single batch DB fetch
                long dbStart = System.nanoTime();
                List<Meme> dbMemes = MemeDatabase.getDatabase(this).memeDao().getAll();
                Map<String, Meme> memeMap = new HashMap<>();
                if (dbMemes != null) {
                    for (Meme m : dbMemes) {
                        memeMap.put(m.imageUri, m);
                    }
                }
                long dbTimeMs = (System.nanoTime() - dbStart) / 1_000_000;
                Log.d(PERF_TAG, "(b) getAll() and map building completed in " + dbTimeMs + " ms. Map size: " + memeMap.size());

                // (c) Batch insert missing URIs
                long insertStart = System.nanoTime();
                long now = System.currentTimeMillis();
                List<Meme> newMemesToInsert = new ArrayList<>();

                for (Uri uri : imageUris) {
                    String uriStr = uri.toString();
                    if (!memeMap.containsKey(uriStr)) {
                        Meme newMeme = new Meme(uriStr, null, now, now);
                        newMemesToInsert.add(newMeme);
                        memeMap.put(uriStr, newMeme); // Update in-memory map
                    }
                }

                if (!newMemesToInsert.isEmpty()) {
                    MemeDatabase.getDatabase(this).memeDao().insertAll(newMemesToInsert);
                }
                long insertTimeMs = (System.nanoTime() - insertStart) / 1_000_000;
                Log.d(PERF_TAG, "(c) Batch insert completed in " + insertTimeMs + " ms. Inserted " + newMemesToInsert.size() + " new memes.");

                currentSourceScannedUris = imageUris;

                // Apply filters with in-memory map
                applyFiltersWithMap(memeMap, totalStartTime);
            });
        });
    }

    private void performRefresh() {
        Uri sourceFolder = StorageHelper.getSourceFolderUri(this);
        if (sourceFolder == null) {
            Toast.makeText(this, "Select a source folder first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (chipRefresh != null) {
            chipRefresh.setEnabled(false);
        }

        final int scanId = ++latestRefreshScanId;
        final long refreshStart = System.nanoTime();

        MemeScanner.scanFolder(this, sourceFolder, imageUris -> {
            executorService.execute(() -> {
                if (scanId != latestRefreshScanId) {
                    return;
                }

                List<Meme> dbMemes = MemeDatabase.getDatabase(this).memeDao().getAll();
                Map<String, Meme> memeMap = new HashMap<>();
                if (dbMemes != null) {
                    for (Meme m : dbMemes) {
                        memeMap.put(m.imageUri, m);
                    }
                }

                List<Uri> oldUris = currentSourceScannedUris != null ? currentSourceScannedUris : new ArrayList<>();
                Set<String> oldUriSet = new HashSet<>();
                for (Uri u : oldUris) {
                    oldUriSet.add(u.toString());
                }

                List<Uri> newScannedUris = imageUris != null ? imageUris : new ArrayList<>();
                Set<String> newUriSet = new HashSet<>();
                for (Uri u : newScannedUris) {
                    newUriSet.add(u.toString());
                }

                int newCount = 0;
                long now = System.currentTimeMillis();
                List<Meme> newMemesToInsert = new ArrayList<>();

                for (Uri u : newScannedUris) {
                    String uriStr = u.toString();
                    if (!oldUriSet.contains(uriStr)) {
                        newCount++;
                    }
                    if (!memeMap.containsKey(uriStr)) {
                        Meme newMeme = new Meme(uriStr, null, now, now);
                        newMemesToInsert.add(newMeme);
                        memeMap.put(uriStr, newMeme);
                    }
                }

                int removedCount = 0;
                for (Uri u : oldUris) {
                    if (!newUriSet.contains(u.toString())) {
                        removedCount++;
                    }
                }

                if (!newMemesToInsert.isEmpty()) {
                    MemeDatabase.getDatabase(this).memeDao().insertAll(newMemesToInsert);
                }

                boolean hasChanged = (newCount > 0 || removedCount > 0 || newScannedUris.size() != oldUris.size());
                long elapsedMs = (System.nanoTime() - refreshStart) / 1_000_000;

                Log.d(PERF_TAG, "Refresh completed in " + elapsedMs + " ms. " + newCount + " new, " + removedCount + " removed.");

                String toastMessage;
                if (!hasChanged) {
                    toastMessage = "No new images";
                } else if (newCount > 0 && removedCount == 0) {
                    toastMessage = newCount + " new image(s) found";
                } else if (newCount == 0 && removedCount > 0) {
                    toastMessage = removedCount + " image(s) removed";
                } else {
                    toastMessage = newCount + " new, " + removedCount + " removed";
                }

                runOnUiThread(() -> {
                    if (chipRefresh != null) {
                        chipRefresh.setEnabled(true);
                    }
                    Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();

                    if (hasChanged && scanId == latestRefreshScanId) {
                        currentSourceScannedUris = newScannedUris;
                        applyFiltersWithMap(memeMap, refreshStart);
                    }
                });
            });
        });
    }

    /**
     * UNIFIED FILTER METHOD: Combines chip selection ("All", "Tagged", "Untagged") and fuzzy search query,
     * sorts results by relevance score descending and firstSeen descending, and updates the adapter.
     */
    private void applyFilters() {
        executorService.execute(() -> {
            List<Meme> dbMemes = MemeDatabase.getDatabase(this).memeDao().getAll();
            Map<String, Meme> memeMap = new HashMap<>();
            if (dbMemes != null) {
                for (Meme m : dbMemes) {
                    memeMap.put(m.imageUri, m);
                }
            }
            applyFiltersWithMap(memeMap, System.nanoTime());
        });
    }

    private void applyFiltersWithMap(Map<String, Meme> memeMap, long totalStartTime) {
        List<Uri> displayUris = new ArrayList<>();
        Map<String, String> tagsMap = new HashMap<>();
        Map<Uri, Long> firstSeenMap = new HashMap<>();
        Map<Uri, Integer> matchScoreMap = new HashMap<>();

        if (currentSourceScannedUris == null || currentSourceScannedUris.isEmpty()) {
            runOnUiThread(() -> {
                sourceAdapter.setImages(new ArrayList<>());
                sourceAdapter.setTags(new HashMap<>());
                tvSourceEmpty.setVisibility(View.VISIBLE);

                long totalTimeMs = (System.nanoTime() - totalStartTime) / 1_000_000;
                Log.d(PERF_TAG, "(d) Total time until grid is shown: " + totalTimeMs + " ms.");
            });
            return;
        }

        String query = etSourceSearch != null && etSourceSearch.getText() != null
                ? etSourceSearch.getText().toString().trim()
                : "";

        boolean isSearchActive = !query.isEmpty();

        for (Uri uri : currentSourceScannedUris) {
            String uriStr = uri.toString();
            Meme meme = memeMap.get(uriStr);
            String memeTags = meme != null ? meme.tags : null;
            boolean isTagged = memeTags != null && !memeTags.trim().isEmpty();
            long firstSeen = meme != null ? meme.firstSeen : 0L;

            // 1. Evaluate Chip Filter rule
            boolean passesChip = true;
            if ("Tagged".equals(currentChipFilter)) {
                passesChip = isTagged;
            } else if ("Untagged".equals(currentChipFilter)) {
                passesChip = !isTagged;
            }

            // 2. Evaluate Fuzzy Search rule
            boolean passesSearch = true;
            int score = 0;
            if (isSearchActive) {
                FuzzySearchMatcher.MatchResult matchResult = FuzzySearchMatcher.match(memeTags, query);
                passesSearch = matchResult.isMatch;
                score = matchResult.score;
            }

            if (passesChip && passesSearch) {
                displayUris.add(uri);
                if (memeTags != null) {
                    tagsMap.put(uriStr, memeTags);
                }
                firstSeenMap.put(uri, firstSeen);
                matchScoreMap.put(uri, score);
            }
        }

        // 3. Sort displayed list:
        //    - When search is active: best match score descending, then firstSeen descending
        //    - When search box is empty: newest first (firstSeen descending)
        displayUris.sort((u1, u2) -> {
            if (isSearchActive) {
                Integer s1Obj = matchScoreMap.get(u1);
                int s1 = s1Obj != null ? s1Obj : 0;
                Integer s2Obj = matchScoreMap.get(u2);
                int s2 = s2Obj != null ? s2Obj : 0;
                if (s1 != s2) {
                    return Integer.compare(s2, s1); // Higher score first
                }
            }

            Long fs1Obj = firstSeenMap.get(u1);
            long fs1 = fs1Obj != null ? fs1Obj : 0L;
            Long fs2Obj = firstSeenMap.get(u2);
            long fs2 = fs2Obj != null ? fs2Obj : 0L;
            if (fs1 != fs2) {
                return Long.compare(fs2, fs1); // Descending (newest first)
            }
            return u1.toString().compareTo(u2.toString());
        });

        runOnUiThread(() -> {
            sourceAdapter.setImages(displayUris);
            sourceAdapter.setTags(tagsMap);
            tvSourceEmpty.setVisibility(displayUris.isEmpty() ? View.VISIBLE : View.GONE);

            long totalTimeMs = (System.nanoTime() - totalStartTime) / 1_000_000;
            Log.d(PERF_TAG, "(d) Total time until grid is shown: " + totalTimeMs + " ms.");
        });
    }

    private void openTagDialogForUri(Uri uri, String existingTags) {
        TagMemeDialogFragment dialog = TagMemeDialogFragment.newInstance(uri, existingTags);
        dialog.setOnTagSavedListener((memeUri, newTags) -> {
            executorService.execute(() -> {
                // WHY: Preserve original firstSeen timestamp when tagging so tagging does not alter grid position
                Meme existing = MemeDatabase.getDatabase(this).memeDao().getByUri(memeUri.toString());
                long originalFirstSeen = existing != null ? existing.firstSeen : System.currentTimeMillis();
                long dateAdded = existing != null ? existing.dateAdded : System.currentTimeMillis();

                Meme meme = new Meme(memeUri.toString(), newTags, dateAdded, originalFirstSeen);
                MemeDatabase.getDatabase(this).memeDao().insert(meme);
                
                // Re-apply filters immediately on main thread so newly tagged meme disappears if "Untagged" chip is selected
                runOnUiThread(this::applyFilters);
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
