package dev.tvtimer.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

final class LauncherProfileManager {
    private LauncherProfileManager() {
    }

    static void apply(Context context, String selectedProfile) {
        LauncherProfile.requireSupported(selectedProfile);
        PackageManager manager = context.getPackageManager();
        String selectedClass = LauncherProfile.aliasClassName(selectedProfile);

        manager.setComponentEnabledSetting(
                new ComponentName(context.getPackageName(), selectedClass),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );
        for (String profile : LauncherProfile.VALUES) {
            String className = LauncherProfile.aliasClassName(profile);
            if (!className.equals(selectedClass)) {
                manager.setComponentEnabledSetting(
                        new ComponentName(context.getPackageName(), className),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                );
            }
        }
    }
}
