package com.example.meme_engine.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import com.example.meme_engine.R;
import com.example.meme_engine.data.Meme;
import com.example.meme_engine.data.MemeDatabase;
import com.example.meme_engine.util.MemeScanner;
import com.example.meme_engine.util.StorageHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchActivity extends AppCompatActivity {

    private static final String TAG = "SearchActivity";
    private MemeAdapter adapter;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean isPickerMode;
    private List<Uri> currentFolderScannedUris = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        
        isPickerMode = Intent.ACTION_GET_CONTENT.equals(action) || 
                       Intent.ACTION_PICK.equals(action) ||
                       Intent.ACTION_OPEN_DOCUMENT.equals(action) ||
                       "android.provider.action.PICK_IMAGES".equals(action) ||
                       (getComponentName() != null && getComponentName().getShortClassName().contains("MemePickerAlias"));

        RecyclerView rv = findViewById(R.id.rvSearchResults);
        adapter = new MemeAdapter(this::onMemeSelected);
        rv.setAdapter(adapter);

        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadCurrentFolderAndSearch();
    }

    private void loadCurrentFolderAndSearch() {
        Uri sourceFolder = StorageHelper.getSourceFolderUri(this);
        if (sourceFolder == null) return;

        MemeScanner.scanFolder(this, sourceFolder, imageUris -> {
            runOnUiThread(() -> {
                currentFolderScannedUris = imageUris;
                EditText etSearch = findViewById(R.id.etSearch);
                performSearch(etSearch != null ? etSearch.getText().toString() : "");
            });
        });
    }

    private void performSearch(String query) {
        executorService.execute(() -> {
            try {
                List<Uri> displayUris = new ArrayList<>();
                Map<String, String> tagsMap = new HashMap<>();

                Set<String> currentFolderUriStrings = new HashSet<>();
                for (Uri uri : currentFolderScannedUris) {
                    currentFolderUriStrings.add(uri.toString());
                }

                if (query == null || query.trim().isEmpty()) {
                    for (Uri uri : currentFolderScannedUris) {
                        displayUris.add(uri);
                        Meme meme = MemeDatabase.getDatabase(this).memeDao().getByUri(uri.toString());
                        if (meme != null && meme.tags != null) {
                            tagsMap.put(uri.toString(), meme.tags);
                        }
                    }
                } else {
                    List<Meme> allMemes = MemeDatabase.getDatabase(this).memeDao().getAll();
                    if (allMemes != null) {
                        for (Meme meme : allMemes) {
                            if (currentFolderUriStrings.contains(meme.imageUri)) {
                                if (matchesSearch(meme.tags, query.trim())) {
                                    Uri uri = Uri.parse(meme.imageUri);
                                    displayUris.add(uri);
                                    tagsMap.put(meme.imageUri, meme.tags);
                                }
                            }
                        }
                    }
                }

                runOnUiThread(() -> {
                    adapter.setImages(displayUris);
                    adapter.setTags(tagsMap);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error performing search", e);
            }
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

    private void onMemeSelected(Uri uri, String tags) {
        if (isPickerMode) {
            Intent result = new Intent();
            result.setData(uri);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(RESULT_OK, result);
            finish();
        } else {
            TagMemeDialogFragment dialog = TagMemeDialogFragment.newInstance(uri, tags);
            dialog.setOnTagSavedListener((memeUri, newTags) -> {
                executorService.execute(() -> {
                    Meme meme = new Meme(memeUri.toString(), newTags, System.currentTimeMillis());
                    MemeDatabase.getDatabase(this).memeDao().insert(meme);
                    loadCurrentFolderAndSearch();
                });
            });
            dialog.show(getSupportFragmentManager(), "TagMemeDialog");
        }
    }
}
