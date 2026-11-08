package com.watchpath.app.data.remote;

import com.watchpath.app.data.remote.dto.MediaDto;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The user's selections for a /discover query: media type, genres, a single
 * release year and a minimum TMDB rating. Defaults mean "no constraint".
 *
 * Serializable so a snapshot can travel through Fragment arguments/results.
 */
public class DiscoverQuery implements Serializable {

    public String mediaType = MediaDto.TYPE_MOVIE;

    /** TMDB genre ids; ids differ between movies and TV. */
    public final Set<Integer> genreIds = new LinkedHashSet<>();

    /** 4-digit year, or 0 for "any year". */
    public int year = 0;

    /** Minimum vote_average (0-10), or 0 for "any rating". */
    public double minRating = 0;

    public boolean isEmpty() {
        return genreIds.isEmpty() && year == 0 && minRating == 0;
    }

    /** Snapshot so a background page-load is unaffected by later edits. */
    public DiscoverQuery copy() {
        DiscoverQuery c = new DiscoverQuery();
        c.mediaType = mediaType;
        c.genreIds.addAll(genreIds);
        c.year = year;
        c.minRating = minRating;
        return c;
    }
}
