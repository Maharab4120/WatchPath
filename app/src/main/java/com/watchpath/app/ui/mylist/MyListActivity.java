package com.watchpath.app.ui.mylist;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.watchpath.app.R;
import com.watchpath.app.WatchPathApp;
import com.watchpath.app.data.MediaRepository;
import com.watchpath.app.data.local.MediaEntity;
import com.watchpath.app.databinding.ActivityMyListBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.ui.details.DetailsActivity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows saved titles from the local Room database, split by status tab.
 *
 * Week 11 syllabus: Room / SQLite queries, background data operations.
 */
public class MyListActivity extends BaseActivity {

    private static final String TAG = "WatchPathMyList";

    /** Tab labels. "All" shows every saved title; the rest filter by status. */
    private static final String[] TAB_TITLES = {
            "All", "Watching", "Planned", "On-Hold", "Dropped", "Watched"
    };

    private ActivityMyListBinding binding;
    private MediaEntityAdapter adapter;
    private MediaRepository repository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentTab = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        repository = new MediaRepository(WatchPathApp.getDatabase().mediaDao());

        adapter = new MediaEntityAdapter();
        adapter.setOnItemClickListener(this::openDetails);
        binding.recyclerMyList.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerMyList.setAdapter(adapter);

        // Populate tabs.
        for (String title : TAB_TITLES) {
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(title));
        }
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = TAB_TITLES[tab.getPosition()];
                loadList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload on every resume, in case the user changed a status on Details.
        loadList();
    }

    private void loadList() {
        final String tab = currentTab;
        executor.execute(() -> {
            List<MediaEntity> rows = "All".equals(tab)
                    ? repository.getAll()
                    : repository.getByStatus(tab);

            mainHandler.post(() -> {
                Log.d(TAG, "Tab \"" + tab + "\" -> " + rows.size() + " rows");
                adapter.submitList(rows);
                binding.textEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                binding.recyclerMyList.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void openDetails(MediaEntity item) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_ID, item.id);
        intent.putExtra(DetailsActivity.EXTRA_TYPE, item.type);
        intent.putExtra(DetailsActivity.EXTRA_TITLE, item.title);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}