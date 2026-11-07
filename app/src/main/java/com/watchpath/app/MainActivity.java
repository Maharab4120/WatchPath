
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
import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.databinding.ActivityMainBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.ui.details.DetailsActivity;
import com.watchpath.app.ui.mylist.MyListActivity;
import com.watchpath.app.ui.search.MainViewModel;
import com.watchpath.app.ui.search.MediaAdapter;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private MediaAdapter adapter;
    private MainViewModel viewModel;

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

                    viewModel.loadTrending();

                } else {

                    // Search after 500ms
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

        // Observe Header text (Trending vs Search)
        viewModel.getIsTrendingMode().observe(this, isTrending -> {

            binding.textListHeader.setText(
                    isTrending
                            ? R.string.header_trending
                            : R.string.header_search_results);
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

    private void sendInviteToContact(android.net.Uri contactUri) {
        String contactName = null;
        String phoneNumber = null; // We can keep this just in case, but we'll use the name

        // Use try-with-resources to guarantee the cursor is closed
        try (android.database.Cursor cursor = getContentResolver().query(
                contactUri,
                new String[]{
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER);

                if (nameIndex >= 0) contactName = cursor.getString(nameIndex);
                if (numberIndex >= 0) phoneNumber = cursor.getString(numberIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (contactName != null) {
            // Build the personalized message
            String message = getString(R.string.invite_message_personalized, contactName);

            // Use ACTION_SEND instead of ACTION_SENDTO to open the full share sheet
            android.content.Intent sendIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, message);

            // Optional: If you want to pre-fill the SMS number if they choose SMS,
            // you can still pass the phone number as an extra.
            if (phoneNumber != null) {
                sendIntent.putExtra("address", phoneNumber);
            }

            // Open the Android Share Sheet
            startActivity(android.content.Intent.createChooser(sendIntent, getString(R.string.invite_chooser_title)));
        } else {
            android.widget.Toast.makeText(this, R.string.no_contacts_found, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
