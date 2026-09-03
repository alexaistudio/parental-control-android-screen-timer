package dev.tvtimer.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccessibilityServicesTest {
    private static final String TIMER = "dev.tvtimer.app/dev.tvtimer.app.LimiterAccessibilityService";

    @Test
    public void addsServiceWithoutRemovingExistingOnes() {
        String current = "com.reader/.TalkBackService:com.switch/.SwitchService";
        assertEquals(current + ":" + TIMER, AccessibilityServices.add(current, TIMER));
    }

    @Test
    public void doesNotDuplicateService() {
        assertEquals(TIMER, AccessibilityServices.add(TIMER, TIMER));
        assertTrue(AccessibilityServices.contains(TIMER, TIMER));
    }

    @Test
    public void handlesEmptySystemValues() {
        assertEquals(TIMER, AccessibilityServices.add("null", TIMER));
        assertEquals(TIMER, AccessibilityServices.add("", TIMER));
        assertFalse(AccessibilityServices.contains("null", TIMER));
    }
}
