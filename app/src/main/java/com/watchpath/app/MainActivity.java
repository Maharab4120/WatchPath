package com.watchpath.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.databinding.ActivityMainBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.ui.search.MediaAdapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Temporary launch activity. In Step D this becomes SearchActivity with a real
 * search bar. For now it fires a hardcoded query and shows results in a list.
 */
public class MainActivity extends BaseActivity {

    private static final String TAG = "WatchPathTest";

    private ActivityMainBinding binding;
    private MediaAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // RecyclerView setup
        adapter = new MediaAdapter();
        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerResults.setAdapter(adapter);

        // Test search until Step D adds a real search bar.
        testSearch("fight club");
    }

    private void testSearch(String query) {
        executor.execute(() -> {
            try {
                MediaRemoteDataSource ds = new MediaRemoteDataSource();
                List<MediaDto> results = ds.searchMulti(query);

                mainHandler.post(() -> {
                    Log.d(TAG, "Got " + results.size() + " results for \"" + query + "\"");
                    adapter.submitList(results);
                });
            } catch (Exception e) {
                Log.e(TAG, "Search failed", e);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}