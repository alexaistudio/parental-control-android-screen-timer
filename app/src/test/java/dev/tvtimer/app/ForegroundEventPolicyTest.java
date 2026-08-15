package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ForegroundEventPolicyTest {
    private static final String OWN = "dev.tvtimer.app";
    private static final String OWN_ACTIVITY = "dev.tvtimer.app.MainActivity";

    @Test
    public void ignoresOverlayWindowEventsButAcceptsOpeningTheSettingsActivity() {
        assertFalse(ForegroundEventPolicy.shouldReplaceActivePackage(
                OWN,
                "android.widget.TextView",
                OWN,
                OWN_ACTIVITY,
                true
        ));
        assertTrue(ForegroundEventPolicy.shouldReplaceActivePackage(
                OWN,
                OWN_ACTIVITY,
                OWN,
                OWN_ACTIVITY,
                true
        ));
        assertFalse(ForegroundEventPolicy.shouldReplaceActivePackage(
                "com.google.android.youtube.tv",
                "com.google.android.youtube.tv.MainActivity",
                OWN,
                OWN_ACTIVITY,
                false
        ));
    }

    @Test
    public void acceptsApplicationsAndIgnoresTransientSystemWindows() {
        assertTrue(ForegroundEventPolicy.shouldReplaceActivePackage(
                "com.google.android.youtube.tv",
                "com.google.android.youtube.tv.MainActivity",
                OWN,
                OWN_ACTIVITY,
                true
        ));
        assertFalse(ForegroundEventPolicy.shouldReplaceActivePackage(
                "com.android.systemui",
                "com.android.systemui.SomeWindow",
                OWN,
                OWN_ACTIVITY,
                true
        ));
        assertFalse(ForegroundEventPolicy.shouldReplaceActivePackage(
                "com.example.inputmethod",
                "com.example.inputmethod.Keyboard",
                OWN,
                OWN_ACTIVITY,
                true
        ));
    }
}
