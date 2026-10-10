package com.watchpath.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One saved title in the user's list.
 *
 * Composite primary key (id + type) because TMDB ids are per-namespace:
 * movie 550 is Fight Club, but TV 550 is a different show. Always carry type.
 */
@Entity(tableName = "media", primaryKeys = {"id", "type"})
public class MediaEntity {

    public int id;

    @NonNull
    public String type = "movie";    // "movie" or "tv"

    @NonNull
    public String title = "";

    public String posterPath;        // may be null
    public String overview;
    public double rating;
    public String year;              // 4-char string, may be ""

    /** One of: Watching, Planned, On-Hold, Dropped, Watched */
    @NonNull
    public String status = "Planned";

    /** For sorting; larger = more recently added. */
    public long addedAt;

    // Required by Room - no-arg constructor for object creation from cursors.
    public MediaEntity() { }
}