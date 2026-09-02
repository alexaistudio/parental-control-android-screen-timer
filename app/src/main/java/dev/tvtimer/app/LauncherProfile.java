package dev.tvtimer.app;

import java.util.Arrays;

final class LauncherProfile {
    static final String DEFAULT = "timer";
    static final String CALCULATOR = "calculator";
    static final String MEDIA = "media";
    static final String[] VALUES = {DEFAULT, CALCULATOR, MEDIA};

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
        return "dev.tvtimer.app.TimerLauncher";
    }
}
