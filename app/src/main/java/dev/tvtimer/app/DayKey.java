package dev.tvtimer.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DayKey {
    private DayKey() {
    }

    public static String localDay(long wallClockMillis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date(wallClockMillis));
    }
}
