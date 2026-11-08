package com.watchpath.app.data.remote;

import com.watchpath.app.BuildConfig;
import com.watchpath.app.data.remote.dto.CastMember;
import com.watchpath.app.data.remote.dto.MediaDetailsDto;
import com.watchpath.app.data.remote.dto.MediaDto;
import com.watchpath.app.util.Constants;
import com.watchpath.app.util.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

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
}