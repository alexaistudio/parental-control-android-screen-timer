package dev.tvtimer.app;

final class UsbRecoveryPolicy {
    static final String ACTION_MEDIA_MOUNTED = "android.intent.action.MEDIA_MOUNTED";

    private UsbRecoveryPolicy() {
    }

    static boolean shouldOpenRecovery(String action, boolean recoveryFilePresent) {
        return ACTION_MEDIA_MOUNTED.equals(action) && recoveryFilePresent;
    }
}
