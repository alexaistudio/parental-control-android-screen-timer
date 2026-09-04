package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class EmergencyCodeTest {
    @Test
    public void generatedCodeAlwaysHasFourDigits() {
        for (int index = 0; index < 100; index++) {
            assertTrue(EmergencyCode.isValid(EmergencyCode.generate()));
        }
    }

    @Test
    public void rejectsOtherFormats() {
        assertFalse(EmergencyCode.isValid(null));
        assertFalse(EmergencyCode.isValid("123"));
        assertFalse(EmergencyCode.isValid("12345"));
        assertFalse(EmergencyCode.isValid("12a4"));
    }
}
