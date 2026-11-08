package com.watchpath.app.data.remote.dto;

/**
 * One entry from TMDB's /genre/{movie|tv}/list endpoint.
 *
 * Genre ids are not shared between movies and TV, so the id is only
 * meaningful together with the media type it was fetched for.
 */
public class Genre {

    public final int id;
    public final String name;

    public Genre(int id, String name) {
        this.id = id;
        this.name = name;
    }
}
