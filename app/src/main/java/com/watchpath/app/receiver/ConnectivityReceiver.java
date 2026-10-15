package com.watchpath.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Runtime-registered receiver for network connectivity changes.
 *
 * Registered in MyListActivity.onStart() and unregistered in onStop(), so it
 * only runs while the screen is visible.
 *
 * Why runtime-registered: Android 8.0+ blocks manifest-declared receivers for
 * the CONNECTIVITY_ACTION implicit broadcast. Runtime registration is the
 * modern, correct approach for this broadcast - the exam answer is to know
 * the difference and explain why.
 */
public class ConnectivityReceiver extends BroadcastReceiver {

    public interface Listener {
        /** @param online true if there is currently a usable network. */
        void onConnectivityChanged(boolean online);
    }

    private final Listener listener;

    public ConnectivityReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        listener.onConnectivityChanged(isOnline(context));
    }

    /** Static helper so the activity can also check at register-time. */
    @SuppressWarnings("deprecation")
    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }
}