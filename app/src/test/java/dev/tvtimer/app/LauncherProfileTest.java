package dev.tvtimer.app;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LauncherProfileTest {
    @Test
    public void exposesOnlyBundledLauncherProfiles() {
        assertEquals(10, LauncherProfile.VALUES.length);
        Set<String> uniqueProfiles = new HashSet<>();
        Set<String> uniqueAliases = new HashSet<>();
        for (String profile : LauncherProfile.VALUES) {
            assertTrue(LauncherProfile.isSupported(profile));
            assertTrue(uniqueProfiles.add(profile));
            assertTrue(uniqueAliases.add(LauncherProfile.aliasClassName(profile)));
        }
        assertFalse(LauncherProfile.isSupported("custom"));
    }

    @Test
    public void mapsProfileToDeclaredAlias() {
        assertEquals("dev.tvtimer.app.TimerLauncher", LauncherProfile.aliasClassName(LauncherProfile.DEFAULT));
        assertEquals("dev.tvtimer.app.CalculatorLauncher", LauncherProfile.aliasClassName(LauncherProfile.CALCULATOR));
        assertEquals("dev.tvtimer.app.MediaLauncher", LauncherProfile.aliasClassName(LauncherProfile.MEDIA));
        assertEquals("dev.tvtimer.app.ClockLauncher", LauncherProfile.aliasClassName(LauncherProfile.CLOCK));
        assertEquals("dev.tvtimer.app.WeatherLauncher", LauncherProfile.aliasClassName(LauncherProfile.WEATHER));
        assertEquals("dev.tvtimer.app.NotesLauncher", LauncherProfile.aliasClassName(LauncherProfile.NOTES));
        assertEquals("dev.tvtimer.app.CalendarLauncher", LauncherProfile.aliasClassName(LauncherProfile.CALENDAR));
        assertEquals("dev.tvtimer.app.FilesLauncher", LauncherProfile.aliasClassName(LauncherProfile.FILES));
        assertEquals("dev.tvtimer.app.GalleryLauncher", LauncherProfile.aliasClassName(LauncherProfile.GALLERY));
        assertEquals("dev.tvtimer.app.HelpLauncher", LauncherProfile.aliasClassName(LauncherProfile.HELP));
    }
}
