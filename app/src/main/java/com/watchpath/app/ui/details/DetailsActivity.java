package com.watchpath.app.ui.details;

import android.os.Bundle;

import com.watchpath.app.databinding.ActivityDetailsBinding;
import com.watchpath.app.ui.BaseActivity;

/**
 * Stub details screen. Phase 3 will turn this into a real page with poster,
 * overview, cast, seasons (for TV), and the Stream button.
 *
 * For now it just proves the explicit Intent navigation works and displays
 * the title + type we passed in.
 */
public class DetailsActivity extends BaseActivity {

    // Public keys so the caller doesn't have to guess the strings.
    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_TITLE = "extra_title";

    private ActivityDetailsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int id = getIntent().getIntExtra(EXTRA_ID, -1);
        String type = getIntent().getStringExtra(EXTRA_TYPE);
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        binding.textDetailsTitle.setText(title != null ? title : "(unknown)");
        binding.textDetailsSubtitle.setText("id=" + id + ", type=" + type);

        // Show a back arrow in the action bar.
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(title);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        // Handles the back arrow in the toolbar.
        finish();
        return true;
    }
}