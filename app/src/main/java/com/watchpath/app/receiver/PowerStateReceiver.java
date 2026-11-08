package com.watchpath.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.provider.Settings;

/**
 * Runtime-registered receiver for device power-state transitions:
 * low / recovered battery and airplane mode.
 *
 * Week 10 syllabus: a BroadcastReceiver reacting to system broadcasts.
 *
 * Why runtime-registered: like ConnectivityReceiver, this keeps the receiver
 * alive only while My List is on screen, and it works on every supported API
 * level without relying on implicit-broadcast exemptions.
 */
public class PowerStateReceiver extends BroadcastReceiver {

    /** Called whenever the battery or airplane-mode state changes. */
    public interface Listener {
        void onPowerStateChanged(boolean lowBattery, boolean airplaneMode);
    }

    private final Listener listener;
    private boolean lowBattery;
    private boolean airplaneMode;

    public PowerStateReceiver(Listener listener, boolean lowBattery, boolean airplaneMode) {
        this.listener = listener;
        this.lowBattery = lowBattery;
        this.airplaneMode = airplaneMode;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        switch (intent.getAction()) {
            case Intent.ACTION_BATTERY_LOW:
                lowBattery = true;
                break;
            case Intent.ACTION_BATTERY_OKAY:
                lowBattery = false;
                break;
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                airplaneMode = intent.getBooleanExtra("state", airplaneMode);
                break;
            default:
                return;
        }
        listener.onPowerStateChanged(lowBattery, airplaneMode);
    }

    /**
     * True while the battery is low and not charging. Reads the sticky
     * ACTION_BATTERY_CHANGED broadcast so the state is known the moment we
     * register, not only after the next transition.
     */
    public static boolean isLowBattery(Context context) {
        Intent battery = context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return false;

        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        if (charging) return false;

        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) return false;

        return level * 100 / scale <= 15;
    }

    /** Reads the current airplane-mode setting. */
    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(
                context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
}
