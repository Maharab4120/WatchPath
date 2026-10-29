package com.watchpath.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.databinding.ActivityMainBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.ui.details.DetailsActivity;
import com.watchpath.app.ui.mylist.MyListActivity;
import com.watchpath.app.ui.search.MainViewModel;
import com.watchpath.app.ui.search.MediaAdapter;

import java.util.List;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private MediaAdapter adapter;
    private MainViewModel viewModel;

    // For Debounced Search
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. FIX: Set the toolbar FIRST, then check if it's not null to set the title
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        // 2. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // 3. Setup RecyclerView
        adapter = new MediaAdapter();
        adapter.setOnItemClickListener(this::openDetails);
        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerResults.setAdapter(adapter);

        // 4. Setup Pull-to-Refresh
        binding.swipeRefresh.setOnRefreshListener(() -> {
            String q = binding.editSearch.getText().toString().trim();
            if (q.isEmpty()) {
                viewModel.loadTrending();
            } else {
                viewModel.search(q);
            }
        });

        // 5. Setup Search Button
        binding.buttonSearch.setOnClickListener(v -> {
            String q = binding.editSearch.getText().toString().trim();
            viewModel.search(q);
        });

        binding.editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = binding.editSearch.getText().toString().trim();
                viewModel.search(q);
                return true;
            }
            return false;
        });

        // 6. Setup Clear Button & Auto-Search (Debounce)
        binding.buttonClear.setOnClickListener(v -> {
            binding.editSearch.setText(""); // This will trigger the TextWatcher below
        });

        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                binding.buttonClear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);

                // Cancel the previous pending search
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                if (query.isEmpty()) {
                    viewModel.loadTrending();
                } else {
                    // Create a new search task that runs after 500ms
                    searchRunnable = () -> viewModel.search(query);
                    searchHandler.postDelayed(searchRunnable, 500);
                }
            }
        });

        // 7. Observe ViewModel State
        setupObservers();
    }

    private void setupObservers() {
        // Observe Loading state
        viewModel.getIsLoading().observe(this, isLoading -> {
            binding.progressLoading.setVisibility(isLoading && !binding.swipeRefresh.isRefreshing() ? View.VISIBLE : View.GONE);
            binding.swipeRefresh.setRefreshing(isLoading && binding.swipeRefresh.isRefreshing());

            if (isLoading) {
                binding.recyclerResults.setVisibility(View.GONE);
                binding.textStatus.setVisibility(View.GONE);
            }
        });

        // Observe Error state
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                binding.textStatus.setText(error);
                binding.textStatus.setVisibility(View.VISIBLE);
                binding.recyclerResults.setVisibility(View.GONE);
            }
        });

        // Observe Header text (Trending vs Search)
        viewModel.getIsTrendingMode().observe(this, isTrending -> {
            binding.textListHeader.setText(isTrending ? R.string.header_trending : R.string.header_search_results);
        });

        // Observe Data (The list of movies)
        viewModel.getMediaList().observe(this, mediaList -> {
            if (mediaList != null) {
                adapter.submitList(mediaList);
                if (mediaList.isEmpty()) {
                    binding.textStatus.setText(R.string.status_empty);
                    binding.textStatus.setVisibility(View.VISIBLE);
                    binding.recyclerResults.setVisibility(View.GONE);
                } else {
                    binding.textStatus.setVisibility(View.GONE);
                    binding.recyclerResults.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void openDetails(MediaDto item) {
        Intent intent = new Intent(this, DetailsActivity.class);
        intent.putExtra(DetailsActivity.EXTRA_ID, item.id);
        intent.putExtra(DetailsActivity.EXTRA_TYPE, item.mediaType);
        intent.putExtra(DetailsActivity.EXTRA_TITLE, item.title);
        startActivity(intent);
    }

    // --- Menu Setup ---

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
        // Prevent memory leaks from the debounce handler
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
    }
}