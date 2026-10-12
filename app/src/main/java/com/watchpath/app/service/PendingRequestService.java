package com.watchpath.app.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.watchpath.app.WatchPathApp;
import com.watchpath.app.data.MediaRepository;
import com.watchpath.app.data.local.MediaEntity;
import com.watchpath.app.data.remote.LinksRemoteDataSource;
import com.watchpath.app.util.NotificationHelper;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Started Service that checks the user's saved titles for newly-available
 * streams and posts a notification for any that just became available.
 *
 * Week 10 syllabus: a Service that does background work and communicates
 * with the user via notifications.
 *
 * Design notes:
 *  - Regular started service (not foreground). The job is short, and it's
 *    only triggered by a user tap in MyListActivity, so we don't hit the
 *    background-start restrictions of Android 12+.
 *  - The Service stops itself when the check finishes.
 *  - "Already notified" state lives in SharedPreferences, so a title is
 *    only announced once.
 */
public class PendingRequestService extends Service {

    private static final String TAG = "WatchPathService";

    public static final String ACTION_CHECK = "com.watchpath.app.action.CHECK_REQUESTS";

    private static final String PREFS_NAME = "watchpath.notified";
    private static final String PREF_IDS = "ids";

    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        executor.execute(this::checkPendingRequests);
        return START_NOT_STICKY; // don't restart if killed
    }

    private void checkPendingRequests() {
        try {
            MediaRepository repo = new MediaRepository(
                    WatchPathApp.getDatabase().mediaDao());
            LinksRemoteDataSource links = new LinksRemoteDataSource();

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Set<String> alreadyNotified = new HashSet<>(
                    prefs.getStringSet(PREF_IDS, Collections.emptySet()));

            List<MediaEntity> saved = repo.getAll();
            Log.d(TAG, "Checking " + saved.size() + " saved titles");

            boolean changed = false;
            for (MediaEntity m : saved) {
                String compositeId = m.type + "_" + m.id;
                if (alreadyNotified.contains(compositeId)) continue;

                List<String> urls = links.getLinks(m.id, m.type);
                if (!urls.isEmpty()) {
                    NotificationHelper.notify(
                            this,
                            (int) System.currentTimeMillis(),
                            getString(com.watchpath.app.R.string.notification_available_title, m.title),
                            getString(com.watchpath.app.R.string.notification_available_body));
                    alreadyNotified.add(compositeId);
                    changed = true;
                    Log.d(TAG, "Notified: " + m.title);
                }
            }

            if (changed) {
                prefs.edit().putStringSet(PREF_IDS, alreadyNotified).apply();
            }
            Log.d(TAG, "Check complete. Total notified: " + alreadyNotified.size());

        } catch (Exception e) {
            Log.e(TAG, "Check failed", e);
        } finally {
            stopSelf();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // not a bound service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}