package dev.tvtimer.app;

final class UpdateProgress {
    private static final int WIDTH = 20;

    private UpdateProgress() {
    }

    static String render(long downloadedBytes, long totalBytes) {
        if (totalBytes <= 0L) {
            return "[--------------------] --%";
        }
        int percent = percent(downloadedBytes, totalBytes);
        int filled = percent * WIDTH / 100;
        StringBuilder result = new StringBuilder(WIDTH + 7);
        result.append('[');
        for (int index = 0; index < WIDTH; index++) {
            result.append(index < filled ? '#' : '-');
        }
        result.append("] ").append(percent).append('%');
        return result.toString();
    }

    static int percent(long downloadedBytes, long totalBytes) {
        if (totalBytes <= 0L) {
            return -1;
        }
        long safeDownloaded = Math.max(0L, Math.min(downloadedBytes, totalBytes));
        return (int) Math.min(100d, Math.floor((double) safeDownloaded * 100d / totalBytes));
    }
}
