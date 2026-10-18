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
 *   requests/{type_id}      { id, type, title, requestCount, status }
 *   requests/{type_id}/users/{uid}   { requestedAt }  <- per-user dedup
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

    /** Helper to get the current device's UID. Waits for sign-in if it's still running. */
    private String getUid() throws Exception {
        return com.watchpath.app.util.AuthHelper.getUidBlocking();
    }


    /** Checks if the current user has already requested this title. */
    public boolean hasUserRequested(int id, String type) throws Exception {
        String uid = getUid();
        String compositeId = type + "_" + id;

        // Check if the user document exists in the subcollection
        DocumentSnapshot doc = Tasks.await(db.collection("requests")
                .document(compositeId)
                .collection("users")
                .document(uid)
                .get());
        return doc.exists();
    }

    /** How many users have requested this title. */
    public int getRequestCount(int id, String type) throws Exception {
        String compositeId = type + "_" + id;
        DocumentSnapshot doc = Tasks.await(db.collection("requests").document(compositeId).get());
        if (!doc.exists()) return 0;
        Long count = doc.getLong("requestCount");
        return count == null ? 0 : count.intValue();
    }

    /**
     * Records a request atomically. A Firestore transaction reads the user's doc
     * and only if it does NOT exist writes it AND increments the count, in one
     * all-or-nothing commit. Two taps or two devices can never double count.
     *
     * @return true if a new request was recorded, false if this user already requested it
     */
    public boolean requestTitle(int id, String type, String title) throws Exception {
        String uid = getUid();
        String compositeId = type + "_" + id;

        DocumentReference parentRef = db.collection("requests").document(compositeId);
        DocumentReference userRef = parentRef.collection("users").document(uid);

        return Tasks.await(db.runTransaction(transaction -> {
            // 1. READ first (transactions require reads before writes)
            DocumentSnapshot userSnap = transaction.get(userRef);
            if (userSnap.exists()) {
                return false; // already requested, write nothing
            }

            // 2. WRITE: mark this user...
            Map<String, Object> userData = new HashMap<>();
            userData.put("requestedAt", FieldValue.serverTimestamp());
            transaction.set(userRef, userData);

            // ...and bump the count, in the same commit
            Map<String, Object> parentData = new HashMap<>();
            parentData.put("id", id);
            parentData.put("type", type);
            parentData.put("title", title);
            parentData.put("status", "pending");
            parentData.put("requestCount", FieldValue.increment(1));
            transaction.set(parentRef, parentData, SetOptions.merge());

            return true;
        }));
    }
}