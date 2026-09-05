package dev.tvtimer.app;

import org.junit.Test;
import java.util.TimeZone;
import static org.junit.Assert.assertEquals;

public final class DayKeyTest {
    @Test
    public void rollsOverAtDeviceMidnightAndUsesChangedTimezone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"));
            long midnight = 1788555600000L; // 2026-09-05 00:00 MSK
            assertEquals("2026-09-04", DayKey.localDay(midnight - 1));
            assertEquals("2026-09-05", DayKey.localDay(midnight));
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertEquals("2026-09-04", DayKey.localDay(midnight));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
