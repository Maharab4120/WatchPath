package com.watchpath.app.data.remote;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Given a list of candidate URLs for a title, returns the first one that
 * appears reachable. Used by the Stream button to walk the admin-curated
 * fallback chain without opening dead links.
 *
 * How it decides: a short HEAD request against each URL. Any 2xx or 3xx
 * response counts as "reachable". Anything else (timeout, 4xx, 5xx, DNS
 * failure) moves on to the next URL.
 *
 * Honest caveat: some streaming sites respond 200 to any request but redirect
 * you to a page that doesn't have the title, or block HEAD requests. So if
 * every probe fails, we fall back to the first link rather than telling the
 * user "nothing works". This is a heuristic, not a guarantee - the admin
 * curates the list, and our job is to try them in order.
 *
 * Blocking I/O - call from a background thread only.
 */
public class StreamResolver {

    public static class Result {
        public final boolean found;
        public final String url;

        Result(boolean found, String url) {
            this.found = found;
            this.url = url;
        }
    }

    /** Tries each URL in order; returns the first that looks reachable. */
    public Result resolve(List<String> links) {
        if (links == null || links.isEmpty()) {
            return new Result(false, null);
        }

        for (String url : links) {
            if (looksReachable(url)) {
                return new Result(true, url);
            }
        }

        // All probes failed - fall back to the first link rather than
        // dead-ending. The user can still see where the admin pointed them.
        return new Result(true, links.get(0));
    }

    private boolean looksReachable(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(4_000);
            conn.setReadTimeout(4_000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android) WatchPath/1.0");

            int code = conn.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}