package dev.tvtimer.controller;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.muntashirakon.adb.AdbStream;

final class AdbClient {
    static final String BLOCKER_PACKAGE = "dev.tvtimer.app";
    static final String ACCESSIBILITY_SERVICE =
            "dev.tvtimer.app/dev.tvtimer.app.LimiterAccessibilityService";
    private static final String DEVICE_ADMIN =
            "dev.tvtimer.app/dev.tvtimer.app.TimerDeviceAdminReceiver";
    private static final String ASSET_APK = "android-screen-timer.apk";
    private static final String END_MARKER = "__AST_COMMAND_DONE__";

    interface ProgressListener {
        void onProgress(int percent);
    }

    static final class InstallResult {
        final boolean accessibilityEnabled;
        final boolean deviceOwnerRequested;
        final boolean deviceOwnerEnabled;
        final boolean debuggingDisabled;
        final String deviceLabel;

        InstallResult(boolean accessibilityEnabled, boolean deviceOwnerRequested,
                      boolean deviceOwnerEnabled, boolean debuggingDisabled, String deviceLabel) {
            this.accessibilityEnabled = accessibilityEnabled;
            this.deviceOwnerRequested = deviceOwnerRequested;
            this.deviceOwnerEnabled = deviceOwnerEnabled;
            this.debuggingDisabled = debuggingDisabled;
            this.deviceLabel = deviceLabel;
        }
    }

    private final Context context;
    private final AdbConnectionManager manager;
    private String connectedHost;
    private int connectedPort = -1;

    AdbClient(Context context) {
        this.context = context.getApplicationContext();
        manager = AdbConnectionManager.get(context);
    }

    synchronized boolean isConnectedTo(String host, int port) {
        return manager.isConnected() && host.equals(connectedHost) && port == connectedPort;
    }

    synchronized void pair(String host, int port, String pairingCode) throws Exception {
        if (!pairingCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("Pairing code must contain six digits");
        }
        disconnect();
        ExecutorService pairingWorker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "adb-pairing");
            thread.setDaemon(true);
            return thread;
        });
        Future<Boolean> result = pairingWorker.submit(() -> manager.pair(host, port, pairingCode));
        try {
            if (!result.get(25, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The target device rejected the pairing code");
            }
        } catch (TimeoutException exception) {
            result.cancel(true);
            throw new IllegalStateException("Pairing timed out. Open a new code on the target device.", exception);
        } finally {
            pairingWorker.shutdownNow();
        }
    }

    synchronized String connect(String host, int port) throws Exception {
        disconnect();
        manager.setThrowOnUnauthorised(false);
        boolean connected = manager.connect(host, port);
        if (!connected && !manager.isConnected()) {
            throw new IllegalStateException("ADB did not accept the connection");
        }
        connectedHost = host;
        connectedPort = port;
        String manufacturer = cleanProperty(runShell("getprop ro.product.manufacturer"));
        String model = cleanProperty(runShell("getprop ro.product.model"));
        String android = cleanProperty(runShell("getprop ro.build.version.release"));
        String label = (manufacturer + " " + model).trim();
        if (label.isBlank()) {
            label = host;
        }
        return android.isBlank() ? label : label + " · Android " + android;
    }

    synchronized InstallResult installAndConfigure(boolean configureAccessibility,
                                                    boolean requestDeviceOwner,
                                                    boolean disableDebugging,
                                                    ProgressListener progress) throws Exception {
        if (!manager.isConnected()) {
            throw new IllegalStateException("Connect to the target device first");
        }
        File apk = materializeEmbeddedApk();
        try {
            installApk(apk, progress);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
        }

        boolean ownerEnabled = false;
        if (requestDeviceOwner) {
            String ownerOutput = runShell("dpm set-device-owner --user 0 " + DEVICE_ADMIN);
            ownerEnabled = ownerOutput.toLowerCase(java.util.Locale.ROOT).contains("success");
        }

        boolean accessibilityEnabled = !configureAccessibility;
        if (configureAccessibility) {
            try {
                runShell("cmd appops set " + BLOCKER_PACKAGE + " ACCESS_RESTRICTED_SETTINGS allow");
            } catch (Exception ignored) {
                // This app-op does not exist on older Android versions.
            }
            String current = runShell("settings get secure enabled_accessibility_services").trim();
            String updated = AccessibilityServices.add(current, ACCESSIBILITY_SERVICE);
            runShell("settings put secure enabled_accessibility_services " + updated);
            runShell("settings put secure accessibility_enabled 1");
            String verified = runShell("settings get secure enabled_accessibility_services").trim();
            accessibilityEnabled = AccessibilityServices.contains(verified, ACCESSIBILITY_SERVICE);
        }

        String packageCheck = runShell("pm list packages " + BLOCKER_PACKAGE);
        if (!packageCheck.contains("package:" + BLOCKER_PACKAGE)) {
            throw new IllegalStateException("Android did not report the installed package");
        }

        runShell("am start -n " + BLOCKER_PACKAGE + "/.MainActivity");

        String manufacturer = cleanProperty(runShell("getprop ro.product.manufacturer"));
        String model = cleanProperty(runShell("getprop ro.product.model"));
        String deviceLabel = (manufacturer + " " + model).trim();

        boolean debugDisabled = false;
        if (disableDebugging) {
            String wireless = runShell("settings get global adb_wifi_enabled").trim();
            if ("1".equals(wireless)) {
                try {
                    runShell("settings put global adb_wifi_enabled 0");
                    debugDisabled = !"1".equals(
                            runShell("settings get global adb_wifi_enabled").trim());
                } catch (Exception expectedDisconnect) {
                    // Losing the connection is the expected success signal here.
                    debugDisabled = true;
                }
            }
        }

        return new InstallResult(accessibilityEnabled, requestDeviceOwner, ownerEnabled,
                debugDisabled, deviceLabel);
    }

    synchronized void disconnect() {
        try {
            manager.disconnect();
        } catch (Exception ignored) {
            // A dead socket is already disconnected for our purposes.
        }
        connectedHost = null;
        connectedPort = -1;
    }

    private File materializeEmbeddedApk() throws Exception {
        File output = new File(context.getCacheDir(), ASSET_APK);
        try (InputStream input = context.getAssets().open(ASSET_APK);
             OutputStream file = new FileOutputStream(output, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                file.write(buffer, 0, read);
            }
        }
        if (output.length() <= 0) {
            throw new IllegalStateException("Embedded blocker APK is empty");
        }
        return output;
    }

    private void installApk(File apk, ProgressListener progress) throws Exception {
        long size = apk.length();
        try (AdbStream stream = manager.openStream("exec:cmd package install -r -S " + size);
             InputStream input = new java.io.FileInputStream(apk)) {
            OutputStream output = stream.openOutputStream();
            byte[] buffer = new byte[64 * 1024];
            long sent = 0;
            int lastPercent = -1;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                sent += read;
                int percent = (int) Math.min(100, (sent * 100L) / size);
                if (percent != lastPercent) {
                    lastPercent = percent;
                    progress.onProgress(percent);
                }
            }
            output.flush();
            String response = readResponse(stream, 180, TimeUnit.SECONDS, null);
            if (!response.toLowerCase(java.util.Locale.ROOT).contains("success")) {
                throw new IllegalStateException(response.isBlank()
                        ? "Package Manager returned no installation result"
                        : response.trim());
            }
        }
    }

    private String runShell(String command) throws Exception {
        try (AdbStream stream = manager.openStream(
                "shell:" + command + "; echo " + END_MARKER)) {
            String response = readResponse(stream, 20, TimeUnit.SECONDS, END_MARKER);
            return response.replace(END_MARKER, "").trim();
        }
    }

    private static String readResponse(AdbStream stream, long timeout, TimeUnit unit,
                                       String completionMarker) throws Exception {
        ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "adb-response");
            thread.setDaemon(true);
            return thread;
        });
        Future<String> future = reader.submit(() -> {
            StringBuilder output = new StringBuilder();
            InputStream input = stream.openInputStream();
            byte[] buffer = new byte[4096];
            while (true) {
                int count = input.read(buffer);
                if (count <= 0) {
                    break;
                }
                output.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
                if (completionMarker != null && output.indexOf(completionMarker) >= 0) {
                    break;
                }
            }
            return output.toString();
        });
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException exception) {
            try {
                stream.close();
            } catch (Exception ignored) {
            }
            future.cancel(true);
            throw new IllegalStateException("ADB command timed out", exception);
        } finally {
            reader.shutdownNow();
        }
    }

    private static String cleanProperty(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
