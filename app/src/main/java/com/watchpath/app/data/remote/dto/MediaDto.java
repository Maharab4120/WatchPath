package com.watchpath.app.data.remote.dto;

/**
 * One search result from TMDB's /search/multi endpoint.
 *
 * TMDB returns movies and TV shows with slightly different field names
 * (movies use "title" + "release_date", TV uses "name" + "first_air_date").
 * We normalize both into single fields so the UI doesn't have to care.
 */
public class MediaDto {

    public static final String TYPE_MOVIE = "movie";
    public static final String TYPE_TV = "tv";

    public final int id;
    public final String mediaType;    // TYPE_MOVIE or TYPE_TV
    public final String title;         // normalized from title/name
    public final String posterPath;    // may be null; use Constants.TMDB_IMAGE_BASE + size + path
    public final String overview;
    public final double rating;        // 0.0 if missing
    public final String year;          // 4-digit string, or "" if unknown

    public MediaDto(int id, String mediaType, String title, String posterPath,
                    String overview, double rating, String year) {
        this.id = id;
        this.mediaType = mediaType;
        this.title = title;
        this.posterPath = posterPath;
        this.overview = overview;
        this.rating = rating;
        this.year = year;
    }

    @Override
    public String toString() {
        return title + " (" + year + ") [" + mediaType + ", id=" + id + ", rating=" + rating + "]";
    }
}