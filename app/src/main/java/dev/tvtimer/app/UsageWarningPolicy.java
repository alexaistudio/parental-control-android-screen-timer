package dev.tvtimer.app;

final class UsageWarningPolicy {
    static final int DISABLED = 0;
    static final int[] CHOICES_MINUTES = {10, 20, 30};

    private UsageWarningPolicy() {
    }

    static boolean isSupported(int minutes) {
        if (minutes == DISABLED) {
            return true;
        }
        for (int choice : CHOICES_MINUTES) {
            if (choice == minutes) {
                return true;
            }
        }
        return false;
    }

    static void requireSupported(int minutes) {
        if (!isSupported(minutes)) {
            throw new IllegalArgumentException("Unsupported usage warning interval");
        }
    }

    static long dueThresholdMinutes(
            int intervalMinutes,
            long lastAcknowledgedMinutes,
            long usedMillis
    ) {
        requireSupported(intervalMinutes);
        if (intervalMinutes == DISABLED || usedMillis < 0L) {
            return 0L;
        }
        long usedMinutes = usedMillis / 60_000L;
        long threshold = usedMinutes / intervalMinutes * intervalMinutes;
        return threshold > lastAcknowledgedMinutes ? threshold : 0L;
    }
}
