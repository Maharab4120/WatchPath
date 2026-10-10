package com.watchpath.app.ui.details;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.watchpath.app.data.remote.MediaRemoteDataSource;
import com.watchpath.app.data.remote.dto.MediaDetailsDto;
import com.watchpath.app.databinding.ActivityDetailsBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.util.Constants;
import com.watchpath.app.util.ImageLoader;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.watchpath.app.R;

/**
 * Full details screen. Fetches /movie/{id} or /tv/{id} from TMDB and renders
 * backdrop, poster, rating, runtime (or season count), genres, and overview.
 */
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set the title bar text from the Intent so the user sees it while loading.
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
                    render(d);
                    showContent();
                });
            } catch (Exception e) {
                Log.e(TAG, "Details load failed", e);
                mainHandler.post(this::showError);
            }
        });
    }

    private void render(MediaDetailsDto d) {
        // Backdrop
        if (d.backdropPath != null) {
            String url = Constants.TMDB_IMAGE_BASE + "/" + Constants.TMDB_BACKDROP_SIZE + d.backdropPath;
            ImageLoader.get().load(url, binding.imageBackdrop);
        } else {
            ImageLoader.get().load(null, binding.imageBackdrop);
        }

        // Poster
        if (d.posterPath != null) {
            String url = Constants.TMDB_IMAGE_BASE + "/" + Constants.TMDB_POSTER_SIZE + d.posterPath;
            ImageLoader.get().load(url, binding.imagePoster);
        } else {
            ImageLoader.get().load(null, binding.imagePoster);
        }

        binding.textTitle.setText(d.title);

        // "1999 · ★ 8.4 · 139 min"
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

        // Genres: hide if empty
        if (d.genres.isEmpty()) {
            binding.textGenres.setVisibility(View.GONE);
        } else {
            binding.textGenres.setVisibility(View.VISIBLE);
            binding.textGenres.setText(d.genres);
        }

        // Tagline: hide if empty
        if (d.tagline == null || d.tagline.trim().isEmpty()) {
            binding.textTagline.setVisibility(View.GONE);
        } else {
            binding.textTagline.setVisibility(View.VISIBLE);
            binding.textTagline.setText("\u201C" + d.tagline + "\u201D");
        }

        // Overview
        if (d.overview == null || d.overview.isEmpty()) {
            binding.textOverview.setText("No overview available.");
        } else {
            binding.textOverview.setText(d.overview);
        }
    }

    // --- UI state helpers ---

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