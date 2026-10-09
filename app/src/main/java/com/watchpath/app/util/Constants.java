package com.watchpath.app.util;

/**
 * App-wide constants. The TMDB API key is NOT here - it lives in BuildConfig
 * (populated from local.properties at build time) so it never appears in
 * source control.
 */
public final class Constants {

    private Constants() { /* no instances */ }

    public static final String TMDB_BASE_URL = "https://api.themoviedb.org/3";
    public static final String TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p";

    public static final String TMDB_POSTER_SIZE = "w342";   // grid thumbnail
    public static final String TMDB_BACKDROP_SIZE = "w780"; // detail header

    public static final int NETWORK_TIMEOUT_MS = 15_000;
}