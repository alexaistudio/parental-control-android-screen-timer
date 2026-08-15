package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ForegroundEventPolicyTest {
    private static final String OWN = "dev.tvtimer.app";

    @Test
    public void ignoresOverlayWindowEventsButAcceptsOpeningTheSettingsActivity() {
        assertFalse(ForegroundEventPolicy.shouldReplaceActivePackage(OWN, OWN, false));
        assertTrue(ForegroundEventPolicy.shouldReplaceActivePackage(OWN, OWN, true));
    }

    @Test
    public void acceptsApplicationsAndIgnoresTransientSystemWindows() {
        assertTrue(ForegroundEventPolicy.shouldReplaceActivePackage(
                "com.google.android.youtube.tv",
                OWN,
                true
        ));
        assertFalse(ForegroundEventPolicy.shouldReplaceActivePackage(
                "com.android.systemui",
                OWN,
                true
        ));
        assertFalse(ForegroundEventPolicy.shouldReplaceActivePackage(
                "com.example.inputmethod",
                OWN,
                true
        ));
    }
}
