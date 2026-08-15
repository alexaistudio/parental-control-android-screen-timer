package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class UpdateProgressTest {
    @Test
    public void rendersAsciiProgressFromZeroToOneHundredPercent() {
        assertEquals("[--------------------] 0%", UpdateProgress.render(0L, 100L));
        assertEquals("[##########----------] 50%", UpdateProgress.render(50L, 100L));
        assertEquals("[####################] 100%", UpdateProgress.render(100L, 100L));
        assertEquals("[####################] 100%", UpdateProgress.render(150L, 100L));
        assertEquals("[--------------------] --%", UpdateProgress.render(10L, 0L));
    }

    @Test
    public void exposesPercentForUiChangeDetection() {
        assertEquals(-1, UpdateProgress.percent(10L, -1L));
        assertEquals(25, UpdateProgress.percent(25L, 100L));
        assertEquals(100, UpdateProgress.percent(Long.MAX_VALUE, Long.MAX_VALUE));
    }
}
