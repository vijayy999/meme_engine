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
import java.util.List;
import java.util.Map;

public class MemeAdapter extends RecyclerView.Adapter<MemeAdapter.MemeViewHolder> {

    private List<Uri> imageUris = new ArrayList<>();
    private Map<String, String> memeTags = new HashMap<>();
    private final OnMemeClickListener listener;

    public interface OnMemeClickListener {
        void onMemeClick(Uri uri, String existingTags);
    }

    public MemeAdapter(OnMemeClickListener listener) {
        this.listener = listener;
    }

    public void setImages(List<Uri> uris) {
        this.imageUris = uris;
        notifyDataSetChanged();
    }

    public void setTags(Map<String, String> tags) {
        this.memeTags = tags;
        notifyDataSetChanged();
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

        holder.itemView.setOnClickListener(v -> listener.onMemeClick(uri, tags));
    }

    @Override
    public int getItemCount() {
        return imageUris.size();
    }

    static class MemeViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMemeThumbnail;
        ImageView ivBadge;
        TextView tvTagPreview;

        public MemeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMemeThumbnail = itemView.findViewById(R.id.ivMemeThumbnail);
            ivBadge = itemView.findViewById(R.id.ivBadge);
            tvTagPreview = itemView.findViewById(R.id.tvTagPreview);
        }
    }
}
