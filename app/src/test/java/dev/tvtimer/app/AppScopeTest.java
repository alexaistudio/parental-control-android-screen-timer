package dev.tvtimer.app;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AppScopeTest {
    private static final String OWN = "dev.tvtimer.app";

    @Test
    public void allScopeCountsNormalPackagesButNotOwnOrTransientSystemWindows() {
        assertTrue(AppScope.isTarget(AppScope.ALL, "com.google.android.youtube.tv", OWN, null));
        assertFalse(AppScope.isTarget(AppScope.ALL, OWN, OWN, null));
        assertFalse(AppScope.isTarget(AppScope.ALL, "com.android.systemui", OWN, null));
        assertFalse(AppScope.isTarget(AppScope.ALL, "com.google.android.tvlauncher", OWN, null));
        assertFalse(AppScope.isTarget(AppScope.ALL, "com.example.keyboard", OWN, null));
    }

    @Test
    public void selectedScopeCountsOnlySelectedPackages() {
        HashSet<String> selected = new HashSet<>();
        selected.add("com.google.android.youtube.tv");

        assertTrue(AppScope.isTarget(
                AppScope.SELECTED,
                "com.google.android.youtube.tv",
                OWN,
                selected
        ));
        assertFalse(AppScope.isTarget(AppScope.SELECTED, "com.netflix.ninja", OWN, selected));
        assertFalse(AppScope.isTarget(
                AppScope.SELECTED,
                "com.google.android.youtube.tv",
                OWN,
                Collections.emptySet()
        ));
    }
}
