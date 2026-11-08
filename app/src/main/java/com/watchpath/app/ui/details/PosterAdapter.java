package com.watchpath.app.ui.details;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.watchpath.app.R;
import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.util.Constants;
import com.watchpath.app.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal row of poster thumbnails, used for the "More like this" section
 * on the details screen.
 */
public class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.VH> {

    public interface OnItemClick {
        void onItemClick(MediaDto item);
    }

    private final List<MediaDto> items = new ArrayList<>();
    private final OnItemClick onClick;

    public PosterAdapter(OnItemClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<MediaDto> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_poster, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MediaDto item = items.get(position);

        holder.title.setText(item.title);

        String url = item.posterPath == null ? null
                : Constants.TMDB_IMAGE_BASE + "/" + Constants.TMDB_POSTER_SIZE + item.posterPath;
        ImageLoader.get().load(url, holder.poster);

        holder.itemView.setOnClickListener(v -> onClick.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView title;

        VH(@NonNull View itemView) {
            super(itemView);
            poster = itemView.findViewById(R.id.imagePoster);
            title = itemView.findViewById(R.id.textTitle);
        }
    }
}
