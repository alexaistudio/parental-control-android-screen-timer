package dev.tvtimer.controller;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

final class ControllerLog {
    private static final String TAG = "ASTParentDiagnostics";
    private static final String FILE_NAME = "parent-installer-diagnostics.log";
    private static final int MAX_BYTES = 512 * 1024;
    private static final int RETAIN_BYTES = 384 * 1024;
    private static final int QR_PAGE_BYTES = 1_100;
    private static final Object LOCK = new Object();
    private static final AtomicLong REQUEST_IDS = new AtomicLong();

    private static volatile Context applicationContext;
    private static boolean crashHandlerInstalled;

    private ControllerLog() {
    }

    static void install(Context context) {
        Context application = context.getApplicationContext();
        applicationContext = application == null ? context : application;
        synchronized (LOCK) {
            if (!crashHandlerInstalled) {
                Thread.UncaughtExceptionHandler previous =
                        Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                    append("FATAL", "Crash", "Uncaught exception", throwable, thread.getName());
                    if (previous != null) {
                        previous.uncaughtException(thread, throwable);
                    }
                });
                crashHandlerInstalled = true;
            }
        }
        info("Process", "Started version=" + BuildConfig.VERSION_NAME
                + " sdk=" + Build.VERSION.SDK_INT
                + " device=" + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL));
    }

    static void info(String source, String message) {
        Log.i(TAG, source + ": " + SecretRedactor.redact(message));
        append("INFO", source, message, null, Thread.currentThread().getName());
    }

    static void warning(String source, String message, Throwable throwable) {
        Log.w(TAG, source + ": " + SecretRedactor.redact(message), throwable);
        append("WARN", source, message, throwable, Thread.currentThread().getName());
    }

    static void error(String source, String message, Throwable throwable) {
        Log.e(TAG, source + ": " + SecretRedactor.redact(message), throwable);
        append("ERROR", source, message, throwable, Thread.currentThread().getName());
    }

    static long request(String source, String message) {
        long id = REQUEST_IDS.incrementAndGet();
        append("REQUEST", source, "#" + id + " " + message, null,
                Thread.currentThread().getName());
        return id;
    }

    static void response(String source, long requestId, String message) {
        append("RESPONSE", source, "#" + requestId + " " + display(message), null,
                Thread.currentThread().getName());
    }

    static void failure(String source, long requestId, String message, Throwable throwable) {
        append("FAILURE", source, "#" + requestId + " " + message, throwable,
                Thread.currentThread().getName());
    }

    static String snapshot() {
        String events;
        synchronized (LOCK) {
            events = readFile(logFile());
        }
        return "Android Screen Timer Parent diagnostics\n"
                + "version=" + BuildConfig.VERSION_NAME
                + " sdk=" + Build.VERSION.SDK_INT
                + " device=" + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL) + "\n"
                + "Detailed local requests and responses are included. Pairing codes, private "
                + "ADB keys, and APK binary contents are never logged. Local IP addresses and "
                + "device details may be present.\n"
                + "The file is bounded to 512 KiB; when full, the oldest entries rotate out.\n\n"
                + (events.isEmpty() ? "No events yet.\n" : events);
    }

    static List<String> qrPages() {
        return DiagnosticTextPager.split(snapshot(), QR_PAGE_BYTES);
    }

    private static void append(String level, String source, String message, Throwable throwable,
                               String threadName) {
        Context context = applicationContext;
        if (context == null) {
            return;
        }
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        StringBuilder entry = new StringBuilder()
                .append(timestamp).append(' ')
                .append(level).append(' ')
                .append('[').append(safe(threadName)).append("] ")
                .append(safe(source)).append(" - ")
                .append(SecretRedactor.redact(message).replace("\r", ""))
                .append('\n');
        if (throwable != null) {
            StringWriter trace = new StringWriter();
            throwable.printStackTrace(new PrintWriter(trace));
            entry.append(SecretRedactor.redact(trace.toString()).replace("\r", ""));
        }
        byte[] bytes = entry.toString().getBytes(StandardCharsets.UTF_8);
        synchronized (LOCK) {
            File file = logFile();
            if (file.length() + bytes.length > MAX_BYTES) {
                String retained = utf8Tail(readFile(file), RETAIN_BYTES);
                String marker = timestamp + " INFO [" + safe(threadName)
                        + "] Log - Oldest entries rotated at 512 KiB\n";
                write(file, (marker + retained).getBytes(StandardCharsets.UTF_8), false);
            }
            write(file, bytes, true);
        }
    }

    private static void write(File file, byte[] bytes, boolean append) {
        try (FileOutputStream output = new FileOutputStream(file, append)) {
            output.write(bytes);
        } catch (IOException exception) {
            Log.e(TAG, "Unable to persist diagnostic log", exception);
        }
    }

    private static String readFile(File file) {
        if (!file.isFile()) {
            return "";
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException exception) {
            Log.e(TAG, "Unable to read diagnostic log", exception);
            return "";
        }
    }

    private static String utf8Tail(String value, int maxBytes) {
        int start = value.length();
        int bytes = 0;
        while (start > 0) {
            int codePoint = value.codePointBefore(start);
            int symbolBytes = new String(Character.toChars(codePoint))
                    .getBytes(StandardCharsets.UTF_8).length;
            if (bytes + symbolBytes > maxBytes) {
                break;
            }
            bytes += symbolBytes;
            start -= Character.charCount(codePoint);
        }
        String tail = value.substring(start);
        int firstLineBreak = tail.indexOf('\n');
        return firstLineBreak >= 0 ? tail.substring(firstLineBreak + 1) : tail;
    }

    private static File logFile() {
        return new File(applicationContext.getFilesDir(), FILE_NAME);
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "<empty response>" : value;
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replace("\r", " ").replace("\n", " ");
    }
}
