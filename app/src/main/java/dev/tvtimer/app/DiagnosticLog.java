package dev.tvtimer.app;

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

final class DiagnosticLog {
    private static final String TAG = "ScreenTimerDiagnostics";
    private static final String FILE_NAME = "diagnostics.log";
    private static final int MAX_BYTES = 32 * 1024;
    private static final int RETAIN_BYTES = 24 * 1024;
    private static final int QR_PAGE_BYTES = 1_100;
    private static final Object LOCK = new Object();
    private static boolean crashHandlerInstalled;

    private DiagnosticLog() {
    }

    static void installCrashHandler(Context context) {
        Context application = context.getApplicationContext();
        Context safeContext = application == null ? context : application;
        synchronized (LOCK) {
            if (crashHandlerInstalled) {
                return;
            }
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                append(safeContext, "FATAL", "Crash", thread.getName(), throwable);
                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                }
            });
            crashHandlerInstalled = true;
        }
        info(safeContext, "App", "Process started; SDK " + Build.VERSION.SDK_INT);
    }

    static void info(Context context, String source, String message) {
        Log.i(TAG, source + ": " + message);
        append(context, "INFO", source, message, null);
    }

    static void warning(Context context, String source, String message, Throwable throwable) {
        Log.w(TAG, source + ": " + message, throwable);
        append(context, "WARN", source, message, throwable);
    }

    static void error(Context context, String source, String message, Throwable throwable) {
        Log.e(TAG, source + ": " + message, throwable);
        append(context, "ERROR", source, message, throwable);
    }

    static List<String> qrPages(Context context) {
        return DiagnosticTextPager.split(snapshot(context), QR_PAGE_BYTES);
    }

    static String snapshot(Context context) {
        String events;
        synchronized (LOCK) {
            events = readFile(logFile(context));
        }
        StringBuilder result = new StringBuilder();
        result.append("Android Screen Timer diagnostics\n")
                .append("version=").append(BuildConfig.VERSION_NAME)
                .append(" sdk=").append(Build.VERSION.SDK_INT)
                .append(" device=").append(safe(Build.MANUFACTURER))
                .append(' ').append(safe(Build.MODEL)).append('\n')
                .append("No PIN, authenticator secret, account, network, or viewing history is logged.\n\n");
        result.append(events.isEmpty() ? "No diagnostic events yet.\n" : events);
        return result.toString();
    }

    private static void append(
            Context context,
            String level,
            String source,
            String message,
            Throwable throwable
    ) {
        if (context == null) {
            return;
        }
        String timestamp = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
        ).format(new Date());
        StringBuilder entry = new StringBuilder()
                .append(timestamp).append(' ')
                .append(level).append(' ')
                .append(safe(source)).append(" - ")
                .append(safe(message)).append('\n');
        if (throwable != null) {
            StringWriter trace = new StringWriter();
            throwable.printStackTrace(new PrintWriter(trace));
            entry.append(trace.toString().replace("\r", ""));
        }
        synchronized (LOCK) {
            File file = logFile(context);
            String current = readFile(file);
            String combined = current + entry;
            if (combined.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                combined = utf8Tail(combined, RETAIN_BYTES);
            }
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(combined.getBytes(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                Log.w(TAG, "Unable to persist diagnostic log", exception);
            }
        }
    }

    private static String readFile(File file) {
        if (!file.isFile()) {
            return "";
        }
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4_096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException exception) {
            Log.w(TAG, "Unable to read diagnostic log", exception);
            return "";
        }
    }

    private static String utf8Tail(String value, int maxBytes) {
        int start = value.length();
        int bytes = 0;
        while (start > 0) {
            int codePoint = value.codePointBefore(start);
            String symbol = new String(Character.toChars(codePoint));
            int symbolBytes = symbol.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + symbolBytes > maxBytes) {
                break;
            }
            bytes += symbolBytes;
            start -= Character.charCount(codePoint);
        }
        String tail = value.substring(start);
        int firstLineBreak = tail.indexOf('\n');
        return firstLineBreak >= 0 && firstLineBreak + 1 < tail.length()
                ? tail.substring(firstLineBreak + 1)
                : tail;
    }

    private static File logFile(Context context) {
        Context application = context.getApplicationContext();
        Context safeContext = application == null ? context : application;
        return new File(safeContext.getFilesDir(), FILE_NAME);
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replace("\r", " ");
    }
}
