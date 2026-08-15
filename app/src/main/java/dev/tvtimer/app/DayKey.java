package dev.tvtimer.app;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DayKey {
    private static final ThreadLocal<SimpleDateFormat> FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        }
    };

    private DayKey() {
    }

    public static String localDay(long wallClockMillis) {
        return FORMAT.get().format(new Date(wallClockMillis));
    }
}
