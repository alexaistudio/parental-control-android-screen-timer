package dev.tvtimer.app;

final class ForegroundEventPolicy {
    private ForegroundEventPolicy() {
    }

    static boolean shouldReplaceActivePackage(
            String eventPackage,
            String eventClass,
            String ownPackage,
            String ownActivityClass,
            boolean windowStateChanged
    ) {
        if (eventPackage == null || eventPackage.isEmpty()) {
            return false;
        }
        if (!windowStateChanged) {
            return false;
        }
        if (eventPackage.equals("android")
                || eventPackage.equals("com.android.systemui")
                || eventPackage.contains("inputmethod")
                || eventPackage.contains("keyboard")) {
            return false;
        }
        if (eventPackage.equals(ownPackage)) {
            return ownActivityClass.equals(eventClass);
        }
        return true;
    }
}
