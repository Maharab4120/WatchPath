package com.watchpath.app.ui.details;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.watchpath.app.R;
import com.watchpath.app.WatchPathApp;
import com.watchpath.app.data.MediaRepository;
import com.watchpath.app.data.local.MediaEntity;
import com.watchpath.app.data.remote.LinksRemoteDataSource;
import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.StreamResolver;
import com.watchpath.app.data.remote.dto.MediaDetailsDto;
import com.watchpath.app.databinding.ActivityDetailsBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.util.Constants;
import com.watchpath.app.util.ImageLoader;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class DetailsActivity extends BaseActivity {

    private static final String TAG = "WatchPathDetails";

    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_TITLE = "extra_title";

    private ActivityDetailsBinding binding;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private int mediaId;
    private String mediaType;

    private MediaRepository repository;
    private LinksRemoteDataSource linksSource;
    private String[] statuses;

    private MediaDetailsDto currentDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        repository = new MediaRepository(WatchPathApp.getDatabase().mediaDao());
        linksSource = new LinksRemoteDataSource();
        statuses = getResources().getStringArray(R.array.status_options);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, statuses);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerStatus.setAdapter(spinnerAdapter);

        binding.buttonSaveStatus.setEnabled(false);
        binding.buttonSaveStatus.setOnClickListener(v -> saveCurrent());
        binding.buttonStream.setOnClickListener(v -> streamCurrent());
        binding.buttonRequest.setOnClickListener(v -> requestCurrent());

        String titleFromIntent = getIntent().getStringExtra(EXTRA_TITLE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(titleFromIntent);
        }

        mediaId = getIntent().getIntExtra(EXTRA_ID, -1);
        mediaType = getIntent().getStringExtra(EXTRA_TYPE);
        if (mediaId <= 0 || mediaType == null) {
            finish();
            return;
        }

        loadDetails();
        maybeRequestNotificationPermission();
    }

    // ====================================================================
    // Details loading (Phase 3 + 4B logic, unchanged)
    // ====================================================================

    private void loadDetails() {
        showLoading();
        executor.execute(() -> {
            try {
                MediaRemoteDataSource ds = new MediaRemoteDataSource();
                MediaDetailsDto d = ds.getDetails(mediaId, mediaType);

                mainHandler.post(() -> {
                    Log.d(TAG, "Loaded details for " + d.title);
                    currentDetails = d;
                    render(d);
                    showContent();
                    binding.buttonSaveStatus.setEnabled(true);
                    loadExistingStatus();
                });
            } catch (Exception e) {
                Log.e(TAG, "Details load failed", e);
                mainHandler.post(this::showError);
            }
        });
    }

    private void loadExistingStatus() {
        executor.execute(() -> {
            MediaEntity existing = repository.find(mediaId, mediaType);
            if (existing == null) return;
            mainHandler.post(() -> {
                int pos = indexOfStatus(existing.status);
                if (pos >= 0) binding.spinnerStatus.setSelection(pos);
            });
        });
    }

    private int indexOfStatus(String status) {
        for (int i = 0; i < statuses.length; i++) {
            if (statuses[i].equals(status)) return i;
        }
        return -1;
    }

    private void saveCurrent() {
        if (currentDetails == null) return;

        String status = (String) binding.spinnerStatus.getSelectedItem();
        MediaEntity e = new MediaEntity();
        e.id = currentDetails.id;
        e.type = currentDetails.mediaType;
        e.title = currentDetails.title;
        e.posterPath = currentDetails.posterPath;
        e.overview = currentDetails.overview;
        e.rating = currentDetails.rating;
        e.year = currentDetails.year;
        e.status = status != null ? status : "Planned";
        e.addedAt = System.currentTimeMillis();

        binding.buttonSaveStatus.setEnabled(false);
        executor.execute(() -> {
            repository.addOrUpdate(e);
            mainHandler.post(() -> {
                binding.buttonSaveStatus.setEnabled(true);
                Toast.makeText(this, R.string.status_saved_toast, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Saved " + e.title + " as " + e.status);
            });
        });
    }

    // ====================================================================
    // Stream (Phase 5C) - the differentiator
    // ====================================================================

    private void streamCurrent() {
        if (currentDetails == null) return;

        binding.buttonStream.setEnabled(false);
        binding.buttonStream.setText(R.string.stream_searching);
        binding.requestRow.setVisibility(View.GONE);

        final int id = currentDetails.id;
        final String type = currentDetails.mediaType;
        final String title = currentDetails.title;

        executor.execute(() -> {
            try {
                List<String> links = linksSource.getLinks(id, type);
                Log.d(TAG, "Links for " + title + ": " + links.size());

                if (links.isEmpty()) {
                    // No links yet. Offer the request flow.
                    int count = linksSource.getRequestCount(id, type);
                    mainHandler.post(() -> showRequestPrompt(count));
                    return;
                }

                // Walk the fallback chain.
                StreamResolver.Result result = new StreamResolver().resolve(links);
                if (result.found) {
                    mainHandler.post(() -> openUrl(result.url));
                } else {
                    mainHandler.post(() -> {
                        resetStreamButton();
                        Toast.makeText(this, R.string.stream_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Stream failed", e);
                mainHandler.post(() -> {
                    resetStreamButton();
                    Toast.makeText(this, R.string.stream_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;

        ActivityCompat.requestPermissions(
                this,
                new String[]{ Manifest.permission.POST_NOTIFICATIONS },
                1001);
    }
    private void resetStreamButton() {
        binding.buttonStream.setEnabled(true);
        binding.buttonStream.setText(R.string.button_stream);
    }

    private void openUrl(String url) {
        resetStreamButton();
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.stream_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRequestPrompt(int count) {
        resetStreamButton();
        binding.requestRow.setVisibility(View.VISIBLE);
        if (count > 0) {
            binding.textStreamStatus.setText(getString(R.string.request_info, count));
        } else {
            binding.textStreamStatus.setText(R.string.stream_not_available);
        }
    }

    private void requestCurrent() {
        if (currentDetails == null) return;

        binding.buttonRequest.setEnabled(false);
        final int id = currentDetails.id;
        final String type = currentDetails.mediaType;
        final String title = currentDetails.title;

        executor.execute(() -> {
            try {
                linksSource.requestTitle(id, type, title);
                int newCount = linksSource.getRequestCount(id, type);
                mainHandler.post(() -> {
                    binding.buttonRequest.setEnabled(true);
                    Toast.makeText(this, R.string.request_sent, Toast.LENGTH_SHORT).show();
                    binding.textStreamStatus.setText(getString(R.string.request_info, newCount));
                    Log.d(TAG, "Requested " + title + ", count now " + newCount);
                });
            } catch (Exception e) {
                Log.e(TAG, "Request failed", e);
                mainHandler.post(() -> binding.buttonRequest.setEnabled(true));
            }
        });
    }

    // ====================================================================
    // Rendering + UI states (unchanged from Phase 3)
    // ====================================================================

    private void render(MediaDetailsDto d) {
        if (d.backdropPath != null) {
            String url = Constants.TMDB_IMAGE_BASE + "/" + Constants.TMDB_BACKDROP_SIZE + d.backdropPath;
            ImageLoader.get().load(url, binding.imageBackdrop);
        } else {
            ImageLoader.get().load(null, binding.imageBackdrop);
        }

        if (d.posterPath != null) {
            String url = Constants.TMDB_IMAGE_BASE + "/" + Constants.TMDB_POSTER_SIZE + d.posterPath;
            ImageLoader.get().load(url, binding.imagePoster);
        } else {
            ImageLoader.get().load(null, binding.imagePoster);
        }

        binding.textTitle.setText(d.title);

        StringBuilder sub = new StringBuilder();
        if (!d.year.isEmpty()) sub.append(d.year);
        if (d.rating > 0) {
            if (sub.length() > 0) sub.append("  ·  ");
            sub.append(String.format(Locale.US, "★ %.1f", d.rating));
        }
        if (!d.runtimeOrSeasons.isEmpty()) {
            if (sub.length() > 0) sub.append("  ·  ");
            sub.append(d.runtimeOrSeasons);
        }
        binding.textSubtitle.setText(sub.toString());

        if (d.genres.isEmpty()) binding.textGenres.setVisibility(View.GONE);
        else { binding.textGenres.setVisibility(View.VISIBLE); binding.textGenres.setText(d.genres); }

        if (d.tagline == null || d.tagline.trim().isEmpty()) binding.textTagline.setVisibility(View.GONE);
        else { binding.textTagline.setVisibility(View.VISIBLE); binding.textTagline.setText("\u201C" + d.tagline + "\u201D"); }

        if (d.overview == null || d.overview.isEmpty()) binding.textOverview.setText("No overview available.");
        else binding.textOverview.setText(d.overview);
    }

    private void showLoading() {
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.scrollContent.setVisibility(View.GONE);
        binding.textError.setVisibility(View.GONE);
    }

    private void showContent() {
        binding.progressLoading.setVisibility(View.GONE);
        binding.scrollContent.setVisibility(View.VISIBLE);
        binding.textError.setVisibility(View.GONE);
    }

    private void showError() {
        binding.progressLoading.setVisibility(View.GONE);
        binding.scrollContent.setVisibility(View.GONE);
        binding.textError.setText(getString(R.string.details_error));
        binding.textError.setVisibility(View.VISIBLE);
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