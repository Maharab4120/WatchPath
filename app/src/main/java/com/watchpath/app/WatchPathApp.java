package com.watchpath.app;

import android.app.Application;
import android.util.Log;

import androidx.room.Room;

import com.watchpath.app.data.local.AppDatabase;

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
    }

    /** Global accessor. UI code calls this, not Room.databaseBuilder directly. */
    public static AppDatabase getDatabase() {
        return database;
    }
}