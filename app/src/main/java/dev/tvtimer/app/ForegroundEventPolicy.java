package dev.tvtimer.app;

final class ForegroundEventPolicy {
    private ForegroundEventPolicy() {
    }

    static boolean shouldReplaceActivePackage(
            String eventPackage,
            String ownPackage,
            boolean windowStateChanged
    ) {
        if (eventPackage == null || eventPackage.isEmpty()) {
            return false;
        }
        if (eventPackage.equals("android")
                || eventPackage.equals("com.android.systemui")
                || eventPackage.contains("inputmethod")
                || eventPackage.contains("keyboard")) {
            return false;
        }
        // Adding/removing our accessibility overlay also emits TYPE_WINDOWS_CHANGED.
        // A real Activity transition emits TYPE_WINDOW_STATE_CHANGED and must be accepted.
        return !eventPackage.equals(ownPackage) || windowStateChanged;
    }
}
