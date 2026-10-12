package com.watchpath.app.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.watchpath.app.R;

/**
 * Wraps notification-channel creation and posting.
 *
 * Week 10 syllabus: notifications are the standard way a Service talks to
 * the user without a UI.
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "watchpath_updates";
    private static final String CHANNEL_NAME = "WatchPath updates";
    private static final String CHANNEL_DESC = "Notifies when a requested title becomes available";

    private NotificationHelper() { }

    /** Call once, early (e.g. from Application.onCreate or first Activity). */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm =
                context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(CHANNEL_DESC);
        nm.createNotificationChannel(channel);
    }

    /** Posts a simple notification. Requires POST_NOTIFICATIONS on API 33+. */
    public static void notify(Context context, int id, String title, String body) {
        ensureChannel(context);

        Notification n = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build();

        try {
            NotificationManagerCompat.from(context).notify(id, n);
        } catch (SecurityException e) {
            // User hasn't granted POST_NOTIFICATIONS. Silently skip.
        }
    }
}