package com.example.meme_engine.ui;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import com.bumptech.glide.Glide;
import com.example.meme_engine.R;
import com.example.meme_engine.data.CopiedMeme;
import com.example.meme_engine.data.Meme;
import com.example.meme_engine.data.MemeDatabase;
import android.content.Context;
import com.example.meme_engine.util.FileUtil;
import com.example.meme_engine.util.StorageHelper;
import java.util.concurrent.Executors;

public class TagMemeDialogFragment extends DialogFragment {

    private static final String ARG_URI = "uri";
    private static final String ARG_TAGS = "tags";
    private static final String ARG_BATCH_INDEX = "batch_index";
    private static final String ARG_BATCH_TOTAL = "batch_total";

    private OnTagSavedListener listener;
    private OnBatchListener batchListener;

    public interface OnTagSavedListener {
        void onTagSaved(Uri uri, String tags);
    }

    public interface OnBatchListener {
        void onOk(Uri uri, String tags);
        void onSkip(Uri uri);
        void onCancelAll();
    }

    public static TagMemeDialogFragment newInstance(Uri uri, String existingTags) {
        TagMemeDialogFragment fragment = new TagMemeDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, uri);
        args.putString(ARG_TAGS, existingTags);
        fragment.setArguments(args);
        return fragment;
    }

    public static TagMemeDialogFragment newBatchInstance(Uri uri, int currentIndex, int totalCount) {
        TagMemeDialogFragment fragment = new TagMemeDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, uri);
        args.putString(ARG_TAGS, "");
        args.putInt(ARG_BATCH_INDEX, currentIndex);
        args.putInt(ARG_BATCH_TOTAL, totalCount);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnTagSavedListener(OnTagSavedListener listener) {
        this.listener = listener;
    }

    public void setOnBatchListener(OnBatchListener batchListener) {
        this.batchListener = batchListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getArguments() == null) {
            return super.onCreateDialog(savedInstanceState);
        }

        Uri uri = getArguments().getParcelable(ARG_URI);
        String existingTags = getArguments().getString(ARG_TAGS);
        int batchIndex = getArguments().getInt(ARG_BATCH_INDEX, -1);
        int batchTotal = getArguments().getInt(ARG_BATCH_TOTAL, -1);
        boolean isBatch = batchIndex > 0 && batchTotal > 0;

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tag_meme, null);
        ImageView ivPreview = view.findViewById(R.id.ivTagPreview);
        EditText etTags = view.findViewById(R.id.etTags);
        EditText etHashtags = view.findViewById(R.id.etHashtags);
        Button btnSave = view.findViewById(R.id.btnSave);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnDestination = view.findViewById(R.id.btnDestination);

        if (uri != null) {
            Glide.with(this).load(uri).centerCrop().into(ivPreview);
        }

        // Separate existing tags into normal tags and hashtags
        StringBuilder normalBuilder = new StringBuilder();
        StringBuilder hashtagsBuilder = new StringBuilder();

        if (existingTags != null && !existingTags.trim().isEmpty()) {
            String[] tokens = existingTags.trim().split("\\s+");
            for (String token : tokens) {
                if (token.startsWith("#")) {
                    hashtagsBuilder.append(token).append(" ");
                } else {
                    normalBuilder.append(token).append(" ");
                }
            }
        }

        etTags.setText(normalBuilder.toString().trim());
        etHashtags.setText(hashtagsBuilder.toString().trim());

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setView(view);

        if (isBatch) {
            builder.setTitle("Tag this meme (" + batchIndex + " of " + batchTotal + ")");
            setCancelable(false);
        }

        AlertDialog dialog = builder.create();

        if (isBatch) {
            btnSave.setText(R.string.ok);
            btnCancel.setText(R.string.skip);
            btnDestination.setText(R.string.cancel_all);
            btnDestination.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_border));

            btnSave.setOnClickListener(v -> {
                if (batchListener != null && uri != null) {
                    String combined = combineTags(etTags.getText().toString(), etHashtags.getText().toString());
                    batchListener.onOk(uri, combined);
                }
                dismiss();
            });

            btnCancel.setOnClickListener(v -> {
                if (batchListener != null && uri != null) {
                    batchListener.onSkip(uri);
                }
                dismiss();
            });

            btnDestination.setOnClickListener(v -> {
                if (batchListener != null) {
                    batchListener.onCancelAll();
                }
                dismiss();
            });
        } else {
            btnSave.setOnClickListener(v -> {
                if (listener != null && uri != null) {
                    String combined = combineTags(etTags.getText().toString(), etHashtags.getText().toString());
                    listener.onTagSaved(uri, combined);
                }
                dismiss();
            });

            btnCancel.setOnClickListener(v -> dismiss());

            btnDestination.setOnClickListener(v -> {
                Context appContext = requireContext().getApplicationContext();
                Uri destFolderUri = StorageHelper.getDestinationFolderUri(appContext);
                if (destFolderUri == null) {
                    Toast.makeText(appContext, R.string.no_destination_folder, Toast.LENGTH_LONG).show();
                    return;
                }

                String combinedTags = combineTags(etTags.getText().toString(), etHashtags.getText().toString());
                android.app.Activity hostActivity = getActivity();

                Executors.newSingleThreadExecutor().execute(() -> {
                    if (uri != null && combinedTags != null) {
                        Meme existing = MemeDatabase.getDatabase(appContext).memeDao().getByUri(uri.toString());
                        long originalFirstSeen = existing != null ? existing.firstSeen : System.currentTimeMillis();
                        long dateAdded = existing != null ? existing.dateAdded : System.currentTimeMillis();
                        Meme meme = new Meme(uri.toString(), combinedTags, dateAdded, originalFirstSeen);
                        MemeDatabase.getDatabase(appContext).memeDao().insert(meme);
                    }

                    Uri copiedDestUri = FileUtil.copyFileAndGetUri(appContext, uri, destFolderUri);
                    boolean success = copiedDestUri != null;
                    if (success) {
                        CopiedMeme copiedMeme = new CopiedMeme(copiedDestUri.toString(), System.currentTimeMillis(), uri.toString());
                        MemeDatabase.getDatabase(appContext).copiedMemeDao().insert(copiedMeme);
                    }

                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        Context ctx = (isAdded() && getContext() != null) ? getContext() : appContext;
                        Toast.makeText(ctx, success ? R.string.copied_to_destination : R.string.failed_to_copy, Toast.LENGTH_SHORT).show();

                        if (success && hostActivity instanceof com.example.meme_engine.MainGridActivity) {
                            ((com.example.meme_engine.MainGridActivity) hostActivity).loadDestinationTab();
                        }
                    });
                });

                dismiss();
            });
        }

        return dialog;
    }

    private String combineTags(String normal, String hashtags) {
        String n = normal != null ? normal.trim() : "";
        String h = hashtags != null ? hashtags.trim() : "";

        // Ensure hashtag words start with '#' if user forgot '#'
        if (!h.isEmpty()) {
            StringBuilder formattedHashtags = new StringBuilder();
            String[] tokens = h.split("\\s+");
            for (String token : tokens) {
                if (!token.startsWith("#")) {
                    formattedHashtags.append("#").append(token).append(" ");
                } else {
                    formattedHashtags.append(token).append(" ");
                }
            }
            h = formattedHashtags.toString().trim();
        }

        if (n.isEmpty()) return h;
        if (h.isEmpty()) return n;
        return n + " " + h;
    }
}
