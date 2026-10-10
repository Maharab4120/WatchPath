package com.watchpath.app.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/**
 * Room database. One instance per app process, held by WatchPathApp.
 */
@Database(entities = { MediaEntity.class }, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MediaDao mediaDao();
}