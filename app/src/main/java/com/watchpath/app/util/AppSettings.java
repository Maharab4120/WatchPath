package com.watchpath.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatDelegate;

import java.util.Locale;

/**
 * User preferences for the Settings screen: theme, region override for
 * "Where to watch", and whether notifications are shown.
 *
 * Stored in a single SharedPreferences file so the whole screen reads/writes
 * one place.
 */
public final class AppSettings {

    public static final String PREFS = "watchpath_settings";

    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_REGION = "region_override";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";

    /** Theme choices, mapped to AppCompatDelegate night modes in {@link #applyTheme}. */
    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private AppSettings() { }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // --- Theme ---

    public static int getThemeMode(Context context) {
        return prefs(context).getInt(KEY_THEME, THEME_SYSTEM);
    }

    public static void setThemeMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_THEME, mode).apply();
        applyTheme(context);
    }

    /** Applies the stored theme. Call from Application.onCreate and on every change. */
    public static void applyTheme(Context context) {
        int mode;
        switch (getThemeMode(context)) {
            case THEME_LIGHT:
                mode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case THEME_DARK:
                mode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            default:
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    // --- Region override for "Where to watch" ---

    /** Raw user override, or "" when none has been set. */
    public static String getRegionOverride(Context context) {
        return prefs(context).getString(KEY_REGION, "");
    }

    public static void setRegionOverride(Context context, String region) {
        String value = region == null ? "" : region.trim().toUpperCase(Locale.US);
        prefs(context).edit().putString(KEY_REGION, value).apply();
    }

    /**
     * The region to query TMDB with. Useful when the device region returns no
     * providers: the user can force another one. Falls back to the device
     * region, then to "US".
     */
    public static String resolveRegion(Context context) {
        String override = getRegionOverride(context);
        if (!TextUtils.isEmpty(override)) return override;

        String device = Locale.getDefault().getCountry();
        if (TextUtils.isEmpty(device)) return "US";
        return device;
    }

    // --- Notifications ---

    public static boolean isNotificationsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NOTIFICATIONS, true);
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }
}
