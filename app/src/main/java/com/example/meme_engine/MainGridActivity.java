package com.example.meme_engine;

import android.Manifest;
import android.content.Context;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.meme_engine.data.CopiedMeme;
import com.example.meme_engine.data.Meme;
import com.example.meme_engine.data.MemeDatabase;
import com.example.meme_engine.ui.DestinationAdapter;
import com.example.meme_engine.ui.MemeAdapter;
import com.example.meme_engine.ui.SearchActivity;
import com.example.meme_engine.ui.SettingsFragment;
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
    private static final long CLEANUP_THRESHOLD_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Source Tab fields
    private LinearLayout llSourceTab;
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

    // Source multi-select UI
    private LinearLayout llSourceMultiSelectBar;
    private TextView tvSourceSelectedCount;

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
            if (item.getItemId() == R.id.action_settings) {
                openSettingsFragment();
                return true;
            }
            return false;
        });

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

        llSourceMultiSelectBar = findViewById(R.id.llSourceMultiSelectBar);
        tvSourceSelectedCount = findViewById(R.id.tvSourceSelectedCount);
        Button btnSourceCancelSelection = findViewById(R.id.btnSourceCancelSelection);
        Button btnSourceDeleteSelected = findViewById(R.id.btnSourceDeleteSelected);

        // Source Adapter
        sourceAdapter = new MemeAdapter(new MemeAdapter.SourceListener() {
            @Override
            public void onMemeClick(Uri uri, String existingTags) {
                openTagDialogForUri(uri, existingTags);
            }

            @Override
            public void onSelectionChanged(int count) {
                if (count > 0) {
                    llSourceMultiSelectBar.setVisibility(View.VISIBLE);
                    tvSourceSelectedCount.setText(count + " Selected");
                } else {
                    llSourceMultiSelectBar.setVisibility(View.GONE);
                }
            }
        });
        rvSourceGrid.setAdapter(sourceAdapter);

        btnSourceCancelSelection.setOnClickListener(v -> sourceAdapter.clearSelection());
        btnSourceDeleteSelected.setOnClickListener(v -> confirmDeleteSourceMemes());

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

        // Modern Back Press Handling (supports closing SettingsFragment if open)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // If SettingsFragment is open, pop it first
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                    return;
                }

                // 1. Check if Source tab multi-select is active
                if (llSourceTab != null && llSourceTab.getVisibility() == View.VISIBLE
                        && sourceAdapter != null && !sourceAdapter.getSelectedUris().isEmpty()) {
                    sourceAdapter.clearSelection();
                    return;
                }

                // 2. Check if Destination tab multi-select is active
                if (llDestinationTab != null && llDestinationTab.getVisibility() == View.VISIBLE
                        && destinationAdapter != null && !destinationAdapter.getSelectedUris().isEmpty()) {
                    destinationAdapter.clearSelection();
                    return;
                }

                // 3. Check if Search box contains text
                if (etSourceSearch != null && etSourceSearch.getText() != null
                        && !etSourceSearch.getText().toString().trim().isEmpty()) {
                    etSourceSearch.setText("");
                    etSourceSearch.clearFocus();

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(etSourceSearch.getWindowToken(), 0);
                    }
                    return;
                }

                // 3. Otherwise, pass back press to default system handling (exit app)
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            View container = findViewById(R.id.fragmentContainer);
            if (container != null) {
                if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                    container.setVisibility(View.GONE);
                    loadSourceTab();
                    loadDestinationTab();
                }
            }
        });

        checkPermissionAndLoad();
        handleSharedImageIntent(getIntent());
    }

    private void openSettingsFragment() {
        View container = findViewById(R.id.fragmentContainer);
        if (container != null) {
            container.setVisibility(View.VISIBLE);
        }
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right)
                .replace(R.id.fragmentContainer, new SettingsFragment())
                .addToBackStack("settings")
                .commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedImageIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        performDestinationAutoClean();
        loadSourceTab();
        if (llDestinationTab != null && llDestinationTab.getVisibility() == View.VISIBLE) {
            loadDestinationTab();
        }
    }

    private void handleSharedImageIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
            // Clear action so intent isn't re-processed on orientation changes
            intent.setAction(null);

            Uri sharedImageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (sharedImageUri == null) return;

            Uri sourceFolderUri = StorageHelper.getSourceFolderUri(this);
            if (sourceFolderUri == null) {
                Toast.makeText(this, "Select a source folder first", Toast.LENGTH_LONG).show();
                return;
            }

            executorService.execute(() -> {
                Uri newCopiedSourceUri = FileUtil.copyFileAndGetUri(this, sharedImageUri, sourceFolderUri);
                runOnUiThread(() -> {
                    if (newCopiedSourceUri != null) {
                        loadSourceTab();
                        openTagDialogForUri(newCopiedSourceUri, "");
                    } else {
                        Toast.makeText(this, "Could not save shared image", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null && type.startsWith("image/")) {
            intent.setAction(null);

            ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (imageUris == null || imageUris.isEmpty()) return;

            Uri sourceFolderUri = StorageHelper.getSourceFolderUri(this);
            if (sourceFolderUri == null) {
                Toast.makeText(this, "Select a source folder first", Toast.LENGTH_LONG).show();
                return;
            }

            processBatchSharedImages(imageUris, 0, new int[]{0}, sourceFolderUri);
        }
    }

    private void processBatchSharedImages(List<Uri> imageUris, int index, int[] copiedCountHolder, Uri sourceFolderUri) {
        if (index >= imageUris.size()) {
            finishBatchShare(copiedCountHolder[0]);
            return;
        }

        Uri currentUri = imageUris.get(index);
        if (currentUri == null) {
            processBatchSharedImages(imageUris, index + 1, copiedCountHolder, sourceFolderUri);
            return;
        }

        executorService.execute(() -> {
            Uri newCopiedSourceUri = FileUtil.copyFileAndGetUri(this, currentUri, sourceFolderUri);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                if (newCopiedSourceUri != null) {
                    copiedCountHolder[0]++;
                    loadSourceTab();

                    TagMemeDialogFragment dialog = TagMemeDialogFragment.newBatchInstance(newCopiedSourceUri, index + 1, imageUris.size());
                    dialog.setOnBatchListener(new TagMemeDialogFragment.OnBatchListener() {
                        @Override
                        public void onOk(Uri uri, String tags) {
                            if (tags != null && !tags.trim().isEmpty()) {
                                executorService.execute(() -> {
                                    long now = System.currentTimeMillis();
                                    Meme meme = new Meme(uri.toString(), tags, now, now);
                                    MemeDatabase.getDatabase(MainGridActivity.this).memeDao().insert(meme);
                                    runOnUiThread(() -> {
                                        applyFilters();
                                        processBatchSharedImages(imageUris, index + 1, copiedCountHolder, sourceFolderUri);
                                    });
                                });
                            } else {
                                processBatchSharedImages(imageUris, index + 1, copiedCountHolder, sourceFolderUri);
                            }
                        }

                        @Override
                        public void onSkip(Uri uri) {
                            processBatchSharedImages(imageUris, index + 1, copiedCountHolder, sourceFolderUri);
                        }

                        @Override
                        public void onCancelAll() {
                            finishBatchShare(copiedCountHolder[0]);
                        }
                    });
                    dialog.show(getSupportFragmentManager(), "BatchTagMemeDialog");
                } else {
                    processBatchSharedImages(imageUris, index + 1, copiedCountHolder, sourceFolderUri);
                }
            });
        });
    }

    private void finishBatchShare(int copiedCount) {
        loadSourceTab();
        Toast.makeText(this, copiedCount + " memes added to source folder", Toast.LENGTH_SHORT).show();
    }

    private void performDestinationAutoClean() {
        Uri sourceFolderUri = StorageHelper.getSourceFolderUri(this);
        Uri destFolderUri = StorageHelper.getDestinationFolderUri(this);

        if (destFolderUri == null) return;

        // Skip cleanup completely if source and destination folders are the same
        if (sourceFolderUri != null && sourceFolderUri.toString().equals(destFolderUri.toString())) {
            return;
        }

        executorService.execute(() -> {
            try {
                long thresholdTime = System.currentTimeMillis() - CLEANUP_THRESHOLD_MS;
                List<CopiedMeme> expiredMemes = MemeDatabase.getDatabase(this).copiedMemeDao().getExpiredCopied(thresholdTime);

                if (expiredMemes == null || expiredMemes.isEmpty()) {
                    return;
                }

                int deletedCount = 0;
                for (CopiedMeme m : expiredMemes) {
                    try {
                        Uri destUri = Uri.parse(m.destImageUri);
                        FileUtil.deleteFile(this, destUri);
                        MemeDatabase.getDatabase(this).copiedMemeDao().deleteByDestUri(m.destImageUri);
                        deletedCount++;
                    } catch (Exception ignored) {}
                }

                if (deletedCount > 0) {
                    runOnUiThread(() -> {
                        if (llDestinationTab != null && llDestinationTab.getVisibility() == View.VISIBLE) {
                            loadDestinationTab();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(PERF_TAG, "Error in destination auto clean", e);
            }
        });
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
            // Trigger automatic background update check after permission flow
            UpdateManager.checkAutomaticUpdate(this);
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private void loadSourceTab() {
        Uri sourceFolder = StorageHelper.getSourceFolderUri(this);
        if (sourceFolder == null) {
            return;
        }

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

    public void loadDestinationTab() {
        Uri destFolder = StorageHelper.getDestinationFolderUri(this);
        if (destFolder == null) {
            tvDestinationFolderName.setText(R.string.no_destination_folder);
            destinationAdapter.setImages(new ArrayList<>());
            tvDestinationEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvDestinationFolderName.setText("Destination: " + StorageHelper.getFolderName(this, destFolder));

        MemeScanner.scanFolder(this, destFolder, imageUris -> {
            executorService.execute(() -> {
                List<CopiedMeme> copiedMemes = MemeDatabase.getDatabase(this).copiedMemeDao().getAll();
                Map<String, Long> timestampMap = new HashMap<>();
                if (copiedMemes != null) {
                    for (CopiedMeme cm : copiedMemes) {
                        timestampMap.put(cm.destImageUri, cm.copyTimestamp);
                    }
                }

                imageUris.sort((u1, u2) -> {
                    Long t1 = timestampMap.get(u1.toString());
                    Long t2 = timestampMap.get(u2.toString());

                    if (t1 != null && t2 != null) {
                        return Long.compare(t2, t1); // Descending (newest first)
                    } else if (t1 != null) {
                        return -1; // u1 is tracked, place before untracked u2
                    } else if (t2 != null) {
                        return 1;  // u2 is tracked, place before untracked u1
                    } else {
                        return u1.toString().compareTo(u2.toString());
                    }
                });

                runOnUiThread(() -> {
                    destinationAdapter.setImages(imageUris);
                    tvDestinationEmpty.setVisibility(imageUris.isEmpty() ? View.VISIBLE : View.GONE);
                });
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

    private void confirmDeleteSourceMemes() {
        Set<Uri> selected = sourceAdapter.getSelectedUris();
        if (selected.isEmpty()) return;

        int count = selected.size();
        new AlertDialog.Builder(this)
                .setTitle("Delete Memes")
                .setMessage("Are you sure you want to delete " + count + " meme(s)? This will permanently delete the image(s) from your phone and cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSelectedSourceMemes(selected))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSelectedSourceMemes(Set<Uri> selected) {
        executorService.execute(() -> {
            int deletedCount = 0;
            for (Uri uri : selected) {
                try {
                    boolean success = FileUtil.deleteFile(this, uri);
                    if (success) {
                        deletedCount++;
                        String uriStr = uri.toString();
                        MemeDatabase.getDatabase(this).memeDao().deleteByUri(uriStr);
                        MemeDatabase.getDatabase(this).copiedMemeDao().deleteBySourceUri(uriStr);
                    }
                } catch (Exception e) {
                    Log.e(PERF_TAG, "Error deleting source meme: " + uri, e);
                }
            }

            int finalDeletedCount = deletedCount;
            runOnUiThread(() -> {
                Toast.makeText(this, finalDeletedCount + " meme(s) deleted", Toast.LENGTH_SHORT).show();
                sourceAdapter.clearSelection();
                loadSourceTab();
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
