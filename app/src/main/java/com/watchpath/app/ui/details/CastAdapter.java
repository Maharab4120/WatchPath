package com.watchpath.app.ui.details;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.watchpath.app.R;
import com.watchpath.app.data.remote.dto.CastMember;
import com.watchpath.app.util.Constants;
import com.watchpath.app.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal row of cast headshots for the details screen.
 */
public class CastAdapter extends RecyclerView.Adapter<CastAdapter.VH> {

    private final List<CastMember> items = new ArrayList<>();

    public void submit(List<CastMember> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cast, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CastMember m = items.get(position);

        holder.name.setText(m.name);
        if (m.character == null || m.character.isEmpty()) {
            holder.character.setVisibility(View.GONE);
        } else {
            holder.character.setVisibility(View.VISIBLE);
            holder.character.setText(m.character);
        }

        String url = m.profilePath == null ? null
                : Constants.TMDB_IMAGE_BASE + "/" + Constants.TMDB_PROFILE_SIZE + m.profilePath;
        ImageLoader.get().load(url, holder.profile);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView profile;
        final TextView name;
        final TextView character;

        VH(@NonNull View itemView) {
            super(itemView);
            profile = itemView.findViewById(R.id.imageProfile);
            name = itemView.findViewById(R.id.textName);
            character = itemView.findViewById(R.id.textCharacter);
        }
    }
}
