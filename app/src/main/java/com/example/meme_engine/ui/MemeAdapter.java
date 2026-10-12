package com.example.meme_engine.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.meme_engine.R;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MemeAdapter extends RecyclerView.Adapter<MemeAdapter.MemeViewHolder> {

    private List<Uri> imageUris = new ArrayList<>();
    private Map<String, String> memeTags = new HashMap<>();
    private final Set<Uri> selectedUris = new HashSet<>();
    private boolean isMultiSelectMode = false;
    private final SourceListener listener;

    public interface SourceListener {
        void onMemeClick(Uri uri, String existingTags);
        void onSelectionChanged(int count);
    }

    public MemeAdapter(SourceListener listener) {
        this.listener = listener;
    }

    public void setImages(List<Uri> uris) {
        this.imageUris = uris != null ? uris : new ArrayList<>();
        this.selectedUris.clear();
        this.isMultiSelectMode = false;
        notifyDataSetChanged();
    }

    public void setTags(Map<String, String> tags) {
        this.memeTags = tags != null ? tags : new HashMap<>();
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
    public MemeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_meme, parent, false);
        return new MemeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemeViewHolder holder, int position) {
        Uri uri = imageUris.get(position);
        String tags = memeTags.get(uri.toString());

        Glide.with(holder.ivMemeThumbnail.getContext())
                .load(uri)
                .centerCrop()
                .into(holder.ivMemeThumbnail);

        if (tags != null && !tags.trim().isEmpty()) {
            holder.ivBadge.setImageResource(R.drawable.ic_tag_done);
            if (holder.tvTagPreview != null) {
                holder.tvTagPreview.setVisibility(View.VISIBLE);
                holder.tvTagPreview.setText(tags.trim());
            }
        } else {
            holder.ivBadge.setImageResource(R.drawable.ic_tag_none);
            if (holder.tvTagPreview != null) {
                holder.tvTagPreview.setVisibility(View.GONE);
            }
        }

        boolean isSelected = selectedUris.contains(uri);
        holder.vSelectionOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.ivCheckmark.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        // Hide tag badge during multi-select
        holder.ivBadge.setVisibility(isMultiSelectMode ? View.GONE : View.VISIBLE);

        holder.itemView.setOnClickListener(v -> {
            if (isMultiSelectMode) {
                toggleSelection(uri);
            } else {
                listener.onMemeClick(uri, tags);
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

    static class MemeViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMemeThumbnail;
        ImageView ivBadge;
        TextView tvTagPreview;
        View vSelectionOverlay;
        ImageView ivCheckmark;

        public MemeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMemeThumbnail = itemView.findViewById(R.id.ivMemeThumbnail);
            ivBadge = itemView.findViewById(R.id.ivBadge);
            tvTagPreview = itemView.findViewById(R.id.tvTagPreview);
            vSelectionOverlay = itemView.findViewById(R.id.vSelectionOverlay);
            ivCheckmark = itemView.findViewById(R.id.ivCheckmark);
        }
    }
}
