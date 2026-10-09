package com.watchpath.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.databinding.ActivityMainBinding;
import com.watchpath.app.ui.BaseActivity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Temporary launch activity. In Phase 2C this becomes SearchActivity with a
 * real search UI. For now it just proves the TMDB pipe works end to end:
 * background thread -> HTTP GET -> JSON parse -> log on main thread.
 */
public class MainActivity extends BaseActivity {

    private static final String TAG = "WatchPathTest";

    private ActivityMainBinding binding;

    // Single background thread for network I/O. Android forbids network on the
    // main thread - it would freeze the UI.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Handler bound to the main thread, so we can hop back to it for UI/log work.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Temporary: fire a test search and log the results.
        testSearch("fight club");
    }

    private void testSearch(String query) {
        executor.execute(() -> {
            try {
                MediaRemoteDataSource ds = new MediaRemoteDataSource();
                List<MediaDto> results = ds.searchMulti(query);

                mainHandler.post(() -> {
                    Log.d(TAG, "Got " + results.size() + " results for \"" + query + "\"");
                    for (int i = 0; i < Math.min(5, results.size()); i++) {
                        Log.d(TAG, "  [" + i + "] " + results.get(i).toString());
                    }
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