package dev.tvtimer.controller;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InstalledPackageProbeTest {
    @Test
    public void matchesExpectedInstalledVersion() {
        String dump = "versionCode=16 minSdk=23 targetSdk=35\nversionName=1.4.5\n";

        assertTrue(InstalledPackageProbe.matches(dump, "1.4.5", 16L));
        assertFalse(InstalledPackageProbe.matches(dump, "1.4.4", 15L));
        assertFalse(InstalledPackageProbe.matches("package missing", "1.4.5", 16L));
    }
}
