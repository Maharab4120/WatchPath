package com.watchpath.app.service;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;

import com.watchpath.app.WatchPathApp;
import com.watchpath.app.data.MediaRepository;
import com.watchpath.app.data.local.MediaEntity;
import com.watchpath.app.data.remote.LinksRemoteDataSource;
import com.watchpath.app.util.NotificationHelper;
import com.watchpath.app.receiver.RequestsReadyReceiver;

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
 *  - Foreground service: it promotes itself with an ongoing progress
 *    notification ("Checking 2 of 12 titles…") and is started from a visible
 *    Activity, so the Android 12+ background-start restrictions don't apply.
 *  - The Service stops itself (and removes the notification) when the check
 *    finishes.
 *  - "Already notified" state lives in SharedPreferences, so a title is
 *    only announced once.
 */
public class PendingRequestService extends Service {

    private static final String TAG = "WatchPathService";

    public static final String ACTION_CHECK = "com.watchpath.app.action.CHECK_REQUESTS";

    /** Id of the ongoing foreground notification (and its progress updates). */
    public static final int NOTIFICATION_ID = 4101;

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
        Log.d(TAG, "Service started (foreground)");

        // Promote to foreground immediately: an ongoing progress notification
        // while the check runs. Admitting it up-front avoids the 5s start limit.
        startForeground(NOTIFICATION_ID, progressNotification(
                getString(com.watchpath.app.R.string.service_starting), 0, 0));

        executor.execute(this::checkPendingRequests);
        return START_NOT_STICKY; // don't restart if killed
    }

    /** Builds/updates the ongoing foreground notification. */
    private android.app.Notification progressNotification(String text, int current, int total) {
        return NotificationHelper.buildProgressNotification(
                this, getString(com.watchpath.app.R.string.service_checking_title),
                text, current, total);
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
            int total = saved.size();
            Log.d(TAG, "Checking " + total + " saved titles");

            boolean changed = false;
            for (int i = 0; i < total; i++) {
                MediaEntity m = saved.get(i);

                // Live tray progress: "Checking 3 of 12 titles…"
                startForeground(NOTIFICATION_ID, progressNotification(
                        getString(com.watchpath.app.R.string.service_check_progress, i + 1, total),
                        i + 1, total));

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
            Intent done = new Intent(RequestsReadyReceiver.ACTION_REQUESTS_READY);

            done.setPackage(getPackageName()); // keep it inside our own app
            done.putExtra(RequestsReadyReceiver.EXTRA_NOTIFIED_COUNT, alreadyNotified.size());
            sendBroadcast(done);

        } catch (Exception e) {
            Log.e(TAG, "Check failed", e);
        } finally {
            // Drop the ongoing notification and leave the foreground state.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
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