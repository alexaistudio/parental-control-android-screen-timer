package dev.tvtimer.app;

import java.util.Arrays;

final class LauncherProfile {
    static final String DEFAULT = "timer";
    static final String CALCULATOR = "calculator";
    static final String MEDIA = "media";
    static final String CLOCK = "clock";
    static final String WEATHER = "weather";
    static final String NOTES = "notes";
    static final String CALENDAR = "calendar";
    static final String FILES = "files";
    static final String GALLERY = "gallery";
    static final String HELP = "help";
    static final String[] VALUES = {
            DEFAULT,
            CALCULATOR,
            MEDIA,
            CLOCK,
            WEATHER,
            NOTES,
            CALENDAR,
            FILES,
            GALLERY,
            HELP
    };

    private LauncherProfile() {
    }

    static boolean isSupported(String value) {
        return value != null && Arrays.asList(VALUES).contains(value);
    }

    static void requireSupported(String value) {
        if (!isSupported(value)) {
            throw new IllegalArgumentException("Unsupported launcher profile");
        }
    }

    static String aliasClassName(String value) {
        requireSupported(value);
        if (CALCULATOR.equals(value)) {
            return "dev.tvtimer.app.CalculatorLauncher";
        }
        if (MEDIA.equals(value)) {
            return "dev.tvtimer.app.MediaLauncher";
        }
        if (CLOCK.equals(value)) {
            return "dev.tvtimer.app.ClockLauncher";
        }
        if (WEATHER.equals(value)) {
            return "dev.tvtimer.app.WeatherLauncher";
        }
        if (NOTES.equals(value)) {
            return "dev.tvtimer.app.NotesLauncher";
        }
        if (CALENDAR.equals(value)) {
            return "dev.tvtimer.app.CalendarLauncher";
        }
        if (FILES.equals(value)) {
            return "dev.tvtimer.app.FilesLauncher";
        }
        if (GALLERY.equals(value)) {
            return "dev.tvtimer.app.GalleryLauncher";
        }
        if (HELP.equals(value)) {
            return "dev.tvtimer.app.HelpLauncher";
        }
        return "dev.tvtimer.app.TimerLauncher";
    }
}
