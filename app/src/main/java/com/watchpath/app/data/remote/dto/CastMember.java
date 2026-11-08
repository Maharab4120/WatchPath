package com.watchpath.app.data.remote.dto;

/**
 * One cast entry from TMDB's /{movie|tv}/{id}/credits.
 */
public class CastMember {

    public final String name;
    public final String character;   // may be empty
    public final String profilePath; // may be null

    public CastMember(String name, String character, String profilePath) {
        this.name = name;
        this.character = character;
        this.profilePath = profilePath;
    }
}
