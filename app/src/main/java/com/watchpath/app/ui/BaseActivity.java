package com.watchpath.app.ui;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Base class for every activity in WatchPath.
 *
 * Its only real job during Phase 1 is to log every Activity lifecycle callback
 * under a single Logcat tag, so we can demonstrate the lifecycle during the
 * Week 13 demo (especially what happens on rotation).
 *
 * We use a base class instead of copying Log.d() into every activity so the
 * logging is consistent and lives in exactly one place - DRY.
 */
public abstract class BaseActivity extends AppCompatActivity {

    /** Filter Logcat by this tag to see the full lifecycle sequence. */
    public static final String LIFECYCLE_TAG = "WatchPathLifecycle";

    /** Subclasses can override if they want a custom display name in the logs. */
    protected String activityName() {
        return getClass().getSimpleName();
    }

    private void log(String callback) {
        Log.d(LIFECYCLE_TAG, activityName() + " - " + callback);
    }

    // --- Setup callbacks: log AFTER super so super's work completes first ---

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log("onCreate");
    }

    @Override
    protected void onStart() {
        super.onStart();
        log("onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        log("onResume");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        log("onRestart");
    }

    // --- Teardown callbacks: log BEFORE super, matching Android's guidance ---

    @Override
    protected void onPause() {
        log("onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        log("onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        log("onDestroy");
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        log("onSaveInstanceState");
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        log("onRestoreInstanceState");
    }
}