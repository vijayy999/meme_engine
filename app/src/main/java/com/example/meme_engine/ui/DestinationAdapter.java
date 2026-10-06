package com.example.meme_engine.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.meme_engine.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DestinationAdapter extends RecyclerView.Adapter<DestinationAdapter.DestinationViewHolder> {

    private List<Uri> imageUris = new ArrayList<>();
    private final Set<Uri> selectedUris = new HashSet<>();
    private boolean isMultiSelectMode = false;
    private final DestinationListener listener;

    public interface DestinationListener {
        void onDeleteSingle(Uri uri);
        void onSelectionChanged(int count);
    }

    public DestinationAdapter(DestinationListener listener) {
        this.listener = listener;
    }

    public void setImages(List<Uri> uris) {
        this.imageUris = uris != null ? uris : new ArrayList<>();
        this.selectedUris.clear();
        this.isMultiSelectMode = false;
        notifyDataSetChanged();
    }

    public Set<Uri> getSelectedUris() {
        return new HashSet<>(selectedUris);
    }

    public void clearSelection() {
        selectedUris.clear();
        isMultiSelectMode = false;
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(0);
    }

    @NonNull
    @Override
    public DestinationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_destination_meme, parent, false);
        return new DestinationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DestinationViewHolder holder, int position) {
        Uri uri = imageUris.get(position);

        Glide.with(holder.ivMemeThumbnail.getContext())
                .load(uri)
                .centerCrop()
                .into(holder.ivMemeThumbnail);

        boolean isSelected = selectedUris.contains(uri);
        holder.vSelectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.ivCheckmark.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        // Hide single delete X during multi-select mode
        holder.ivDeleteSingle.setVisibility(isMultiSelectMode ? View.GONE : View.VISIBLE);

        holder.ivDeleteSingle.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteSingle(uri);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (isMultiSelectMode) {
                toggleSelection(uri);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isMultiSelectMode) {
                isMultiSelectMode = true;
                toggleSelection(uri);
                return true;
            }
            return false;
        });
    }

    private void toggleSelection(Uri uri) {
        if (selectedUris.contains(uri)) {
            selectedUris.remove(uri);
        } else {
            selectedUris.add(uri);
        }
        if (selectedUris.isEmpty()) {
            isMultiSelectMode = false;
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionChanged(selectedUris.size());
        }
    }

    @Override
    public int getItemCount() {
        return imageUris.size();
    }

    static class DestinationViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMemeThumbnail;
        View vSelectionOverlay;
        ImageView ivCheckmark;
        ImageView ivDeleteSingle;

        public DestinationViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMemeThumbnail = itemView.findViewById(R.id.ivMemeThumbnail);
            vSelectionOverlay = itemView.findViewById(R.id.vSelectionOverlay);
            ivCheckmark = itemView.findViewById(R.id.ivCheckmark);
            ivDeleteSingle = itemView.findViewById(R.id.ivDeleteSingle);
        }
    }
}
