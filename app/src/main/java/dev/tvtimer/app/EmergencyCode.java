package dev.tvtimer.app;

import java.security.SecureRandom;
import java.util.Locale;

final class EmergencyCode {
    private static final SecureRandom RANDOM = new SecureRandom();

    private EmergencyCode() {
    }

    static String generate() {
        return String.format(Locale.US, "%04d", RANDOM.nextInt(10_000));
    }

    static boolean isValid(String value) {
        return value != null && value.matches("[0-9]{4}");
    }
}
