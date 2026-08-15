package dev.tvtimer.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VersionComparator {
    private static final Pattern VERSION = Pattern.compile(
            "^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[-+].*)?$"
    );

    private VersionComparator() {
    }

    static boolean isNewer(String candidate, String installed) {
        int[] candidateParts = parse(candidate);
        int[] installedParts = parse(installed);
        if (candidateParts == null || installedParts == null) {
            return false;
        }
        for (int index = 0; index < candidateParts.length; index++) {
            if (candidateParts[index] != installedParts[index]) {
                return candidateParts[index] > installedParts[index];
            }
        }
        return false;
    }

    static String normalized(String version) {
        int[] parts = parse(version);
        if (parts == null) {
            return "";
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    private static int[] parse(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = VERSION.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(matcher.group(1)),
                    parseOptional(matcher.group(2)),
                    parseOptional(matcher.group(3))
            };
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static int parseOptional(String value) {
        return value == null ? 0 : Integer.parseInt(value);
    }
}
