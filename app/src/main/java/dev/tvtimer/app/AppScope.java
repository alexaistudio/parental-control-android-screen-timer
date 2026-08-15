package dev.tvtimer.app;

import java.util.Collections;
import java.util.Set;

public final class AppScope {
    public static final String ALL = "all";
    public static final String SELECTED = "selected";

    private AppScope() {
    }

    public static boolean isTarget(
            String mode,
            String packageName,
            String ownPackage,
            Set<String> selectedPackages
    ) {
        if (packageName == null || packageName.isEmpty() || isIgnored(packageName, ownPackage)) {
            return false;
        }
        if (SELECTED.equals(mode)) {
            Set<String> selected = selectedPackages == null
                    ? Collections.emptySet()
                    : selectedPackages;
            return selected.contains(packageName);
        }
        return true;
    }

    public static boolean isIgnored(String packageName, String ownPackage) {
        if (packageName == null) {
            return true;
        }
        return packageName.equals(ownPackage)
                || packageName.equals("android")
                || packageName.equals("com.android.systemui")
                || packageName.contains("inputmethod")
                || packageName.contains("keyboard");
    }
}
