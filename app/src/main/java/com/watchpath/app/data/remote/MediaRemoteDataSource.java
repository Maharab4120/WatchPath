package com.watchpath.app.data.remote;

import com.watchpath.app.BuildConfig;
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

    public List<MediaDto> searchMulti(String query) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8");
        String url = Constants.TMDB_BASE_URL
                + "/search/multi"
                + "?api_key=" + BuildConfig.TMDB_API_KEY
                + "&query=" + encoded;

        String json = NetworkUtils.get(url);
        return parseSearchResponse(json);
    }

    private List<MediaDto> parseSearchResponse(String json) throws Exception {
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

    private String extractYear(String dateStr) {
        if (dateStr == null || dateStr.length() < 4) return "";
        return dateStr.substring(0, 4);
    }
}