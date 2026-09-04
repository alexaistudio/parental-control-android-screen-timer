package dev.tvtimer.app;

final class ExtensionDurationPolicy {
    static final int ASK_EVERY_TIME = 0;
    static final int[] CHOICES_MINUTES = {10, 15, 20, 30, 40, 60, 90, 120};

    private ExtensionDurationPolicy() {
    }

    static boolean isSupported(int minutes) {
        if (minutes == ASK_EVERY_TIME) {
            return true;
        }
        for (int value : CHOICES_MINUTES) {
            if (value == minutes) {
                return true;
            }
        }
        return false;
    }

    static void requireSupported(int minutes) {
        if (!isSupported(minutes)) {
            throw new IllegalArgumentException("Unsupported extension duration");
        }
    }

    static long toMillis(int minutes) {
        requireSupported(minutes);
        if (minutes == ASK_EVERY_TIME) {
            throw new IllegalArgumentException("A concrete duration is required");
        }
        return minutes * 60_000L;
    }
}
