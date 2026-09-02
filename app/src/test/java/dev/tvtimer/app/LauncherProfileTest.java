package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LauncherProfileTest {
    @Test
    public void exposesOnlyBundledLauncherProfiles() {
        assertTrue(LauncherProfile.isSupported(LauncherProfile.DEFAULT));
        assertTrue(LauncherProfile.isSupported(LauncherProfile.CALCULATOR));
        assertTrue(LauncherProfile.isSupported(LauncherProfile.MEDIA));
        assertFalse(LauncherProfile.isSupported("custom"));
    }

    @Test
    public void mapsProfileToDeclaredAlias() {
        assertEquals(
                "dev.tvtimer.app.CalculatorLauncher",
                LauncherProfile.aliasClassName(LauncherProfile.CALCULATOR)
        );
    }
}
