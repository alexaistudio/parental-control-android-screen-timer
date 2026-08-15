package dev.tvtimer.app;

public final class LimitMath {
    private static final long MAX_SINGLE_TICK_MILLIS = 60_000L;

    private LimitMath() {
    }

    public static long elapsedDelta(long previousElapsed, long currentElapsed) {
        if (previousElapsed < 0L || currentElapsed <= previousElapsed) {
            return 0L;
        }
        return Math.min(currentElapsed - previousElapsed, MAX_SINGLE_TICK_MILLIS);
    }

    public static long remaining(long dailyLimitMillis, long bonusMillis, long usedMillis) {
        long allowance;
        try {
            allowance = Math.addExact(Math.max(0L, dailyLimitMillis), Math.max(0L, bonusMillis));
        } catch (ArithmeticException exception) {
            allowance = Long.MAX_VALUE;
        }
        return Math.max(0L, allowance - Math.max(0L, usedMillis));
    }

    public static String formatCountdown(long remainingMillis) {
        long totalSeconds = Math.max(0L, remainingMillis + 999L) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder result = new StringBuilder(8);
        appendAtLeastTwoDigits(result, hours);
        result.append(':');
        appendAtLeastTwoDigits(result, minutes);
        result.append(':');
        appendAtLeastTwoDigits(result, seconds);
        return result.toString();
    }

    private static void appendAtLeastTwoDigits(StringBuilder destination, long value) {
        if (value < 10L) {
            destination.append('0');
        }
        destination.append(value);
    }
}
