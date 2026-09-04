package dev.tvtimer.app;

final class RecoveryFilePolicy {
    private RecoveryFilePolicy() {
    }

    static boolean isRecoveryFileName(String name) {
        return "recovery".equalsIgnoreCase(name)
                || "recovery.txt".equalsIgnoreCase(name)
                || "file recovery".equalsIgnoreCase(name)
                || "file recovery.txt".equalsIgnoreCase(name);
    }
}
