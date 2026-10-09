package com.watchpath.app.ui.search;

import com.watchpath.app.util.Constants;
import com.watchpath.app.util.ImageLoader;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.databinding.ItemMediaBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for search results.
 *
 * Week 9 syllabus: RecyclerView, adapter, ViewHolder, custom item layouts.
 * Uses ViewBinding inside the ViewHolder instead of findViewById.
 */
public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {

    /** Called when the user taps an item. We'll wire this in Step D. */
    public interface OnItemClickListener {
        void onItemClick(MediaDto item);
    }

    private final List<MediaDto> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /** Replace the current list and refresh the UI. */
    public void submitList(List<MediaDto> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MediaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMediaBinding binding = ItemMediaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new MediaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class MediaViewHolder extends RecyclerView.ViewHolder {

        private final ItemMediaBinding binding;

        MediaViewHolder(ItemMediaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MediaDto item) {
            binding.textTitle.setText(item.title);

            // Year: show an em-dash if unknown.
            binding.textYear.setText(item.year.isEmpty() ? "—" : item.year);

            // Rating: only show if it's above zero.
            if (item.rating > 0) {
                binding.textRating.setText(String.format(Locale.US, "★ %.1f", item.rating));
            } else {
                binding.textRating.setText("★ —");
            }

            // Type badge text.
            binding.textTypeBadge.setText(
                    MediaDto.TYPE_MOVIE.equals(item.mediaType) ? "MOVIE" : "TV");

            // Build the full poster URL from TMDB's base + size + path.
            // posterPath can be null for obscure titles - ImageLoader handles that.
            if (item.posterPath != null) {
                String url = Constants.TMDB_IMAGE_BASE + "/"
                        + Constants.TMDB_POSTER_SIZE
                        + item.posterPath;
                ImageLoader.get().load(url, binding.imagePoster);
            } else {
                ImageLoader.get().load(null, binding.imagePoster); // clears + shows placeholder
            }

            // Tap handling (wired in Step D; safe no-op for now).
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }
    }
}