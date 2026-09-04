package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RemovalProtectionPolicyTest {
    @Test
    public void detectsCommonTvAppManagementAndUninstallScreens() {
        assertTrue(RemovalProtectionPolicy.isSensitiveScreen(
                "com.android.tv.settings",
                "com.android.tv.settings.device.apps.AppManagementActivity"
        ));
        assertTrue(RemovalProtectionPolicy.isSensitiveScreen(
                "com.google.android.permissioncontroller",
                "com.android.packageinstaller.UninstallerActivity"
        ));
        assertTrue(RemovalProtectionPolicy.isSensitiveScreen(
                "com.android.settings",
                "com.android.settings.Settings$AccessibilitySettingsActivity"
        ));
        assertTrue(RemovalProtectionPolicy.isSensitiveScreen(
                "android",
                "com.android.settings.DeviceAdminAdd"
        ));
        assertTrue(RemovalProtectionPolicy.isSensitiveScreen(
                "com.android.tv.settings",
                "com.android.tv.settings.MainSettings"
        ));
        assertTrue(RemovalProtectionPolicy.isSensitiveScreen(
                "com.xiaomi.mitv.settings",
                null
        ));
    }

    @Test
    public void ignoresNormalLauncherAndApplicationWindows() {
        assertFalse(RemovalProtectionPolicy.isSensitiveScreen(
                "com.google.android.tvlauncher",
                "com.google.android.tvlauncher.MainActivity"
        ));
        assertFalse(RemovalProtectionPolicy.isSensitiveScreen(
                "com.google.android.youtube.tv",
                "com.google.android.apps.youtube.tv.activity.ShellActivity"
        ));
    }

    @Test
    public void keepsProtectionWhileSettingsOwnsTheForeground() {
        assertTrue(RemovalProtectionPolicy.isProtectionPackage("com.android.tv.settings"));
        assertTrue(RemovalProtectionPolicy.isProtectionPackage("com.google.android.permissioncontroller"));
        assertFalse(RemovalProtectionPolicy.isProtectionPackage("com.google.android.youtube.tv"));
    }

    @Test
    public void waitsForAStableSensitiveScreenAndHonorsMaintenance() {
        assertFalse(RemovalProtectionPolicy.shouldBlock(true, 1_100L, 1_400L, false));
        assertTrue(RemovalProtectionPolicy.shouldBlock(true, 1_400L, 1_400L, false));
        assertFalse(RemovalProtectionPolicy.shouldBlock(false, 2_000L, 1_400L, false));
        assertFalse(RemovalProtectionPolicy.shouldBlock(true, 2_000L, 1_400L, true));
    }

    @Test
    public void dismissesProtectionOnlyForTheHomeNavigationReason() {
        assertTrue(RemovalProtectionPolicy.isHomeNavigationReason("homekey"));
        assertFalse(RemovalProtectionPolicy.isHomeNavigationReason("recentapps"));
        assertFalse(RemovalProtectionPolicy.isHomeNavigationReason(null));
    }
}
