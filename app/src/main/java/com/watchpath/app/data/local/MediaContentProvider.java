package com.watchpath.app.data.local;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.watchpath.app.WatchPathApp;

/**
 * Read-only ContentProvider exposing the Room "media" table.
 *
 * Week 10 syllabus: Content Providers. This satisfies the requirement with a
 * real consumer - MyListActivity reads through ContentResolver.query() using
 * the URIs below, instead of hitting the DAO directly.
 *
 * URIs:
 *   content://com.watchpath.app.provider/media               -> all rows
 *   content://com.watchpath.app.provider/media/status/Watched -> filtered
 *
 * Writes are deliberately blocked at the provider level, since the app's
 * only write path goes through the repository. This keeps the provider
 * focused and makes the read/write separation explicit.
 */
public class MediaContentProvider extends ContentProvider {

    public static final String AUTHORITY = "com.watchpath.app.provider";
    public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/media");

    private static final int MATCH_ALL = 1;
    private static final int MATCH_BY_STATUS = 2;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        MATCHER.addURI(AUTHORITY, "media", MATCH_ALL);
        MATCHER.addURI(AUTHORITY, "media/status/*", MATCH_BY_STATUS);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri,
                        @Nullable String[] projection,
                        @Nullable String selection,
                        @Nullable String[] selectionArgs,
                        @Nullable String sortOrder) {
        MediaDao dao = WatchPathApp.getDatabase().mediaDao();

        switch (MATCHER.match(uri)) {
            case MATCH_ALL:
                return dao.getAllCursor();
            case MATCH_BY_STATUS: {
                String status = uri.getLastPathSegment();
                return dao.getByStatusCursor(status);
            }
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (MATCHER.match(uri)) {
            case MATCH_ALL:
            case MATCH_BY_STATUS:
                return "vnd.android.cursor.dir/vnd.watchpath.media";
            default:
                return null;
        }
    }

    // --- Writes blocked: the app's write path is the repository, not the provider ---

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("MediaContentProvider is read-only");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("MediaContentProvider is read-only");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values,
                      @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("MediaContentProvider is read-only");
    }
}