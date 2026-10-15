package com.watchpath.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Manifest-declared receiver for a custom app-wide broadcast.
 *
 * This is the textbook pattern - a manifest-declared receiver that fires
 * whenever the broadcast is sent, even if the app isn't running. In our case
 * PendingRequestService sends ACTION_REQUESTS_READY after it finishes, and
 * this receiver logs the event (and could, in a larger app, trigger UI work).
 *
 * Custom broadcasts like this one are the safe way to use a manifest-declared
 * receiver on Android 8+, because the platform only blocks IMPLICIT system
 * broadcasts. Our own app-to-app broadcast is explicit and always works.
 */
public class RequestsReadyReceiver extends BroadcastReceiver {

    public static final String ACTION_REQUESTS_READY =
            "com.watchpath.app.action.REQUESTS_READY";
    public static final String EXTRA_NOTIFIED_COUNT = "notified_count";

    private static final String TAG = "WatchPathBroadcast";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!ACTION_REQUESTS_READY.equals(intent.getAction())) return;

        int count = intent.getIntExtra(EXTRA_NOTIFIED_COUNT, 0);
        Log.d(TAG, "REQUESTS_READY received, notified=" + count);
    }
}