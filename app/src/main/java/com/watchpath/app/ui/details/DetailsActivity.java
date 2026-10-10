package com.watchpath.app.ui.details;

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
import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.dto.MediaDetailsDto;
import com.watchpath.app.databinding.ActivityDetailsBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.util.Constants;
import com.watchpath.app.util.ImageLoader;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private String[] statuses;

    /** Populated after the network call returns; needed by the Save button. */
    private MediaDetailsDto currentDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new MediaRepository(WatchPathApp.getDatabase().mediaDao());
        statuses = getResources().getStringArray(R.array.status_options);

        // Set up the Spinner with the status list.
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, statuses);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerStatus.setAdapter(spinnerAdapter);

        // Save button disabled until details have loaded, to avoid saving blanks.
        binding.buttonSaveStatus.setEnabled(false);
        binding.buttonSaveStatus.setOnClickListener(v -> saveCurrent());

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
    }

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

    /** Checks Room for an existing entry and pre-selects the Spinner. */
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

    /** Builds a MediaEntity from the loaded details + selected status, saves to Room. */
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

        if (d.genres.isEmpty()) {
            binding.textGenres.setVisibility(View.GONE);
        } else {
            binding.textGenres.setVisibility(View.VISIBLE);
            binding.textGenres.setText(d.genres);
        }

        if (d.tagline == null || d.tagline.trim().isEmpty()) {
            binding.textTagline.setVisibility(View.GONE);
        } else {
            binding.textTagline.setVisibility(View.VISIBLE);
            binding.textTagline.setText("\u201C" + d.tagline + "\u201D");
        }

        if (d.overview == null || d.overview.isEmpty()) {
            binding.textOverview.setText("No overview available.");
        } else {
            binding.textOverview.setText(d.overview);
        }
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