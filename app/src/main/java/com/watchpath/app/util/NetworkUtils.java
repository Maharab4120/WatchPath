package com.watchpath.app.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Tiny helper around HttpURLConnection.
 *
 * Week 11 syllabus point: "HTTP/API requests, JSON/XML parsing."
 * We use raw HttpURLConnection + org.json (in MediaRemoteDataSource) rather
 * than Retrofit, so the HTTP call and the JSON parsing are both visible in
 * our own code - easier to explain at the Week 13 demo.
 *
 * NOTE: this performs blocking I/O. Always call from a background thread
 * (ExecutorService). Never from the main thread.
 */
public final class NetworkUtils {

    private NetworkUtils() { /* no instances */ }

    /**
     * Performs a GET and returns the full response body as a String.
     * Throws on any failure - callers must catch and surface a user-friendly error.
     */
    public static String get(String urlString) throws Exception {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(Constants.NETWORK_TIMEOUT_MS);
            conn.setReadTimeout(Constants.NETWORK_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new RuntimeException("HTTP " + status + " for " + urlString);
            }

            reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();

        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) { }
            if (conn != null) conn.disconnect();
        }
    }
}