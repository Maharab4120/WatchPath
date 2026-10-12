package com.watchpath.app;

import android.app.Application;
import android.util.Log;

import androidx.room.Room;

import com.watchpath.app.data.local.AppDatabase;

import com.watchpath.app.util.NotificationHelper;

/**
 * Custom Application class. Holds app-wide singletons.
 *
 * For now: the Room database. Firebase will go here later in Phase 5.
 */
public class WatchPathApp extends Application {

    private static final String TAG = "WatchPathApp";
    private static final String DB_NAME = "watchpath.db";

    private static AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application created");

        database = Room.databaseBuilder(this, AppDatabase.class, DB_NAME)
                // For a student project this is fine; production apps use migrations.
                .fallbackToDestructiveMigration()
                .build();
        com.google.firebase.firestore.FirebaseFirestore.getInstance();
        Log.d(TAG, "Firestore initialized");
        com.watchpath.app.util.NotificationHelper.ensureChannel(this);
        // TEMPORARY smoke test - remove in Phase 5C.
        /*new Thread(() -> {
            try {
                com.watchpath.app.data.remote.LinksRemoteDataSource ds =
                        new com.watchpath.app.data.remote.LinksRemoteDataSource();
                android.util.Log.d(TAG, "Test getLinks(movie 550) = " + ds.getLinks(550, "movie"));
                android.util.Log.d(TAG, "Test getRequestCount(movie 550) = " + ds.getRequestCount(550, "movie"));
            } catch (Exception e) {
                android.util.Log.e(TAG, "Firestore smoke test failed", e);
            }
        }).start();*/
    }

    /** Global accessor. UI code calls this, not Room.databaseBuilder directly. */
    public static AppDatabase getDatabase() {
        return database;
    }
}