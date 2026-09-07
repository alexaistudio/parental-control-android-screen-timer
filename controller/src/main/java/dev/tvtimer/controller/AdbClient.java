package dev.tvtimer.controller;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.muntashirakon.adb.AdbStream;

final class AdbClient {
    static final String BLOCKER_PACKAGE = "dev.tvtimer.app";
    static final String ACCESSIBILITY_SERVICE =
            "dev.tvtimer.app/dev.tvtimer.app.LimiterAccessibilityService";
    private static final String DEVICE_ADMIN =
            "dev.tvtimer.app/dev.tvtimer.app.TimerDeviceAdminReceiver";
    private static final String ASSET_APK = "android-screen-timer.apk";
    private static final String END_MARKER = "__AST_COMMAND_DONE__";
    private static final String REMOTE_URI = "content://dev.tvtimer.app.parentcontrol";

    interface ProgressListener {
        void onProgress(int percent);

        void onWaitingForPackageManager(int elapsedSeconds);

        void onConfiguring();
    }

    private static final class ApkIdentity {
        final String versionName;
        final long versionCode;

        ApkIdentity(String versionName, long versionCode) {
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
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
        ControllerLog.info("ADB/Client", "Client initialized");
    }

    synchronized boolean isConnectedTo(String host, int port) {
        return manager.isConnected() && host.equals(connectedHost) && port == connectedPort;
    }

    synchronized void pair(String host, int port, String pairingCode) throws Exception {
        if (!pairingCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("Pairing code must contain six digits");
        }
        long requestId = ControllerLog.request("ADB/Pair",
                "host=" + host + " port=" + port + " pairingCode=<redacted>");
        try {
            disconnect();
            ExecutorService pairingWorker = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "adb-pairing");
                thread.setDaemon(true);
                return thread;
            });
            Future<Boolean> result = pairingWorker.submit(
                    () -> manager.pair(host, port, pairingCode));
            try {
                if (!result.get(25, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("The target device rejected the pairing code");
                }
            } catch (TimeoutException exception) {
                result.cancel(true);
                throw new IllegalStateException(
                        "Pairing timed out. Open a new code on the target device.", exception);
            } finally {
                pairingWorker.shutdownNow();
            }
            ControllerLog.response("ADB/Pair", requestId, "paired=true");
            ControllerLog.result("ADB/Pair", ControllerResultMessages.paired(host, port));
        } catch (Exception exception) {
            ControllerLog.failure("ADB/Pair", requestId, "Pairing failed", exception);
            ControllerLog.result("ADB/Pair",
                    ControllerResultMessages.failed("PAIR", host, port, exception));
            throw exception;
        }
    }

    synchronized String connect(String host, int port) throws Exception {
        long requestId = ControllerLog.request("ADB/Connect", "host=" + host + " port=" + port);
        try {
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
            String result = android.isBlank() ? label : label + " · Android " + android;
            ControllerLog.response("ADB/Connect", requestId,
                    "connected=true device=" + result);
            ControllerLog.result("ADB/Connect",
                    ControllerResultMessages.connected(host, port, result));
            return result;
        } catch (Exception exception) {
            ControllerLog.failure("ADB/Connect", requestId, "Connection failed", exception);
            ControllerLog.result("ADB/Connect",
                    ControllerResultMessages.failed("CONNECT", host, port, exception));
            throw exception;
        }
    }

    synchronized InstallResult installAndConfigure(boolean configureAccessibility,
                                                    boolean requestDeviceOwner,
                                                    boolean disableDebugging,
                                                    ProgressListener progress) throws Exception {
        if (!manager.isConnected()) {
            throw new IllegalStateException("Connect to the target device first");
        }
        long requestId = ControllerLog.request("ADB/Install",
                "accessibility=" + configureAccessibility
                        + " deviceOwner=" + requestDeviceOwner
                        + " disableWirelessDebug=" + disableDebugging);
        String targetHost = connectedHost;
        int targetPort = connectedPort;
        try {
            boolean wasInstalled = runShell("pm path " + BLOCKER_PACKAGE)
                    .contains("package:");
            File apk = materializeEmbeddedApk();
            String packageManagerResponse;
            try {
                ApkIdentity identity = readApkIdentity(apk);
                packageManagerResponse = installApk(apk, identity, progress);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
            }
            ControllerLog.result("ADB/PackageManager", ControllerResultMessages.installed(
                    targetHost, targetPort, BLOCKER_PACKAGE, packageManagerResponse));
            progress.onConfiguring();

            boolean ownerEnabled = false;
            if (requestDeviceOwner) {
                String ownerOutput = runShell("dpm set-device-owner --user 0 " + DEVICE_ADMIN);
                ownerEnabled = ownerOutput.toLowerCase(java.util.Locale.ROOT).contains("success");
            }

            boolean accessibilityEnabled = !configureAccessibility;
            if (configureAccessibility) {
                try {
                    runShell("cmd appops set " + BLOCKER_PACKAGE
                            + " ACCESS_RESTRICTED_SETTINGS allow");
                    String restrictedState = runShell("cmd appops get " + BLOCKER_PACKAGE
                            + " ACCESS_RESTRICTED_SETTINGS");
                    ControllerLog.result("ADB/RestrictedSettings",
                            (restrictedState.toLowerCase(java.util.Locale.ROOT).contains("allow")
                                    ? "SUCCESS: RESTRICTED SETTINGS ALLOWED target="
                                    : "WARNING: RESTRICTED SETTINGS NOT ALLOWED target=")
                                    + targetHost + ":" + targetPort
                                    + " response=" + restrictedState);
                } catch (Exception unsupportedAppOp) {
                    ControllerLog.warning("ADB/Configure",
                            "ACCESS_RESTRICTED_SETTINGS app-op unavailable", unsupportedAppOp);
                    ControllerLog.result("ADB/RestrictedSettings",
                            "WARNING: RESTRICTED SETTINGS APP-OP UNAVAILABLE target="
                                    + targetHost + ":" + targetPort
                                    + " error=" + unsupportedAppOp.getClass().getSimpleName()
                                    + ": " + unsupportedAppOp.getMessage());
                    // This app-op does not exist on older Android versions.
                }
                String current = runShell(
                        "settings get secure enabled_accessibility_services").trim();
                String updated = AccessibilityServices.add(current, ACCESSIBILITY_SERVICE);
                runShell("settings put secure enabled_accessibility_services " + updated);
                runShell("settings put secure accessibility_enabled 1");
                String verified = runShell(
                        "settings get secure enabled_accessibility_services").trim();
                accessibilityEnabled = AccessibilityServices.contains(
                        verified, ACCESSIBILITY_SERVICE);
            }

            String packageCheck = runShell("pm list packages " + BLOCKER_PACKAGE);
            if (!packageCheck.contains("package:" + BLOCKER_PACKAGE)) {
                throw new IllegalStateException("Android did not report the installed package");
            }

            // Opening settings over a child’s video is appropriate only after the first install.
            // An in-place update preserves the current foreground app (for example, YouTube).
            if (!wasInstalled) {
                runShell("am start -n " + BLOCKER_PACKAGE + "/.MainActivity");
            }

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
                        ControllerLog.info("ADB/Configure",
                                "Connection closed while disabling wireless debugging; "
                                        + "treating as success");
                        // Losing the connection is the expected success signal here.
                        debugDisabled = true;
                    }
                }
            }

            InstallResult result = new InstallResult(accessibilityEnabled, requestDeviceOwner,
                    ownerEnabled, debugDisabled, deviceLabel);
            ControllerLog.response("ADB/Install", requestId,
                    "complete=true accessibility=" + accessibilityEnabled
                            + " deviceOwner=" + ownerEnabled
                            + " wirelessDebugDisabled=" + debugDisabled
                            + " device=" + deviceLabel);
            ControllerLog.result("ADB/Install", ControllerResultMessages.setupComplete(
                    targetHost, targetPort, accessibilityEnabled, ownerEnabled, debugDisabled));
            return result;
        } catch (Exception exception) {
            ControllerLog.failure("ADB/Install", requestId, "Install or setup failed", exception);
            ControllerLog.result("ADB/Install", ControllerResultMessages.failed(
                    "INSTALL", targetHost, targetPort, exception));
            throw exception;
        }
    }

    synchronized void disconnect() {
        long requestId = ControllerLog.request("ADB/Disconnect",
                "connectedHost=" + connectedHost + " connectedPort=" + connectedPort);
        try {
            manager.disconnect();
            ControllerLog.response("ADB/Disconnect", requestId, "disconnected=true");
        } catch (Exception exception) {
            ControllerLog.failure("ADB/Disconnect", requestId,
                    "Socket already dead or disconnect failed", exception);
            // A dead socket is already disconnected for our purposes.
        }
        connectedHost = null;
        connectedPort = -1;
    }

    synchronized TimerState readTimerState() throws Exception {
        ensureControlConnection();
        return TimerState.parse(runShell("content call --uri " + REMOTE_URI + " --method state"));
    }

    synchronized TimerState adjustTimer(int minutes, String comment) throws Exception {
        if (minutes == 0 || Math.abs((long) minutes) > 1_440L) {
            throw new IllegalArgumentException("Enter from 1 to 1440 minutes");
        }
        ensureControlConnection();
        return TimerState.parse(runShell("content call --uri " + REMOTE_URI
                + " --method adjust --extra " + shellQuote("minutes:i:" + minutes)
                + " --extra " + shellQuote("comment:s:" + (comment == null ? "" : comment))));
    }

    synchronized TimerState setGlobalLimit(int minutes) throws Exception {
        return callWithMinutes("setGlobalLimit", minutes, null);
    }

    synchronized TimerState setAppLimit(String packageName, int minutes) throws Exception {
        return callWithMinutes("setAppLimit", minutes, packageName);
    }

    synchronized TimerState removeAppLimit(String packageName) throws Exception {
        ensureControlConnection();
        return TimerState.parse(runShell("content call --uri " + REMOTE_URI
                + " --method removeAppLimit --extra " + shellQuote("package:s:" + packageName)));
    }

    private TimerState callWithMinutes(String method, int minutes, String packageName) throws Exception {
        if (minutes < 1 || minutes > 1440) throw new IllegalArgumentException("Enter from 1 to 1440 minutes");
        ensureControlConnection();
        String command = "content call --uri " + REMOTE_URI + " --method " + method
                + " --extra " + shellQuote("minutes:i:" + minutes);
        if (packageName != null) command += " --extra " + shellQuote("package:s:" + packageName);
        return TimerState.parse(runShell(command));
    }

    private void ensureControlConnection() throws Exception {
        if (manager.isConnected()) return;
        if (connectedHost == null || connectedPort < 1 || !manager.connect(connectedHost, connectedPort))
            throw new IllegalStateException("TV is no longer available over Wi-Fi ADB");
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static final class TimerState {
        private static final Pattern LONG_VALUE = Pattern.compile(
                "(dailyLimitMillis|usedMillis|bonusMillis|remainingMillis)=(-?\\d+)"
        );
        private static final Pattern ENABLED_VALUE = Pattern.compile("enforcementEnabled=(true|false)");
        private static final Pattern APPS_VALUE = Pattern.compile("appsPayload=([^,} ]*)");
        private final long dailyLimitMillis;
        private final long usedMillis;
        private final long bonusMillis;
        private final long remainingMillis;
        private final boolean enforcementEnabled;
        private final List<AppTimerState> apps;

        private TimerState(long dailyLimitMillis, long usedMillis, long bonusMillis,
                           long remainingMillis, boolean enforcementEnabled, List<AppTimerState> apps) {
            this.dailyLimitMillis = dailyLimitMillis;
            this.usedMillis = usedMillis;
            this.bonusMillis = bonusMillis;
            this.remainingMillis = remainingMillis;
            this.enforcementEnabled = enforcementEnabled;
            this.apps = apps;
        }

        static TimerState parse(String response) {
            if (response == null || !response.contains("ok=true")) {
                throw new IllegalStateException("TV rejected the request: " + cleanProperty(response));
            }
            long daily = -1L;
            long used = -1L;
            long bonus = 0L;
            long remaining = -1L;
            Matcher matcher = LONG_VALUE.matcher(response);
            while (matcher.find()) {
                long value = Long.parseLong(matcher.group(2));
                switch (matcher.group(1)) {
                    case "dailyLimitMillis": daily = value; break;
                    case "usedMillis": used = value; break;
                    case "bonusMillis": bonus = value; break;
                    case "remainingMillis": remaining = value; break;
                    default: break;
                }
            }
            Matcher enabled = ENABLED_VALUE.matcher(response);
            if (daily < 0L || used < 0L || remaining < 0L || !enabled.find()) {
                throw new IllegalStateException("TV returned an incomplete timer state");
            }
            List<AppTimerState> apps = new ArrayList<>();
            Matcher appsMatcher = APPS_VALUE.matcher(response);
            if (appsMatcher.find() && !appsMatcher.group(1).isEmpty()) {
                String decoded = new String(Base64.decode(appsMatcher.group(1), Base64.URL_SAFE), StandardCharsets.UTF_8);
                for (String line : decoded.split("\\n")) {
                    String[] values = line.split("\\t", -1);
                    if (values.length == 4) apps.add(new AppTimerState(values[0], values[1],
                            Long.parseLong(values[2]), Long.parseLong(values[3])));
                }
            }
            return new TimerState(daily, used, bonus, remaining,
                    Boolean.parseBoolean(enabled.group(1)), Collections.unmodifiableList(apps));
        }

        long getDailyLimitMillis() { return dailyLimitMillis; }
        long getRemainingMillis() { return remainingMillis; }
        long getUsedMillis() { return usedMillis; }
        long getBonusMillis() { return bonusMillis; }
        boolean isEnforcementEnabled() { return enforcementEnabled; }
        List<AppTimerState> getApps() { return apps; }
    }

    static final class AppTimerState {
        final String packageName, label;
        final long limitMillis, usedMillis;
        AppTimerState(String packageName, String label, long limitMillis, long usedMillis) {
            this.packageName = packageName; this.label = label;
            this.limitMillis = limitMillis; this.usedMillis = usedMillis;
        }
        @Override public String toString() { return label; }
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
        ControllerLog.info("ADB/Install", "Embedded APK materialized size=" + output.length());
        return output;
    }

    private String installApk(File apk, ApkIdentity identity,
                              ProgressListener progress) throws Exception {
        long size = apk.length();
        long requestId = ControllerLog.request("ADB/PackageManager",
                "exec:cmd package install -r -S " + size + " binary=<omitted>");
        try (AdbStream stream = manager.openStream("exec:cmd package install -r -S " + size);
             InputStream input = new java.io.FileInputStream(apk)) {
            OutputStream output = stream.openOutputStream();
            byte[] buffer = new byte[64 * 1024];
            long sent = 0;
            int lastPercent = -1;
            int lastLoggedPercent = -10;
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                sent += read;
                int percent = (int) Math.min(100, (sent * 100L) / size);
                if (percent != lastPercent) {
                    lastPercent = percent;
                    progress.onProgress(percent);
                }
                if (percent >= lastLoggedPercent + 10 || percent == 100) {
                    lastLoggedPercent = percent;
                    ControllerLog.info("ADB/PackageManager",
                            "#" + requestId + " uploadProgress=" + percent + "% bytes="
                                    + sent + "/" + size);
                }
            }
            output.flush();
            ControllerLog.result("ADB/PackageManager",
                    "IN PROGRESS: APK UPLOADED target=" + connectedHost + ":" + connectedPort
                            + " bytes=" + size
                            + " expectedVersion=" + identity.versionName
                            + " expectedVersionCode=" + identity.versionCode
                            + "; waiting for the target Package Manager");
            progress.onWaitingForPackageManager(0);
            String response = readInstallResponse(stream, requestId, identity, progress);
            ControllerLog.response("ADB/PackageManager", requestId, response);
            if (!response.toLowerCase(java.util.Locale.ROOT).contains("success")) {
                throw new IllegalStateException(response.isBlank()
                        ? "Package Manager returned no installation result"
                        : response.trim());
            }
            return response.trim();
        } catch (Exception exception) {
            ControllerLog.failure("ADB/PackageManager", requestId,
                    "APK install request failed", exception);
            throw exception;
        }
    }

    private String readInstallResponse(AdbStream stream, long requestId, ApkIdentity identity,
                                       ProgressListener progress) throws Exception {
        ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "adb-install-response");
            thread.setDaemon(true);
            return thread;
        });
        Future<String> future = reader.submit(() -> {
            StringBuilder output = new StringBuilder();
            InputStream input = stream.openInputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) > 0) {
                output.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
            }
            return output.toString();
        });
        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos + TimeUnit.SECONDS.toNanos(120);
        try {
            while (System.nanoTime() < deadlineNanos) {
                try {
                    return future.get(3, TimeUnit.SECONDS);
                } catch (TimeoutException timeout) {
                    int elapsedSeconds = (int) TimeUnit.NANOSECONDS.toSeconds(
                            System.nanoTime() - startedNanos
                    );
                    progress.onWaitingForPackageManager(elapsedSeconds);
                    ControllerLog.info("ADB/PackageManager",
                            "#" + requestId + " final response still open after "
                                    + elapsedSeconds + "s; probing installed package version");
                    try {
                        String packageDump = runShell("dumpsys package " + BLOCKER_PACKAGE
                                + " | grep -E 'versionCode=|versionName='");
                        if (InstalledPackageProbe.matches(
                                packageDump,
                                identity.versionName,
                                identity.versionCode
                        )) {
                            ControllerLog.warning("ADB/PackageManager",
                                    "#" + requestId + " target kept the install response open, "
                                            + "but the requested package version is installed; "
                                            + "continuing configuration", null);
                            return "Success (verified installed version=" + identity.versionName
                                    + " versionCode=" + identity.versionCode
                                    + "; OEM Package Manager kept the response stream open)";
                        }
                    } catch (Exception probeFailure) {
                        ControllerLog.warning("ADB/PackageManager",
                                "#" + requestId + " installed-version probe failed; "
                                        + "continuing to wait for the original response",
                                probeFailure);
                    }
                } catch (ExecutionException executionException) {
                    Throwable cause = executionException.getCause();
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    }
                    throw executionException;
                }
            }
            throw new IllegalStateException(
                    "Package Manager returned no final response for 120 seconds and the "
                            + "requested app version could not be verified"
            );
        } finally {
            future.cancel(true);
            reader.shutdownNow();
        }
    }

    private ApkIdentity readApkIdentity(File apk) {
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), 0
        );
        if (archive == null || archive.versionName == null || archive.versionName.isBlank()) {
            throw new IllegalStateException("Unable to read embedded blocker APK version");
        }
        long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? archive.getLongVersionCode()
                : archive.versionCode;
        ControllerLog.info("ADB/Install", "Embedded APK identity package=" + BLOCKER_PACKAGE
                + " version=" + archive.versionName + " versionCode=" + versionCode);
        return new ApkIdentity(archive.versionName, versionCode);
    }

    private String runShell(String command) throws Exception {
        String safeCommand = command.contains(REMOTE_URI)
                ? "content call " + REMOTE_URI + " <parent-control arguments redacted>"
                : command;
        long requestId = ControllerLog.request("ADB/Shell", safeCommand);
        try (AdbStream stream = manager.openStream(
                "shell:" + command + "; echo " + END_MARKER)) {
            String response = readResponse(stream, 20, TimeUnit.SECONDS, END_MARKER);
            String cleaned = response.replace(END_MARKER, "").trim();
            ControllerLog.response("ADB/Shell", requestId, cleaned);
            return cleaned;
        } catch (Exception exception) {
            ControllerLog.failure("ADB/Shell", requestId, "Command failed", exception);
            throw exception;
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
            } catch (Exception closeException) {
                ControllerLog.warning("ADB/Response",
                        "Unable to close timed-out ADB stream", closeException);
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
