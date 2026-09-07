package dev.tvtimer.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * A tiny ADB-facing control surface for the paired parent phone. Every call is
 * authenticated with a parent PIN or current authenticator code.
 */
public final class RemoteControlProvider extends ContentProvider {
    public static final String AUTHORITY = "dev.tvtimer.app.parentcontrol";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_STATE = "state";
    public static final String METHOD_ADJUST = "adjust";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_MINUTES = "minutes";
    public static final String EXTRA_COMMENT = "comment";

    private ConfigStore store;

    @Override
    public boolean onCreate() {
        store = new ConfigStore(getContext());
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle request = extras == null ? Bundle.EMPTY : extras;
        String code = request.getString(EXTRA_CODE, "").trim();
        long now = System.currentTimeMillis();
        if (!store.isConfigured()) {
            return failure("Timer has not been configured on the TV");
        }
        if (!store.verifyParentCode(code, now)) {
            return failure("Incorrect parent code");
        }
        String day = DayKey.localDay(now);
        if (METHOD_STATE.equals(method)) {
            return state(day);
        }
        if (METHOD_ADJUST.equals(method)) {
            if (!request.containsKey(EXTRA_MINUTES)) {
                return failure("minutes is required");
            }
            try {
                int minutes = request.getInt(EXTRA_MINUTES);
                ConfigStore.RemoteAdjustment adjustment = store.applyRemoteAdjustment(
                        day,
                        minutes,
                        request.getString(EXTRA_COMMENT, ""),
                        now
                );
                Bundle result = state(day);
                result.putLong("noticeId", adjustment.getId());
                result.putInt("changedMinutes", adjustment.getMinutes());
                return result;
            } catch (IllegalArgumentException exception) {
                return failure(exception.getMessage());
            }
        }
        return failure("Unknown method");
    }

    private Bundle state(String day) {
        ConfigStore.DayState dayState = store.getDayState(day);
        Bundle result = new Bundle();
        result.putBoolean("ok", true);
        result.putLong("dailyLimitMillis", store.getDailyLimitMillis());
        result.putLong("usedMillis", dayState.getUsedMillis());
        result.putLong("bonusMillis", dayState.getBonusMillis());
        result.putLong("remainingMillis", LimitMath.remaining(
                store.getDailyLimitMillis(),
                dayState.getBonusMillis(),
                dayState.getUsedMillis()
        ));
        result.putBoolean("enforcementEnabled", store.isEnforcementEnabled());
        return result;
    }

    private Bundle failure(String message) {
        Bundle result = new Bundle();
        result.putBoolean("ok", false);
        result.putString("error", message == null ? "Request failed" : message);
        return result;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
