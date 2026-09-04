package dev.tvtimer.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InstalledPackageProbe {
    private static final Pattern VERSION_CODE = Pattern.compile("(?:^|\\s)versionCode=(\\d+)");
    private static final Pattern VERSION_NAME = Pattern.compile("(?:^|\\s)versionName=([^\\s]+)");

    private InstalledPackageProbe() {
    }

    static boolean matches(String packageDump, String expectedVersionName,
                           long expectedVersionCode) {
        if (packageDump == null || expectedVersionName == null) {
            return false;
        }
        Matcher code = VERSION_CODE.matcher(packageDump);
        Matcher name = VERSION_NAME.matcher(packageDump);
        return code.find()
                && name.find()
                && Long.toString(expectedVersionCode).equals(code.group(1))
                && expectedVersionName.equals(name.group(1));
    }
}
