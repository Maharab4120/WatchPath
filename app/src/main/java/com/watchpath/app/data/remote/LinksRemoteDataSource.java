package com.watchpath.app.data.remote;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to Cloud Firestore - the backbone of the app's differentiator.
 *
 * Firestore's native API is asynchronous (Task<T>). We use Tasks.await() to
 * make it blocking, so we can call it from the same ExecutorService pattern
 * we already use for TMDB. The caller must be on a background thread - same
 * rule as NetworkUtils.
 *
 * Collections:
 *   movie_links/{movieId}   { links: [url1, url2, url3], title }
 *   tv_links/{tvId}         { links: [url1, url2, url3], title }
 *   requests/{type_id}      { id, type, title, requestCount, status, requestedAt }
 *
 * Note the composite ID for requests: "movie_550" or "tv_1399". TMDB ids are
 * per-namespace - movie 550 is Fight Club, TV 550 is something else.
 */
public class LinksRemoteDataSource {

    private final FirebaseFirestore db;

    public LinksRemoteDataSource() {
        this.db = FirebaseFirestore.getInstance();
    }

    // --- Links ---

    /**
     * Returns the curated list of URLs for a title, or an empty list if none
     * have been added by the admin yet.
     */
    public List<String> getLinks(int id, String type) throws Exception {
        String collection = "tv".equals(type) ? "tv_links" : "movie_links";
        DocumentSnapshot doc = Tasks.await(
                db.collection(collection).document(String.valueOf(id)).get());

        List<String> out = new ArrayList<>();
        if (!doc.exists()) return out;

        Object raw = doc.get("links");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof String) out.add((String) o);
            }
        }
        return out;
    }

    // --- Requests ---

    /**
     * Increments the request count for a title, creating the document if
     * it doesn't exist yet. FieldValue.increment(1) is atomic - safe if two
     * users request the same title simultaneously.
     */
    public void requestTitle(int id, String type, String title) throws Exception {
        String compositeId = type + "_" + id;
        DocumentReference ref = db.collection("requests").document(compositeId);

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("type", type);
        data.put("title", title);
        data.put("status", "pending");
        data.put("requestCount", FieldValue.increment(1));
        data.put("requestedAt", FieldValue.serverTimestamp());

        // merge() so we don't clobber other fields if the doc exists.
        Tasks.await(ref.set(data, SetOptions.merge()));
    }

    /** How many users have requested this title, or 0 if never requested. */
    public int getRequestCount(int id, String type) throws Exception {
        String compositeId = type + "_" + id;
        DocumentSnapshot doc = Tasks.await(
                db.collection("requests").document(compositeId).get());
        if (!doc.exists()) return 0;
        Long count = doc.getLong("requestCount");
        return count == null ? 0 : count.intValue();
    }
}