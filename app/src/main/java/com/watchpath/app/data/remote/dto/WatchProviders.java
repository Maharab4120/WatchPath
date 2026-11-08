package com.watchpath.app.data.remote.dto;

import java.util.List;

/**
 * "Where to watch" data for one region, from TMDB's
 * /{movie|tv}/{id}/watch/providers endpoint.
 */
public class WatchProviders {

    /** A single streaming/rental service (e.g. "Netflix"). */
    public static class Provider {
        public final String name;
        public final String logoPath;

        public Provider(String name, String logoPath) {
            this.name = name;
            this.logoPath = logoPath;
        }
    }

    /** TMDB / JustWatch deep link for this title + region (may be null). */
    public final String link;
    /** The region the providers belong to, e.g. "US". */
    public final String region;
    public final List<Provider> providers;

    public WatchProviders(String link, String region, List<Provider> providers) {
        this.link = link;
        this.region = region;
        this.providers = providers;
    }
}
