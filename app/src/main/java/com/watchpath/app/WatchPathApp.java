package com.watchpath.app;

import android.app.Application;
import android.util.Log;

/**
 * Custom Application class.
 *
 * Android instantiates exactly one of these per app process, before any
 * activity, service, or receiver. It lives for the whole process lifetime,
 * which makes it the right place for app-wide singletons we'll need later
 * (Room database instance, Firebase client, repositories).
 *
 * We're adding it now, empty, so it's wired into the manifest before we start
 * needing it. That way when Phase 3 adds the Room database we don't have to
 * touch the manifest again.
 */
public class WatchPathApp extends Application {

    private static final String TAG = "WatchPathApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application created");
    }
}