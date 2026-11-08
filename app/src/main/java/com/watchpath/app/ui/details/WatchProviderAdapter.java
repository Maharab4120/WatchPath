package com.watchpath.app.ui.details;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.watchpath.app.R;
import com.watchpath.app.data.remote.dto.WatchProviders;
import com.watchpath.app.util.Constants;
import com.watchpath.app.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal row of streaming-service logos for the "Where to watch" section.
 */
public class WatchProviderAdapter extends RecyclerView.Adapter<WatchProviderAdapter.VH> {

    public interface OnProviderClickListener {
        void onProviderClick(WatchProviders.Provider provider);
    }

    private final List<WatchProviders.Provider> items = new ArrayList<>();
    private OnProviderClickListener listener;

    public void setOnProviderClickListener(OnProviderClickListener listener) {
        this.listener = listener;
    }

    public void submit(List<WatchProviders.Provider> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_provider, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        WatchProviders.Provider p = items.get(position);
        holder.name.setText(p.name);

        String url = p.logoPath == null ? null
                : Constants.TMDB_IMAGE_BASE + "/" + Constants.TMDB_LOGO_SIZE + p.logoPath;
        ImageLoader.get().load(url, holder.logo);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onProviderClick(p);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView name;

        VH(@NonNull View itemView) {
            super(itemView);
            logo = itemView.findViewById(R.id.imageProviderLogo);
            name = itemView.findViewById(R.id.textProviderName);
        }
    }
}
