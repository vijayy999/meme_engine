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
import androidx.fragment.app.DialogFragment;
import com.bumptech.glide.Glide;
import com.example.meme_engine.R;
import com.example.meme_engine.data.CopiedMeme;
import com.example.meme_engine.data.MemeDatabase;
import com.example.meme_engine.util.FileUtil;
import com.example.meme_engine.util.StorageHelper;
import java.util.concurrent.Executors;

public class TagMemeDialogFragment extends DialogFragment {

    private static final String ARG_URI = "uri";
    private static final String ARG_TAGS = "tags";

    private OnTagSavedListener listener;

    public interface OnTagSavedListener {
        void onTagSaved(Uri uri, String tags);
    }

    public static TagMemeDialogFragment newInstance(Uri uri, String existingTags) {
        TagMemeDialogFragment fragment = new TagMemeDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_URI, uri);
        args.putString(ARG_TAGS, existingTags);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnTagSavedListener(OnTagSavedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (getArguments() == null) {
            return super.onCreateDialog(savedInstanceState);
        }

        Uri uri = getArguments().getParcelable(ARG_URI);
        String existingTags = getArguments().getString(ARG_TAGS);

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

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();

        btnSave.setOnClickListener(v -> {
            if (listener != null && uri != null) {
                String combined = combineTags(etTags.getText().toString(), etHashtags.getText().toString());
                listener.onTagSaved(uri, combined);
            }
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());

        btnDestination.setOnClickListener(v -> {
            if (listener != null && uri != null) {
                String combined = combineTags(etTags.getText().toString(), etHashtags.getText().toString());
                listener.onTagSaved(uri, combined);
            }

            Uri destFolderUri = StorageHelper.getDestinationFolderUri(requireContext());
            if (destFolderUri == null) {
                Toast.makeText(requireContext(), R.string.no_destination_folder, Toast.LENGTH_LONG).show();
                return;
            }

            Executors.newSingleThreadExecutor().execute(() -> {
                Uri copiedDestUri = FileUtil.copyFileAndGetUri(requireContext(), uri, destFolderUri);
                boolean success = copiedDestUri != null;
                if (success) {
                    CopiedMeme copiedMeme = new CopiedMeme(copiedDestUri.toString(), System.currentTimeMillis(), uri.toString());
                    MemeDatabase.getDatabase(requireContext()).copiedMemeDao().insert(copiedMeme);
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (success) {
                            Toast.makeText(requireContext(), R.string.copied_to_destination, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), R.string.failed_to_copy, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

            dismiss();
        });

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
