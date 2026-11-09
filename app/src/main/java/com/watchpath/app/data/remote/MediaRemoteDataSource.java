package com.watchpath.app.data.remote;

import com.watchpath.app.BuildConfig;
import com.watchpath.app.data.remote.dto.CastMember;
import com.watchpath.app.data.remote.dto.Genre;
import com.watchpath.app.data.remote.dto.MediaDetailsDto;
import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.data.remote.dto.WatchProviders;
import com.watchpath.app.util.Constants;
import com.watchpath.app.util.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Talks to TMDB. Wraps every network call + JSON parse in a plain Java method.
 *
 * Week 11 syllabus: "HTTP/API requests, JSON/XML parsing."
 * NOTE: methods here perform blocking I/O. Always call from a background thread.
 */
public class MediaRemoteDataSource {

    /** Search with paging. TMDB pages start at 1. */
    public List<MediaDto> searchMulti(String query, int page) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8");
        String url = Constants.TMDB_BASE_URL
                + "/search/multi"
                + "?api_key=" + BuildConfig.TMDB_API_KEY
                + "&query=" + encoded
                + "&page=" + page;

        String json = NetworkUtils.get(url);
        return parseSearchResponse(json);
    }

    /**
     * Calls TMDB /trending/all/week with paging. The JSON structure is
     * identical to /search/multi, so we reuse parseSearchResponse().
     */
    public List<MediaDto> getTrending(int page) throws Exception {
        String url = Constants.TMDB_BASE_URL
                + "/trending/all/week"
                + "?api_key=" + BuildConfig.TMDB_API_KEY
                + "&page=" + page;

        String json = NetworkUtils.get(url);
        return parseSearchResponse(json);
    }

    /** Reads total_pages. TMDB never serves beyond page 500, so we cap it. */
    public static int parseTotalPages(String json) throws Exception {
        int total = new JSONObject(json).optInt("total_pages", 1);
        return Math.max(1, Math.min(total, 500));
    }

    public List<MediaDto> parseSearchResponse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray results = root.getJSONArray("results");

        List<MediaDto> out = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.getJSONObject(i);
            String mediaType = o.optString("media_type", "");

            if (!MediaDto.TYPE_MOVIE.equals(mediaType) && !MediaDto.TYPE_TV.equals(mediaType)) {
                continue;
            }

            int id = o.optInt("id");
            String title = MediaDto.TYPE_MOVIE.equals(mediaType)
                    ? o.optString("title")
                    : o.optString("name");
            String posterPath = o.isNull("poster_path") ? null : o.optString("poster_path");
            String overview = o.optString("overview");
            double rating = o.optDouble("vote_average", 0.0);
            String dateStr = MediaDto.TYPE_MOVIE.equals(mediaType)
                    ? o.optString("release_date")
                    : o.optString("first_air_date");
            String year = extractYear(dateStr);

            out.add(new MediaDto(id, mediaType, title, posterPath, overview, rating, year));
        }
        return out;
    }

    public MediaDetailsDto getDetails(int id, String mediaType) throws Exception {
        String endpoint = MediaDto.TYPE_TV.equals(mediaType) ? "tv" : "movie";
        String url = Constants.TMDB_BASE_URL
                + "/" + endpoint + "/" + id
                + "?api_key=" + BuildConfig.TMDB_API_KEY;

        String json = NetworkUtils.get(url);
        return parseDetailsResponse(json, mediaType);
    }

    private MediaDetailsDto parseDetailsResponse(String json, String mediaType) throws Exception {
        JSONObject o = new JSONObject(json);
        boolean isTv = MediaDto.TYPE_TV.equals(mediaType);

        String title = isTv ? o.optString("name") : o.optString("title");
        String tagline = o.optString("tagline", "");
        String posterPath = o.isNull("poster_path") ? null : o.optString("poster_path");
        String backdropPath = o.isNull("backdrop_path") ? null : o.optString("backdrop_path");
        String overview = o.optString("overview");
        double rating = o.optDouble("vote_average", 0.0);

        String dateStr = isTv ? o.optString("first_air_date") : o.optString("release_date");
        String year = extractYear(dateStr);

        StringBuilder genres = new StringBuilder();
        JSONArray genresArr = o.optJSONArray("genres");
        if (genresArr != null) {
            for (int i = 0; i < genresArr.length(); i++) {
                if (i > 0) genres.append(", ");
                genres.append(genresArr.getJSONObject(i).optString("name"));
            }
        }

        String runtimeOrSeasons;
        if (isTv) {
            int seasons = o.optInt("number_of_seasons", 0);
            runtimeOrSeasons = seasons == 1 ? "1 season" : seasons + " seasons";
        } else {
            int runtime = o.optInt("runtime", 0);
            runtimeOrSeasons = runtime > 0 ? runtime + " min" : "";
        }

        return new MediaDetailsDto(
                o.optInt("id"), mediaType, title, overview, tagline,
                posterPath, backdropPath, rating, year,
                genres.toString(), runtimeOrSeasons);
    }

    public static String extractYear(String dateStr) {
        if (dateStr == null || dateStr.length() < 4) return "";
        return dateStr.substring(0, 4);
    }

    /**
     * Looks up a YouTube trailer key via TMDB's /{movie|tv}/{id}/videos.
     * Prefers an official "Trailer", then any trailer, then any YouTube clip.
     * Returns null when nothing suitable exists. Blocking call.
     */
    public String getTrailerKey(int id, String mediaType) throws Exception {
        String endpoint = MediaDto.TYPE_TV.equals(mediaType) ? "tv" : "movie";
        String url = Constants.TMDB_BASE_URL
                + "/" + endpoint + "/" + id + "/videos"
                + "?api_key=" + BuildConfig.TMDB_API_KEY;

        String json = NetworkUtils.get(url);
        return parseTrailerKey(json);
    }

    /** Pure JSON -> key helper, kept static so it can be unit-tested. */
    public static String parseTrailerKey(String json) throws Exception {
        JSONArray results = new JSONObject(json).optJSONArray("results");
        if (results == null) return null;

        String firstTrailer = null;
        String firstAny = null;
        for (int i = 0; i < results.length(); i++) {
            JSONObject v = results.getJSONObject(i);
            if (!"YouTube".equalsIgnoreCase(v.optString("site"))) continue;

            String key = v.optString("key", "");
            if (key.isEmpty()) continue;

            if ("Trailer".equalsIgnoreCase(v.optString("type"))) {
                if (v.optBoolean("official", false)) return key;
                if (firstTrailer == null) firstTrailer = key;
            } else if (firstAny == null) {
                firstAny = key;
            }
        }
        return firstTrailer != null ? firstTrailer : firstAny;
    }

    /**
     * TMDB /{movie|tv}/{id}/recommendations. The results share the parent's
     * media type, so we parse them with an explicit type (TMDB omits
     * "media_type" here). Blocking call.
     */
    public List<MediaDto> getRecommendations(int id, String mediaType) throws Exception {
        String endpoint = MediaDto.TYPE_TV.equals(mediaType) ? "tv" : "movie";
        String url = Constants.TMDB_BASE_URL
                + "/" + endpoint + "/" + id + "/recommendations"
                + "?api_key=" + BuildConfig.TMDB_API_KEY;

        String json = NetworkUtils.get(url);
        return parseTitleList(json, mediaType);
    }

    /**
     * Parses a "results" array of movie/TV objects that all share one known
     * media type. Unlike parseSearchResponse(), it does not require a
     * "media_type" field on each item. Kept static so it can be unit-tested.
     */
    public static List<MediaDto> parseTitleList(String json, String mediaType) throws Exception {
        JSONArray results = new JSONObject(json).optJSONArray("results");
        List<MediaDto> out = new ArrayList<>();
        if (results == null) return out;

        boolean isTv = MediaDto.TYPE_TV.equals(mediaType);
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.getJSONObject(i);
            int id = o.optInt("id");

            String title = isTv ? o.optString("name") : o.optString("title");
            if (title.isEmpty()) continue;

            String posterPath = o.isNull("poster_path") ? null : o.optString("poster_path");
            String overview = o.optString("overview");
            double rating = o.optDouble("vote_average", 0.0);
            String dateStr = isTv ? o.optString("first_air_date") : o.optString("release_date");

            out.add(new MediaDto(id, mediaType, title, posterPath, overview, rating,
                    extractYear(dateStr)));
        }
        return out;
    }

    /**
     * TMDB /discover/{movie|tv} with optional genre, year and minimum-rating
     * filters. Reuses parseTitleList() because every result shares the query's
     * media type (TMDB omits "media_type" here). Blocking call.
     */
    public List<MediaDto> getDiscover(DiscoverQuery query, int page) throws Exception {
        String mediaType = MediaDto.TYPE_TV.equals(query.mediaType)
                ? MediaDto.TYPE_TV : MediaDto.TYPE_MOVIE;

        StringBuilder url = new StringBuilder(Constants.TMDB_BASE_URL)
                .append("/discover/").append(mediaType)
                .append("?api_key=").append(BuildConfig.TMDB_API_KEY)
                .append("&sort_by=popularity.desc")
                .append("&page=").append(page);

        // with_genres is a comma-separated list of ids.
        if (!query.genreIds.isEmpty()) {
            StringBuilder genres = new StringBuilder();
            for (int id : query.genreIds) {
                if (genres.length() > 0) genres.append(',');
                genres.append(id);
            }
            url.append("&with_genres=").append(genres);
        }

        if (query.year > 0) {
            // Movies filter on primary_release_year, TV on first_air_date_year.
            String yearParam = MediaDto.TYPE_TV.equals(mediaType)
                    ? "first_air_date_year" : "primary_release_year";
            url.append('&').append(yearParam).append('=').append(query.year);
        }

        if (query.minRating > 0) {
            url.append("&vote_average.gte=")
                    .append(String.format(Locale.US, "%.1f", query.minRating));
        }

        String json = NetworkUtils.get(url.toString());
        return parseTitleList(json, mediaType);
    }

    /**
     * TMDB /genre/{movie|tv}/list -> the selectable genres for a media type.
     * Genre ids are media-type specific. Blocking call.
     */
    public List<Genre> getGenres(String mediaType) throws Exception {
        String endpoint = MediaDto.TYPE_TV.equals(mediaType) ? "tv" : "movie";
        String url = Constants.TMDB_BASE_URL
                + "/genre/" + endpoint + "/list"
                + "?api_key=" + BuildConfig.TMDB_API_KEY;

        String json = NetworkUtils.get(url);
        return parseGenres(json);
    }

    /** Parses the "genres" array of /genre/{movie|tv}/list. Kept static for tests. */
    public static List<Genre> parseGenres(String json) throws Exception {
        JSONArray arr = new JSONObject(json).optJSONArray("genres");
        List<Genre> out = new ArrayList<>();
        if (arr == null) return out;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String name = o.optString("name", "");
            if (name.isEmpty()) continue;
            out.add(new Genre(o.optInt("id"), name));
        }
        return out;
    }

    /**
     * TMDB /{movie|tv}/{id}/credits -> the top-billed cast. Blocking call.
     */
    public List<CastMember> getCredits(int id, String mediaType) throws Exception {
        String endpoint = MediaDto.TYPE_TV.equals(mediaType) ? "tv" : "movie";
        String url = Constants.TMDB_BASE_URL
                + "/" + endpoint + "/" + id + "/credits"
                + "?api_key=" + BuildConfig.TMDB_API_KEY;

        String json = NetworkUtils.get(url);
        return parseCredits(json);
    }

    /** Parses the "cast" array, capped at the first 20 entries. */
    public static List<CastMember> parseCredits(String json) throws Exception {
        JSONArray cast = new JSONObject(json).optJSONArray("cast");
        List<CastMember> out = new ArrayList<>();
        if (cast == null) return out;

        for (int i = 0; i < cast.length() && out.size() < 20; i++) {
            JSONObject o = cast.getJSONObject(i);
            String name = o.optString("name", "");
            if (name.isEmpty()) continue;

            String profilePath = o.isNull("profile_path") ? null : o.optString("profile_path");
            if (profilePath != null && profilePath.isEmpty()) profilePath = null;

            out.add(new CastMember(name, o.optString("character", ""), profilePath));
        }
        return out;
    }

    /**
     * TMDB /{movie|tv}/{id}/watch/providers -> the official streaming services
     * for the requested region. The region comes from the user's Settings
     * override; when blank it falls back to the device region, then to the US,
     * then to the first region TMDB returns. Blocking call.
     */
    public WatchProviders getWatchProviders(int id, String mediaType, String regionOverride) throws Exception {
        String endpoint = MediaDto.TYPE_TV.equals(mediaType) ? "tv" : "movie";
        String url = Constants.TMDB_BASE_URL
                + "/" + endpoint + "/" + id + "/watch/providers"
                + "?api_key=" + BuildConfig.TMDB_API_KEY;

        String json = NetworkUtils.get(url);

        String region = regionOverride;
        if (region == null || region.isEmpty()) region = Locale.getDefault().getCountry();
        if (region == null || region.isEmpty()) region = "US";
        return parseWatchProviders(json, region);
    }

    /**
     * Picks the requested region out of the "results" map and flattens the
     * provider lists, preferring subscription ("flatrate"), then free/ad-supported,
     * then rent/buy. Kept static so it can be unit-tested.
     */
    public static WatchProviders parseWatchProviders(String json, String region) throws Exception {
        JSONObject results = new JSONObject(json).optJSONObject("results");
        if (results == null) return new WatchProviders(null, region, new ArrayList<>());

        JSONObject block = results.optJSONObject(region);
        String usedRegion = region;
        if (block == null) {
            block = results.optJSONObject("US");
            usedRegion = "US";
        }
        if (block == null && results.length() > 0) {
            usedRegion = results.keys().next();
            block = results.optJSONObject(usedRegion);
        }
        if (block == null) return new WatchProviders(null, region, new ArrayList<>());

        String link = block.isNull("link") ? null : block.optString("link", null);
        if (link != null && link.isEmpty()) link = null;

        List<WatchProviders.Provider> providers = new ArrayList<>();
        collectProviders(block.optJSONArray("flatrate"), providers);
        collectProviders(block.optJSONArray("free"), providers);
        collectProviders(block.optJSONArray("ads"), providers);
        if (providers.isEmpty()) {
            collectProviders(block.optJSONArray("rent"), providers);
            collectProviders(block.optJSONArray("buy"), providers);
        }
        return new WatchProviders(link, usedRegion, providers);
    }

    /** Appends unique providers (by name) from a JSON array onto {@code out}. */
    private static void collectProviders(JSONArray arr, List<WatchProviders.Provider> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;

            String name = o.optString("provider_name", "");
            if (name.isEmpty()) continue;

            boolean duplicate = false;
            for (WatchProviders.Provider p : out) {
                if (p.name.equals(name)) { duplicate = true; break; }
            }
            if (duplicate) continue;

            String logoPath = o.isNull("logo_path") ? null : o.optString("logo_path");
            if (logoPath != null && logoPath.isEmpty()) logoPath = null;

            out.add(new WatchProviders.Provider(name, logoPath));
        }
    }
}