package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ExtensionDurationPolicyTest {
    @Test
    public void supportsRequestedChoicesAndAskMode() {
        assertTrue(ExtensionDurationPolicy.isSupported(0));
        assertTrue(ExtensionDurationPolicy.isSupported(10));
        assertTrue(ExtensionDurationPolicy.isSupported(15));
        assertTrue(ExtensionDurationPolicy.isSupported(20));
        assertTrue(ExtensionDurationPolicy.isSupported(30));
        assertTrue(ExtensionDurationPolicy.isSupported(40));
        assertTrue(ExtensionDurationPolicy.isSupported(60));
        assertFalse(ExtensionDurationPolicy.isSupported(5));
        assertFalse(ExtensionDurationPolicy.isSupported(120));
    }

    @Test
    public void convertsConcreteChoiceToMilliseconds() {
        assertEquals(2_400_000L, ExtensionDurationPolicy.toMillis(40));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesToConvertAskMode() {
        ExtensionDurationPolicy.toMillis(ExtensionDurationPolicy.ASK_EVERY_TIME);
    }
}
