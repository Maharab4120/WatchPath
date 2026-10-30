package com.watchpath.app.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.watchpath.app.data.local.MediaDao;
import com.watchpath.app.data.local.MediaEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the signed-in user's My List in sync with Firebase Realtime Database.
 *
 *   Room (local cache)  <-->  users/{uid}/mylist/{type}_{id}  (cloud)
 *
 * Guest (anonymous) users are never synced: everything here is a no-op for them.
 */
public final class CloudListSync {

    private static final String TAG = "CloudListSync";
    private static final String PREFS = "cloud_sync_prefs";
    private static final String KEY_MERGED_UID = "merged_uid";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CloudListSync() {}

    // ---------- helpers ----------

    /** UID of the logged-in (non-guest) account, or null in guest mode. */
    private static String accountUid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return (u == null || u.isAnonymous()) ? null : u.getUid();
    }

    private static DatabaseReference listRef(String uid) {
        return FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("mylist");
    }

    private static String key(int id, String type) {
        return type + "_" + id; // movie 550 and tv 550 are different titles
    }

    private static void finish(Runnable onDone) {
        if (onDone != null) MAIN.post(onDone);
    }

    // ---------- single-item writes (called by MediaRepository) ----------

    public static void push(MediaEntity item) {
        String uid = accountUid();
        if (uid == null) return; // guest mode

        Map<String, Object> m = new HashMap<>();
        m.put("id", item.id);
        m.put("type", item.type);
        m.put("title", item.title);
        m.put("posterPath", item.posterPath);
        m.put("overview", item.overview);
        m.put("rating", item.rating);
        m.put("year", item.year);
        m.put("status", item.status);
        m.put("addedAt", item.addedAt);

        listRef(uid).child(key(item.id, item.type)).setValue(m)
                .addOnFailureListener(e -> Log.e(TAG, "Cloud write failed", e));
    }

    public static void delete(int id, String type) {
        String uid = accountUid();
        if (uid == null) return;

        listRef(uid).child(key(id, type)).removeValue()
                .addOnFailureListener(e -> Log.e(TAG, "Cloud delete failed", e));
    }

    // ---------- full sync ----------

    /**
     * Downloads the account's list into Room.
     *  - Cloud copy wins when the same title exists in both places.
     *  - First sync for this account: local-only items (saved as a guest) are uploaded.
     *  - Later syncs: local-only items were deleted elsewhere, so they are removed here.
     * onDone runs on the main thread, also when the sync fails or there is no account.
     */
    public static void syncNow(Context context, MediaDao dao, Runnable onDone) {
        final String uid = accountUid();
        if (uid == null) { finish(onDone); return; }

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final boolean firstSyncForAccount = !uid.equals(prefs.getString(KEY_MERGED_UID, ""));

        listRef(uid).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                // Offline or error: change nothing, so we can never delete data by mistake
                Log.e(TAG, "Cloud read failed", task.getException());
                finish(onDone);
                return;
            }
            DataSnapshot snap = task.getResult();

            IO.execute(() -> {
                try {
                    Set<String> cloudKeys = new HashSet<>();
                    for (DataSnapshot child : snap.getChildren()) {
                        MediaEntity e = fromSnapshot(child);
                        if (e == null) continue;
                        cloudKeys.add(key(e.id, e.type));
                        dao.upsert(e); // cloud copy wins
                    }
                    for (MediaEntity local : dao.getAll()) {
                        if (cloudKeys.contains(key(local.id, local.type))) continue;
                        if (firstSyncForAccount) push(local);              // guest items join the account
                        else dao.delete(local.id, local.type);             // removed on another device
                    }
                    if (firstSyncForAccount) {
                        prefs.edit().putString(KEY_MERGED_UID, uid).apply();
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Sync failed", ex);
                }
                finish(onDone);
            });
        });
    }

    /** Called on logout: wipe the local list (the cloud copy stays) and forget the merge marker. */
    public static void clearLocal(Context context, MediaDao dao, Runnable onDone) {
        IO.execute(() -> {
            try {
                dao.deleteAll();
                context.getApplicationContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().remove(KEY_MERGED_UID).apply();
            } catch (Exception ex) {
                Log.e(TAG, "Clearing local list failed", ex);
            }
            finish(onDone);
        });
    }

    private static MediaEntity fromSnapshot(DataSnapshot s) {
        Integer id = s.child("id").getValue(Integer.class);
        String type = s.child("type").getValue(String.class);
        String title = s.child("title").getValue(String.class);
        if (id == null || type == null || title == null) return null; // malformed, skip

        MediaEntity e = new MediaEntity();
        e.id = id;
        e.type = type;
        e.title = title;
        e.posterPath = s.child("posterPath").getValue(String.class);
        e.overview = s.child("overview").getValue(String.class);
        Double rating = s.child("rating").getValue(Double.class);
        e.rating = rating == null ? 0 : rating;
        String year = s.child("year").getValue(String.class);
        e.year = year == null ? "" : year;
        String status = s.child("status").getValue(String.class);
        e.status = status == null ? "Planned" : status;
        Long added = s.child("addedAt").getValue(Long.class);
        e.addedAt = added == null ? System.currentTimeMillis() : added;
        return e;
    }
}