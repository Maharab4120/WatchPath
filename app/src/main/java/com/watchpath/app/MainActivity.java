
package com.watchpath.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.watchpath.app.data.remote.CloudListSync;
import com.watchpath.app.data.remote.DiscoverQuery;
import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.databinding.ActivityMainBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.ui.details.DetailsActivity;
import com.watchpath.app.ui.mylist.MyListActivity;
import com.watchpath.app.ui.search.FilterSheetFragment;
import com.watchpath.app.ui.search.MainViewModel;
import com.watchpath.app.ui.search.MediaAdapter;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private MediaAdapter adapter;
    private MainViewModel viewModel;
    private static final String TAG = "MainActivity";

    /** The filters currently applied, so the sheet re-opens pre-filled. */
    private DiscoverQuery appliedFilters = new DiscoverQuery();

    // For Debounced Search
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Contact Picker Activity Result Launcher
    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null) {

                            Uri contactUri = result.getData().getData();

                            if (contactUri != null) {
                                sendInviteToContact(contactUri);
                            }
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. Setup Toolbar
        setSupportActionBar(binding.toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
        }

        // 2. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // 3. Setup RecyclerView
        adapter = new MediaAdapter();
        adapter.setOnItemClickListener(this::openDetails);

        binding.recyclerResults.setLayoutManager(
                new LinearLayoutManager(this));

        binding.recyclerResults.setAdapter(adapter);

        // Setup Pagination Scroll Listener
        binding.recyclerResults.addOnScrollListener(
                new RecyclerView.OnScrollListener() {

                    @Override
                    public void onScrolled(
                            @NonNull RecyclerView recyclerView,
                            int dx,
                            int dy) {

                        super.onScrolled(recyclerView, dx, dy);

                        // Only check if scrolling down
                        if (dy > 0) {

                            LinearLayoutManager layoutManager =
                                    (LinearLayoutManager) recyclerView.getLayoutManager();

                            if (layoutManager != null) {

                                int visibleItemCount =
                                        layoutManager.getChildCount();

                                int totalItemCount =
                                        layoutManager.getItemCount();

                                int pastVisibleItems =
                                        layoutManager.findFirstVisibleItemPosition();

                                // Load next page near the bottom
                                if ((visibleItemCount + pastVisibleItems)
                                        >= totalItemCount - 3) {

                                    viewModel.loadNextPage();
                                }
                            }
                        }
                    }
                });

        // 4. Setup Pull-to-Refresh
        binding.swipeRefresh.setOnRefreshListener(() -> {

            String q = binding.editSearch.getText().toString().trim();

            if (q.isEmpty()) {
                reloadDefault();
            } else {
                viewModel.search(q);
            }
        });

        // 5. Setup Search Button
        binding.buttonSearch.setOnClickListener(v -> {

            String q = binding.editSearch.getText().toString().trim();

            viewModel.search(q);
        });

        binding.editSearch.setOnEditorActionListener(
                (v, actionId, event) -> {

                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {

                        String q = binding.editSearch.getText().toString().trim();

                        viewModel.search(q);

                        return true;
                    }

                    return false;
                });

        // 6. Setup Clear Button & Auto-Search (Debounce)
        binding.buttonClear.setOnClickListener(v -> {
            binding.editSearch.setText("");
        });

        binding.editSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {

                String query = s.toString().trim();

                binding.buttonClear.setVisibility(
                        query.isEmpty() ? View.GONE : View.VISIBLE);

                // Cancel previous pending search
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                if (query.isEmpty()) {

                    reloadDefault();

                } else {

                    // Search after 500ms
                    searchRunnable = () -> viewModel.search(query);

                    searchHandler.postDelayed(searchRunnable, 500);
                }
            }
        });

        // 7. Observe ViewModel State
        setupObservers();

        // 8. Receive filters applied in the bottom sheet
        getSupportFragmentManager().setFragmentResultListener(
                FilterSheetFragment.REQUEST_KEY, this, (key, bundle) -> {
                    Object o = bundle.getSerializable(FilterSheetFragment.RESULT_QUERY);
                    if (o instanceof DiscoverQuery) {
                        appliedFilters = (DiscoverQuery) o;
                        viewModel.loadDiscover(appliedFilters);
                    }
                });
    }

    private void setupObservers() {

        // Observe Loading state
        viewModel.getIsLoading().observe(this, isLoading -> {

            binding.progressLoading.setVisibility(
                    isLoading && !binding.swipeRefresh.isRefreshing()
                            ? View.VISIBLE : View.GONE);

            binding.swipeRefresh.setRefreshing(
                    isLoading && binding.swipeRefresh.isRefreshing());

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

        // Observe Header text (Trending vs Search vs Filtered)
        viewModel.getMode().observe(this, mode -> {

            int header;
            if (mode == MainViewModel.Mode.SEARCH) {
                header = R.string.header_search_results;
            } else if (mode == MainViewModel.Mode.DISCOVER) {
                header = R.string.header_filter_results;
            } else {
                header = R.string.header_trending;
            }
            binding.textListHeader.setText(header);
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

    /**
     * Restores the "no search text" view: the filtered results when filters are
     * applied, otherwise trending. Keeps filters from being lost on refresh/clear.
     */
    private void reloadDefault() {
        if (appliedFilters.isEmpty()) {
            viewModel.loadTrending();
        } else {
            viewModel.loadDiscover(appliedFilters);
        }
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

    // Runs every time the menu opens
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        MenuItem account = menu.findItem(R.id.action_account);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null && !user.isAnonymous()) {

            String who = user.getEmail() != null ? user.getEmail() : "";

            account.setTitle(
                    getString(R.string.menu_account_logout, who));

        } else {

            account.setTitle(R.string.menu_account_login);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.action_filter) {

            FilterSheetFragment.newInstance(appliedFilters)
                    .show(getSupportFragmentManager(), "filter_sheet");

            return true;
        }

        if (id == R.id.action_my_list) {

            startActivity(new Intent(this, MyListActivity.class));

            return true;
        }

        if (id == R.id.action_account) {

            onAccountClicked();

            return true;
        }

        if (id == R.id.action_invite_friend) {

            openContactPicker();

            return true;
        }

        if (id == R.id.action_settings) {

            startActivity(new Intent(
                    this, com.watchpath.app.ui.settings.SettingsActivity.class));

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onAccountClicked() {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        boolean loggedIn = user != null && !user.isAnonymous();

        if (loggedIn) {

            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_logout_title)
                    .setMessage(R.string.dialog_logout_message)
                    .setPositiveButton(
                            R.string.action_log_out,
                            (d, w) -> logOut())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();

        } else {

            // Guest wants an account: show login screen
            Intent i = new Intent(this, LoginActivity.class);

            i.putExtra(LoginActivity.EXTRA_FORCE_SHOW, true);

            startActivity(i);
        }
    }

    private void logOut() {

        // Clear local list, then sign out
        CloudListSync.clearLocal(
                getApplicationContext(),
                WatchPathApp.getDatabase().mediaDao(),
                () -> {

                    FirebaseAuth.getInstance().signOut();

                    getSharedPreferences(LoginActivity.PREFS, MODE_PRIVATE)
                            .edit()
                            .putBoolean(LoginActivity.KEY_GUEST, false)
                            .apply();

                    Intent i = new Intent(this, LoginActivity.class);

                    i.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    startActivity(i);
                });
    }

    // --- Invite a Friend ---

    private void openContactPicker() {

        Intent pickIntent = new Intent(
                Intent.ACTION_PICK,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);

        contactPickerLauncher.launch(pickIntent);
    }

    private void sendInviteToContact(Uri contactUri) {
        String contactName = null;

        // Only the name column is read: that is all the message needs
        try (Cursor cursor = getContentResolver().query(
                contactUri,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                contactName = cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not read the chosen contact", e);
        }

        if (contactName == null) {
            Toast.makeText(this, R.string.no_contacts_found, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,
                getString(R.string.invite_message_personalized, contactName));
        startActivity(Intent.createChooser(send, getString(R.string.invite_chooser_title)));
    }
}
