package com.watchpath.app.ui.mylist;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.watchpath.app.data.local.MediaEntity;
import com.watchpath.app.databinding.ItemMediaBinding;
import com.watchpath.app.util.Constants;
import com.watchpath.app.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders rows from the local "media" table (saved titles).
 *
 * Reuses item_media.xml - the visual layout is identical to search results.
 * The only semantic difference: the type badge says MOVIE / TV based on the
 * entity's "type" field.
 */
public class MediaEntityAdapter extends RecyclerView.Adapter<MediaEntityAdapter.RowHolder> {

    public interface OnItemClickListener {
        void onItemClick(MediaEntity item);
    }

    private final List<MediaEntity> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<MediaEntity> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMediaBinding binding = ItemMediaBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RowHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class RowHolder extends RecyclerView.ViewHolder {

        private final ItemMediaBinding binding;

        RowHolder(ItemMediaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MediaEntity item) {
            binding.textTitle.setText(item.title);
            binding.textYear.setText(item.year == null || item.year.isEmpty() ? "—" : item.year);

            if (item.rating > 0) {
                binding.textRating.setText(String.format(Locale.US, "★ %.1f", item.rating));
            } else {
                binding.textRating.setText("★ —");
            }

            binding.textTypeBadge.setText("tv".equals(item.type) ? "TV" : "MOVIE");

            if (item.posterPath != null && !item.posterPath.isEmpty()) {
                String url = Constants.TMDB_IMAGE_BASE + "/"
                        + Constants.TMDB_POSTER_SIZE + item.posterPath;
                ImageLoader.get().load(url, binding.imagePoster);
            } else {
                ImageLoader.get().load(null, binding.imagePoster);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }
    }
}