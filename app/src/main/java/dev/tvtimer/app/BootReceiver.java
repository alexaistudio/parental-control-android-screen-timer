package dev.tvtimer.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "TVTimerBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        ConfigStore store = new ConfigStore(context);
        if (store.isConfigured()) {
            DeviceOwnerProtection.ensureUninstallBlocked(context);
        }
        if (store.isEnforcementEnabled()) {
            store.getDayState(DayKey.localDay(System.currentTimeMillis()));
            Log.i(TAG, "Boot completed; the system accessibility service will resume enforcement");
        }
    }
}
