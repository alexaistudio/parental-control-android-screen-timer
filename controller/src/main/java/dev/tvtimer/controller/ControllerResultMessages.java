package dev.tvtimer.controller;

final class ControllerResultMessages {
    private ControllerResultMessages() {
    }

    static String paired(String host, int port) {
        return "SUCCESS: PAIRED target=" + endpoint(host, port);
    }

    static String connected(String host, int port, String deviceLabel) {
        return "SUCCESS: CONNECTED target=" + endpoint(host, port)
                + " device=" + clean(deviceLabel);
    }

    static String installed(String host, int port, String packageName,
                            String packageManagerResponse) {
        return "SUCCESS: APP INSTALLED target=" + endpoint(host, port)
                + " package=" + clean(packageName)
                + " packageManagerResponse=" + clean(packageManagerResponse);
    }

    static String setupComplete(String host, int port, boolean accessibilityEnabled,
                                boolean deviceOwnerEnabled, boolean debuggingDisabled) {
        return "SUCCESS: SETUP COMPLETE target=" + endpoint(host, port)
                + " accessibility=" + accessibilityEnabled
                + " deviceOwner=" + deviceOwnerEnabled
                + " wirelessDebugDisabled=" + debuggingDisabled;
    }

    static String failed(String operation, String host, int port, Throwable throwable) {
        String detail = throwable == null ? "unknown"
                : throwable.getClass().getSimpleName() + ": " + clean(throwable.getMessage());
        return "FAILURE: " + clean(operation) + " target=" + endpoint(host, port)
                + " error=" + detail;
    }

    private static String endpoint(String host, int port) {
        return clean(host) + ":" + port;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
