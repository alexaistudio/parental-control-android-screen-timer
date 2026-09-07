package dev.tvtimer.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A control surface available only to the local ADB shell/root identity. */
public final class RemoteControlProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.tvtimer.app.parentcontrol";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    private ConfigStore store;

    @Override public boolean onCreate() { store = new ConfigStore(getContext()); return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != Process.SHELL_UID && uid != Process.ROOT_UID && uid != Process.myUid())
            return failure("ADB authorization is required");
        if (!store.isConfigured()) return failure("Timer has not been configured on the TV");
        Bundle request = extras == null ? Bundle.EMPTY : extras;
        long now = System.currentTimeMillis();
        String day = DayKey.localDay(now);
        try {
            if ("state".equals(method)) return state(day, true);
            if ("adjust".equals(method)) {
                int minutes = requiredMinutes(request, false);
                ConfigStore.RemoteAdjustment adjustment = store.applyRemoteAdjustment(day, minutes,
                        request.getString("comment", ""), now);
                Bundle result = state(day, false);
                result.putLong("noticeId", adjustment.getId());
                result.putInt("changedMinutes", adjustment.getMinutes());
                return result;
            }
            if ("setGlobalLimit".equals(method)) {
                store.setDailyLimitMillis(requiredMinutes(request, true) * 60_000L);
                return state(day, false);
            }
            if ("setAppLimit".equals(method)) {
                store.setAppLimitMillis(request.getString("package", "").trim(),
                        requiredMinutes(request, true) * 60_000L);
                return state(day, false);
            }
            if ("removeAppLimit".equals(method)) {
                store.removeAppLimit(request.getString("package", "").trim());
                return state(day, false);
            }
            return failure("Unknown method");
        } catch (IllegalArgumentException exception) { return failure(exception.getMessage()); }
    }

    private int requiredMinutes(Bundle request, boolean positive) {
        if (!request.containsKey("minutes")) throw new IllegalArgumentException("minutes is required");
        int value = request.getInt("minutes");
        if (value == 0 || Math.abs((long) value) > 1440L || (positive && value < 1))
            throw new IllegalArgumentException("minutes must be from 1 to 1440");
        return value;
    }

    private Bundle state(String day, boolean includeApps) {
        ConfigStore.DayState dayState = store.getDayState(day);
        Bundle result = new Bundle();
        result.putBoolean("ok", true);
        result.putLong("dailyLimitMillis", store.getDailyLimitMillis());
        result.putLong("usedMillis", dayState.getUsedMillis());
        result.putLong("bonusMillis", dayState.getBonusMillis());
        result.putLong("remainingMillis", LimitMath.remaining(store.getDailyLimitMillis(), dayState.getBonusMillis(), dayState.getUsedMillis()));
        result.putBoolean("enforcementEnabled", store.isEnforcementEnabled());
        if (includeApps) result.putString("appsPayload", buildAppsPayload(dayState));
        return result;
    }

    private String buildAppsPayload(ConfigStore.DayState dayState) {
        Map<String, String> labels = new HashMap<>();
        collectApps(Intent.CATEGORY_LEANBACK_LAUNCHER, labels);
        collectApps(Intent.CATEGORY_LAUNCHER, labels);
        Map<String, Long> limits = store.getAppLimitsMillis();
        for (String packageName : limits.keySet()) {
            if (!labels.containsKey(packageName)) labels.put(packageName, packageName);
        }
        List<String> packages = new ArrayList<>(labels.keySet());
        Collections.sort(packages, (left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(labels.get(left), labels.get(right)));
        StringBuilder payload = new StringBuilder();
        for (String packageName : packages) {
            String label = labels.get(packageName).replace('\t', ' ').replace('\n', ' ');
            payload.append(packageName).append('\t').append(label).append('\t')
                    .append(limits.containsKey(packageName) ? limits.get(packageName) : 0L).append('\t')
                    .append(dayState.getAppUsedMillis(packageName)).append('\n');
        }
        return Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
    }

    private void collectApps(String category, Map<String, String> destination) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : getContext().getPackageManager().queryIntentActivities(intent, 0)) {
            if (info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            CharSequence label = info.loadLabel(getContext().getPackageManager());
            if (!destination.containsKey(packageName))
                destination.put(packageName, label == null ? packageName : label.toString());
        }
    }

    private Bundle failure(String message) {
        Bundle result = new Bundle(); result.putBoolean("ok", false);
        result.putString("error", message == null ? "Request failed" : message); return result;
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
