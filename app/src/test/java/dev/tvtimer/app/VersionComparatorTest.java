package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VersionComparatorTest {
    @Test
    public void comparesThreePartReleaseVersions() {
        assertTrue(VersionComparator.isNewer("v1.0.1", "1.0.0"));
        assertTrue(VersionComparator.isNewer("2.0.0", "1.99.99"));
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"));
        assertFalse(VersionComparator.isNewer("0.9.9", "1.0.0"));
        assertFalse(VersionComparator.isNewer("latest", "1.0.0"));
        assertEquals("1.2.0", VersionComparator.normalized("v1.2"));
    }
}
