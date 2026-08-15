package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LimitMathTest {
    @Test
    public void countsOnlyForwardElapsedTimeAndCapsAStaleTick() {
        assertEquals(0L, LimitMath.elapsedDelta(-1L, 100L));
        assertEquals(0L, LimitMath.elapsedDelta(200L, 100L));
        assertEquals(750L, LimitMath.elapsedDelta(1_000L, 1_750L));
        assertEquals(60_000L, LimitMath.elapsedDelta(1_000L, 120_000L));
    }

    @Test
    public void remainingIncludesBonusAndNeverBecomesNegative() {
        assertEquals(1_500L, LimitMath.remaining(1_000L, 1_000L, 500L));
        assertEquals(0L, LimitMath.remaining(1_000L, 0L, 2_000L));
        assertEquals(Long.MAX_VALUE, LimitMath.remaining(Long.MAX_VALUE, 1L, 0L));
    }

    @Test
    public void countdownRoundsPartialSecondsUp() {
        assertEquals("00:00:00", LimitMath.formatCountdown(0L));
        assertEquals("00:00:01", LimitMath.formatCountdown(1L));
        assertEquals("01:01:01", LimitMath.formatCountdown(3_661_000L));
    }
}
