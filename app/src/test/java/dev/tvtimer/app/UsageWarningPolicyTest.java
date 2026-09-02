package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class UsageWarningPolicyTest {
    @Test
    public void supportsDisabledAndRequestedIntervals() {
        assertTrue(UsageWarningPolicy.isSupported(0));
        assertTrue(UsageWarningPolicy.isSupported(10));
        assertTrue(UsageWarningPolicy.isSupported(20));
        assertTrue(UsageWarningPolicy.isSupported(30));
        assertFalse(UsageWarningPolicy.isSupported(15));
    }

    @Test
    public void warnsOnlyWhenNextThresholdIsReached() {
        assertEquals(0L, UsageWarningPolicy.dueThresholdMinutes(10, 0L, 599_999L));
        assertEquals(10L, UsageWarningPolicy.dueThresholdMinutes(10, 0L, 600_000L));
        assertEquals(0L, UsageWarningPolicy.dueThresholdMinutes(10, 10L, 1_199_999L));
        assertEquals(20L, UsageWarningPolicy.dueThresholdMinutes(10, 10L, 1_200_000L));
    }

    @Test
    public void catchesUpToLatestCompletedThresholdWithoutRepeating() {
        assertEquals(30L, UsageWarningPolicy.dueThresholdMinutes(10, 0L, 35L * 60_000L));
        assertEquals(0L, UsageWarningPolicy.dueThresholdMinutes(10, 30L, 35L * 60_000L));
        assertEquals(40L, UsageWarningPolicy.dueThresholdMinutes(20, 30L, 40L * 60_000L));
    }

    @Test
    public void disabledModeNeverWarns() {
        assertEquals(0L, UsageWarningPolicy.dueThresholdMinutes(0, 0L, Long.MAX_VALUE));
    }
}
