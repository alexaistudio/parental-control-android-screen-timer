package dev.tvtimer.app;

import java.util.Locale;

final class RemovalProtectionPolicy {
    private static final String HOME_NAVIGATION_REASON = "homekey";
    private RemovalProtectionPolicy() {
    }

    static boolean isSensitiveScreen(String packageName, String className) {
        String packageValue = lower(packageName);
        String classValue = lower(className);
        if (isSettingsPackage(packageValue) || isInstallerPackage(packageValue)) {
            return true;
        }
        if (classValue.isEmpty()) {
            return false;
        }
        if (containsAny(
                classValue,
                "uninstall",
                "deletepackage",
                "appmanagement",
                "manageapplications",
                "installedappdetails",
                "applicationdetails",
                "accessibilitysettings",
                "accessibilityservice",
                "deviceadmin"
        )) {
            return true;
        }
        if (packageValue.contains("settings") && classValue.contains("accessibility")) {
            return true;
        }
        return isInstallerPackage(packageValue)
                && containsAny(classValue, "package", "install", "permission");
    }

    static boolean isProtectionPackage(String packageName) {
        String value = lower(packageName);
        return value.equals("android")
                || isSettingsPackage(value)
                || isInstallerPackage(value);
    }

    static boolean shouldBlock(boolean sensitiveScreenActive, long nowElapsed,
                               long eligibleAtElapsed, boolean maintenanceAllowed) {
        return sensitiveScreenActive
                && nowElapsed >= eligibleAtElapsed
                && !maintenanceAllowed;
    }

    static boolean isHomeNavigationReason(String reason) {
        return HOME_NAVIGATION_REASON.equals(reason);
    }

    private static boolean isSettingsPackage(String value) {
        return value.contains("settings")
                || value.contains("securitycenter")
                || value.contains("security")
                || value.contains("systemmanager")
                || value.contains("packagecontrol");
    }

    private static boolean isInstallerPackage(String value) {
        return value.contains("packageinstaller")
                || value.contains("permissioncontroller");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
