package com.watchpath.app.ui.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.annotation.IdRes;

import com.watchpath.app.R;
import com.watchpath.app.databinding.ActivitySettingsBinding;
import com.watchpath.app.ui.BaseActivity;
import com.watchpath.app.util.AppSettings;

/**
 * Settings: theme choice, region override for "Where to watch", and whether
 * notifications are shown. Every change is written straight to
 * {@link AppSettings}, so there is no Save button.
 */
public class SettingsActivity extends BaseActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupTheme();
        setupRegion();
        setupNotifications();
    }

    private void setupTheme() {
        // Reflect the saved choice first, then listen for changes.
        binding.toggleTheme.check(themeButtonId(AppSettings.getThemeMode(this)));

        binding.toggleTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            // Recreates the app with the new night mode.
            AppSettings.setThemeMode(this, themeModeOf(checkedId));
        });
    }

    private void setupRegion() {
        // Do not fire the listener while restoring the saved value.
        binding.editRegion.setText(AppSettings.getRegionOverride(this));

        binding.editRegion.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                AppSettings.setRegionOverride(SettingsActivity.this, s.toString());
            }
        });
    }

    private void setupNotifications() {
        binding.switchNotifications.setChecked(
                AppSettings.isNotificationsEnabled(this));

        binding.switchNotifications.setOnCheckedChangeListener(
                (button, checked) ->
                        AppSettings.setNotificationsEnabled(this, checked));
    }

    @IdRes
    private static int themeButtonId(int mode) {
        switch (mode) {
            case AppSettings.THEME_LIGHT:
                return R.id.buttonThemeLight;
            case AppSettings.THEME_DARK:
                return R.id.buttonThemeDark;
            default:
                return R.id.buttonThemeSystem;
        }
    }

    private static int themeModeOf(@IdRes int id) {
        if (id == R.id.buttonThemeLight) return AppSettings.THEME_LIGHT;
        if (id == R.id.buttonThemeDark) return AppSettings.THEME_DARK;
        return AppSettings.THEME_SYSTEM;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
