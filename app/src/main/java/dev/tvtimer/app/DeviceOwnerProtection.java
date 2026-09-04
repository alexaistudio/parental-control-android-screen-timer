package dev.tvtimer.app;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

final class DeviceOwnerProtection {
    private static final String TAG = "ScreenTimerOwner";

    private DeviceOwnerProtection() {
    }

    static boolean isDeviceOwner(Context context) {
        DevicePolicyManager manager = manager(context);
        return manager != null && manager.isDeviceOwnerApp(context.getPackageName());
    }

    static boolean ensureUninstallBlocked(Context context) {
        DevicePolicyManager manager = manager(context);
        if (manager == null || !manager.isDeviceOwnerApp(context.getPackageName())) {
            return false;
        }
        try {
            manager.setUninstallBlocked(
                    admin(context),
                    context.getPackageName(),
                    true
            );
            return manager.isUninstallBlocked(admin(context), context.getPackageName());
        } catch (RuntimeException exception) {
            Log.e(TAG, "Unable to enable device-owner uninstall protection", exception);
            return false;
        }
    }

    private static DevicePolicyManager manager(Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    private static ComponentName admin(Context context) {
        return new ComponentName(context, TimerDeviceAdminReceiver.class);
    }
}
