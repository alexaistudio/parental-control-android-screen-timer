package dev.tvtimer.controller;

import java.util.regex.Pattern;

final class SecretRedactor {
    private static final Pattern PAIRING_CODE = Pattern.compile(
            "(?i)((?:pairing|pair)[ _-]?code\\s*[=:]\\s*)\\d{6}");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?s)-----BEGIN (?:RSA )?PRIVATE KEY-----.*?-----END (?:RSA )?PRIVATE KEY-----");

    private SecretRedactor() {
    }

    static String redact(String value) {
        if (value == null) {
            return "<null>";
        }
        String result = PAIRING_CODE.matcher(value).replaceAll("$1<redacted>");
        return PRIVATE_KEY.matcher(result).replaceAll("<private-key-redacted>");
    }
}
