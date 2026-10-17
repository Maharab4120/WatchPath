package com.watchpath.app;

import android.view.Menu;
import android.view.MenuItem;
import android.text.Editable;
import android.text.TextWatcher;

import com.watchpath.app.ui.mylist.MyListActivity;
import com.watchpath.app.R;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.databinding.ActivityMainBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.ui.details.DetailsActivity;
import com.watchpath.app.ui.search.MediaAdapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


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
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        adapter = new MediaAdapter();
        adapter.setOnItemClickListener(this::openDetails);
        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerResults.setAdapter(adapter);

        binding.buttonSearch.setOnClickListener(v -> performSearch());

        binding.editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        //binding.editSearch.setText("fight club");
        //performSearch();
        // Clear button visible only when there's text
        binding.buttonClear.setOnClickListener(v -> {
            binding.editSearch.setText("");
            adapter.submitList(null);
            binding.textStatus.setVisibility(View.GONE);
            binding.recyclerResults.setVisibility(View.VISIBLE);
            binding.editSearch.requestFocus();
        });

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                binding.buttonClear.setVisibility(
                        s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    /** Navigate to Details via an explicit Intent. Week 8 syllabus item. */
    private void openDetails(MediaDto item) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_ID, item.id);
        intent.putExtra(DetailsActivity.EXTRA_TYPE, item.mediaType);
        intent.putExtra(DetailsActivity.EXTRA_TITLE, item.title);
        startActivity(intent);
    }

    private void performSearch() {
        String query = binding.editSearch.getText().toString().trim();
        if (query.isEmpty()) return;

        showLoading();

        executor.execute(() -> {
            try {
                MediaRemoteDataSource ds = new MediaRemoteDataSource();
                List<MediaDto> results = ds.searchMulti(query);

                mainHandler.post(() -> {
                    Log.d(TAG, "Got " + results.size() + " results for \"" + query + "\"");
                    adapter.submitList(results);
                    if (results.isEmpty()) showStatus(getString(R.string.status_empty));
                    else showResults();
                });
            } catch (Exception e) {
                Log.e(TAG, "Search failed", e);
                mainHandler.post(() -> showStatus(getString(R.string.status_error)));
            }
        });
    }

    // --- UI state helpers ---

    private void showLoading() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.textStatus.setVisibility(View.GONE);
        binding.recyclerResults.setVisibility(View.GONE);
    }

    private void showResults() {
        binding.progressLoading.setVisibility(View.GONE);
        binding.textStatus.setVisibility(View.GONE);
        binding.recyclerResults.setVisibility(View.VISIBLE);
    }

    private void showStatus(String message) {
        binding.progressLoading.setVisibility(View.GONE);
        binding.textStatus.setText(message);
        binding.textStatus.setVisibility(View.VISIBLE);
        binding.recyclerResults.setVisibility(View.GONE);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_my_list) {
            startActivity(new Intent(this, MyListActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}