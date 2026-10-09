package com.watchpath.app.data.remote.dto;

/**
 * Full details from TMDB's /movie/{id} or /tv/{id}.
 *
 * Movies and TV differ in a few fields; we normalize them so the UI doesn't
 * have to check the type everywhere:
 *   - movies expose "runtime" (minutes), TV exposes episode_run_time[]
 *   - TV also exposes number_of_seasons / number_of_episodes
 *   - movies use "title" / "release_date"; TV uses "name" / "first_air_date"
 */
public class MediaDetailsDto {

    public final int id;
    public final String mediaType;
    public final String title;
    public final String overview;
    public final String tagline;          // may be empty
    public final String posterPath;       // may be null
    public final String backdropPath;     // may be null
    public final double rating;
    public final String year;
    public final String genres;           // comma-separated, e.g. "Drama, Thriller"
    public final String runtimeOrSeasons; // "139 min" for movies, "5 seasons" for TV

    public MediaDetailsDto(int id, String mediaType, String title, String overview,
                           String tagline, String posterPath, String backdropPath,
                           double rating, String year, String genres,
                           String runtimeOrSeasons) {
        this.id = id;
        this.mediaType = mediaType;
        this.title = title;
        this.overview = overview;
        this.tagline = tagline;
        this.posterPath = posterPath;
        this.backdropPath = backdropPath;
        this.rating = rating;
        this.year = year;
        this.genres = genres;
        this.runtimeOrSeasons = runtimeOrSeasons;
    }
}