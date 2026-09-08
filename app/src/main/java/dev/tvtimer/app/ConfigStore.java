package dev.tvtimer.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressLint("ApplySharedPref")
public final class ConfigStore {
    private static final String PREFERENCES = "tv_timer_state";
    private static final String KEY_CONFIGURED = "configured";
    private static final String KEY_ENFORCEMENT_ENABLED = "enforcement_enabled";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_ITERATIONS = "pin_iterations";
    private static final String KEY_DAILY_LIMIT = "daily_limit_ms";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_SELECTED_PACKAGES = "selected_packages";
    private static final String KEY_USAGE_DAY = "usage_day";
    private static final String KEY_USAGE_MILLIS = "usage_ms";
    private static final String KEY_BONUS_MILLIS = "bonus_ms";
    private static final String KEY_RECOVERY_REQUESTED = "recovery_requested";
    private static final String KEY_USB_RECOVERY_ENABLED = "usb_recovery_enabled";
    private static final String KEY_PARENT_MODE_GESTURE_ENABLED = "parent_mode_gesture_enabled";
    private static final String KEY_SYSTEM_SETTINGS_PROTECTION_ENABLED =
            "system_settings_protection_enabled";
    private static final String KEY_MAINTENANCE_UNTIL = "maintenance_until_ms";
    private static final String KEY_AUTHENTICATOR_SECRET = "authenticator_secret";
    private static final String KEY_DEFAULT_EXTENSION_MINUTES = "default_extension_minutes";
    private static final String KEY_LAUNCHER_PROFILE = "launcher_profile";
    private static final String KEY_USAGE_WARNING_INTERVAL_MINUTES = "usage_warning_interval_minutes";
    private static final String KEY_USAGE_WARNING_DAY = "usage_warning_day";
    private static final String KEY_LAST_USAGE_WARNING_MINUTES = "last_usage_warning_minutes";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_EMERGENCY_SALT = "emergency_salt";
    private static final String KEY_EMERGENCY_HASH = "emergency_hash";
    private static final String KEY_EMERGENCY_ITERATIONS = "emergency_iterations";
    private static final String KEY_EMERGENCY_FAILURES = "emergency_failures";
    private static final String KEY_EMERGENCY_BLOCKED_UNTIL = "emergency_blocked_until";
    private static final String KEY_PARENT_SETTINGS_GRANT_UNTIL = "parent_settings_grant_until";
    private static final String KEY_REMOTE_NOTICE_ID = "remote_notice_id";
    private static final String KEY_REMOTE_NOTICE_MINUTES = "remote_notice_minutes";
    private static final String KEY_REMOTE_NOTICE_COMMENT = "remote_notice_comment";
    private static final String KEY_REMOTE_NOTICE_UNTIL = "remote_notice_until";
    private static final String KEY_BLOCKER_AUDIO_MUTED = "blocker_audio_muted";
    private static final String KEY_BLOCKER_AUDIO_VOLUME = "blocker_audio_volume";
    private static final String KEY_APP_LIMITS = "app_limits";
    private static final String KEY_APP_USAGE = "app_usage";
    private static final String KEY_APP_BONUSES = "app_bonuses";

    public static final long DEFAULT_LIMIT_MILLIS = 60L * 60L * 1_000L;
    public static final long MAINTENANCE_WINDOW_MILLIS = 2L * 60L * 1_000L;
    public static final int DEFAULT_EXTENSION_MINUTES = 15;
    public static final long EMERGENCY_LOCK_MILLIS = 30L * 60L * 1_000L;

    private final SharedPreferences preferences;

    public ConfigStore(Context context) {
        Context application = context.getApplicationContext();
        Context storageContext = application == null ? context : application;
        preferences = storageContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public boolean isConfigured() {
        return preferences.getBoolean(KEY_CONFIGURED, false);
    }

    public boolean isEnforcementEnabled() {
        return isConfigured() && preferences.getBoolean(KEY_ENFORCEMENT_ENABLED, false);
    }

    public long getDailyLimitMillis() {
        return preferences.getLong(KEY_DAILY_LIMIT, DEFAULT_LIMIT_MILLIS);
    }

    public String getScope() {
        return preferences.getString(KEY_SCOPE, AppScope.ALL);
    }

    public Set<String> getSelectedPackages() {
        Set<String> stored = preferences.getStringSet(KEY_SELECTED_PACKAGES, Collections.emptySet());
        return stored == null ? Collections.emptySet() : new HashSet<>(stored);
    }

    public boolean updateSelectedPackages(Set<String> selectedPackages) {
        Set<String> safeSelectedPackages = selectedPackages == null
                ? Collections.emptySet()
                : selectedPackages;
        return preferences.edit()
                .putStringSet(KEY_SELECTED_PACKAGES, new HashSet<>(safeSelectedPackages))
                .commit();
    }

    public boolean configure(
            String pin,
            String emergencyCode,
            long dailyLimitMillis,
            String scope,
            Set<String> selectedPackages
    ) {
        validateSettings(dailyLimitMillis, scope, selectedPackages);
        Set<String> safeSelectedPackages = selectedPackages == null
                ? Collections.emptySet()
                : selectedPackages;
        PinHasher.Record pinRecord = PinHasher.create(pin);
        if (!EmergencyCode.isValid(emergencyCode)) {
            throw new IllegalArgumentException("Emergency code must contain four digits");
        }
        PinHasher.Record emergencyRecord = PinHasher.create(emergencyCode);
        return preferences.edit()
                .putBoolean(KEY_CONFIGURED, true)
                .putBoolean(KEY_ENFORCEMENT_ENABLED, true)
                .putString(KEY_PIN_SALT, pinRecord.getSaltHex())
                .putString(KEY_PIN_HASH, pinRecord.getHashHex())
                .putInt(KEY_PIN_ITERATIONS, pinRecord.getIterations())
                .putString(KEY_EMERGENCY_SALT, emergencyRecord.getSaltHex())
                .putString(KEY_EMERGENCY_HASH, emergencyRecord.getHashHex())
                .putInt(KEY_EMERGENCY_ITERATIONS, emergencyRecord.getIterations())
                .putInt(KEY_EMERGENCY_FAILURES, 0)
                .putLong(KEY_EMERGENCY_BLOCKED_UNTIL, 0L)
                .putLong(KEY_DAILY_LIMIT, dailyLimitMillis)
                .putString(KEY_SCOPE, scope)
                .putStringSet(KEY_SELECTED_PACKAGES, new HashSet<>(safeSelectedPackages))
                .putBoolean(KEY_RECOVERY_REQUESTED, false)
                .putBoolean(KEY_USB_RECOVERY_ENABLED, true)
                .putBoolean(KEY_PARENT_MODE_GESTURE_ENABLED, true)
                .putBoolean(KEY_SYSTEM_SETTINGS_PROTECTION_ENABLED, true)
                .putInt(KEY_DEFAULT_EXTENSION_MINUTES, DEFAULT_EXTENSION_MINUTES)
                .putInt(KEY_USAGE_WARNING_INTERVAL_MINUTES, UsageWarningPolicy.DISABLED)
                .putString(KEY_LAUNCHER_PROFILE, LauncherProfile.DEFAULT)
                .commit();
    }

    public boolean updateSettings(
            long dailyLimitMillis,
            String scope,
            Set<String> selectedPackages,
            boolean enforcementEnabled,
            int defaultExtensionMinutes,
            int usageWarningIntervalMinutes,
            String launcherProfile,
            boolean usbRecoveryEnabled,
            boolean parentModeGestureEnabled,
            boolean systemSettingsProtectionEnabled
    ) {
        validateSettings(dailyLimitMillis, scope, selectedPackages);
        ExtensionDurationPolicy.requireSupported(defaultExtensionMinutes);
        UsageWarningPolicy.requireSupported(usageWarningIntervalMinutes);
        LauncherProfile.requireSupported(launcherProfile);
        Set<String> safeSelectedPackages = selectedPackages == null
                ? Collections.emptySet()
                : selectedPackages;
        SharedPreferences.Editor editor = preferences.edit()
                .putLong(KEY_DAILY_LIMIT, dailyLimitMillis)
                .putString(KEY_SCOPE, scope)
                .putStringSet(KEY_SELECTED_PACKAGES, new HashSet<>(safeSelectedPackages))
                .putBoolean(KEY_ENFORCEMENT_ENABLED, enforcementEnabled)
                .putInt(KEY_DEFAULT_EXTENSION_MINUTES, defaultExtensionMinutes)
                .putInt(KEY_USAGE_WARNING_INTERVAL_MINUTES, usageWarningIntervalMinutes)
                .putString(KEY_LAUNCHER_PROFILE, launcherProfile)
                .putBoolean(KEY_USB_RECOVERY_ENABLED, usbRecoveryEnabled)
                .putBoolean(KEY_PARENT_MODE_GESTURE_ENABLED, parentModeGestureEnabled)
                .putBoolean(
                        KEY_SYSTEM_SETTINGS_PROTECTION_ENABLED,
                        systemSettingsProtectionEnabled
                );
        if (!usbRecoveryEnabled) {
            editor.putBoolean(KEY_RECOVERY_REQUESTED, false);
        }
        return editor.commit();
    }

    public int getDefaultExtensionMinutes() {
        int value = preferences.getInt(
                KEY_DEFAULT_EXTENSION_MINUTES,
                DEFAULT_EXTENSION_MINUTES
        );
        return value != ExtensionDurationPolicy.ASK_EVERY_TIME
                && ExtensionDurationPolicy.isSupported(value)
                ? value
                : DEFAULT_EXTENSION_MINUTES;
    }

    public int getUsageWarningIntervalMinutes() {
        int value = preferences.getInt(
                KEY_USAGE_WARNING_INTERVAL_MINUTES,
                UsageWarningPolicy.DISABLED
        );
        return UsageWarningPolicy.isSupported(value) ? value : UsageWarningPolicy.DISABLED;
    }

    public String getLanguage() {
        return AppLanguage.normalize(preferences.getString(
                KEY_LANGUAGE,
                AppLanguage.DEFAULT_LANGUAGE
        ));
    }

    public boolean setLanguage(String language) {
        return preferences.edit()
                .putString(KEY_LANGUAGE, AppLanguage.normalize(language))
                .commit();
    }

    public long getDueUsageWarningMinutes(String dayKey, long usedMillis) {
        int intervalMinutes = getUsageWarningIntervalMinutes();
        String warningDay = preferences.getString(KEY_USAGE_WARNING_DAY, null);
        long lastAcknowledged = dayKey.equals(warningDay)
                ? preferences.getLong(KEY_LAST_USAGE_WARNING_MINUTES, 0L)
                : 0L;
        return UsageWarningPolicy.dueThresholdMinutes(
                intervalMinutes,
                lastAcknowledged,
                usedMillis
        );
    }

    public boolean acknowledgeUsageWarning(String dayKey, long thresholdMinutes) {
        if (dayKey == null || thresholdMinutes <= 0L) {
            return false;
        }
        return preferences.edit()
                .putString(KEY_USAGE_WARNING_DAY, dayKey)
                .putLong(KEY_LAST_USAGE_WARNING_MINUTES, thresholdMinutes)
                .commit();
    }

    public String getLauncherProfile() {
        String value = preferences.getString(KEY_LAUNCHER_PROFILE, LauncherProfile.DEFAULT);
        return LauncherProfile.isSupported(value) ? value : LauncherProfile.DEFAULT;
    }

    public String getOrCreateAuthenticatorSecret() {
        String existing = preferences.getString(KEY_AUTHENTICATOR_SECRET, null);
        if (TotpAuthenticator.isValidSecret(existing)) {
            return existing;
        }
        String generated = TotpAuthenticator.generateSecret();
        if (!preferences.edit().putString(KEY_AUTHENTICATOR_SECRET, generated).commit()) {
            throw new IllegalStateException("Unable to store authenticator secret");
        }
        return generated;
    }

    public boolean verifyParentCode(String code, long nowMillis) {
        if (verifyPin(code)) {
            return true;
        }
        String secret = preferences.getString(KEY_AUTHENTICATOR_SECRET, null);
        return TotpAuthenticator.verify(code, secret, nowMillis);
    }

    /** Accepts every code that may be entered on a lock screen. Emergency codes remain one-time. */
    public boolean verifyAnyAccessCode(String code, long nowMillis) {
        return verifyParentCode(code, nowMillis) || consumeEmergencyCode(code, nowMillis);
    }

    public boolean verifyAuthenticatorCode(String code, long nowMillis) {
        String secret = preferences.getString(KEY_AUTHENTICATOR_SECRET, null);
        return TotpAuthenticator.verify(code, secret, nowMillis);
    }

    public synchronized boolean consumeEmergencyCode(String code, long nowMillis) {
        if (!EmergencyCode.isValid(code)
                || nowMillis < preferences.getLong(KEY_EMERGENCY_BLOCKED_UNTIL, 0L)) {
            return false;
        }
        boolean verified = PinHasher.verify(
                code,
                preferences.getString(KEY_EMERGENCY_SALT, null),
                preferences.getString(KEY_EMERGENCY_HASH, null),
                preferences.getInt(KEY_EMERGENCY_ITERATIONS, PinHasher.CURRENT_ITERATIONS)
        );
        if (verified) {
            return preferences.edit()
                    .remove(KEY_EMERGENCY_SALT)
                    .remove(KEY_EMERGENCY_HASH)
                    .remove(KEY_EMERGENCY_ITERATIONS)
                    .putInt(KEY_EMERGENCY_FAILURES, 0)
                    .putLong(KEY_EMERGENCY_BLOCKED_UNTIL, 0L)
                    .commit();
        }
        int failures = preferences.getInt(KEY_EMERGENCY_FAILURES, 0) + 1;
        SharedPreferences.Editor editor = preferences.edit();
        if (failures >= 3) {
            editor.putInt(KEY_EMERGENCY_FAILURES, 0)
                    .putLong(KEY_EMERGENCY_BLOCKED_UNTIL, nowMillis + EMERGENCY_LOCK_MILLIS);
        } else {
            editor.putInt(KEY_EMERGENCY_FAILURES, failures);
        }
        editor.commit();
        return false;
    }

    public boolean replaceEmergencyCode(String emergencyCode) {
        if (!EmergencyCode.isValid(emergencyCode)) {
            throw new IllegalArgumentException("Emergency code must contain four digits");
        }
        PinHasher.Record record = PinHasher.create(emergencyCode);
        return preferences.edit()
                .putString(KEY_EMERGENCY_SALT, record.getSaltHex())
                .putString(KEY_EMERGENCY_HASH, record.getHashHex())
                .putInt(KEY_EMERGENCY_ITERATIONS, record.getIterations())
                .putInt(KEY_EMERGENCY_FAILURES, 0)
                .putLong(KEY_EMERGENCY_BLOCKED_UNTIL, 0L)
                .commit();
    }

    public boolean hasEmergencyCode() {
        return preferences.contains(KEY_EMERGENCY_HASH);
    }

    public boolean grantParentSettingsLaunch(long nowMillis) {
        return preferences.edit()
                .putLong(KEY_PARENT_SETTINGS_GRANT_UNTIL, nowMillis + 30_000L)
                .commit();
    }

    public synchronized boolean consumeParentSettingsLaunch(long nowMillis) {
        long allowedUntil = preferences.getLong(KEY_PARENT_SETTINGS_GRANT_UNTIL, 0L);
        preferences.edit().remove(KEY_PARENT_SETTINGS_GRANT_UNTIL).commit();
        return nowMillis <= allowedUntil;
    }

    public boolean changePin(String newPin) {
        PinHasher.Record pinRecord = PinHasher.create(newPin);
        return preferences.edit()
                .putString(KEY_PIN_SALT, pinRecord.getSaltHex())
                .putString(KEY_PIN_HASH, pinRecord.getHashHex())
                .putInt(KEY_PIN_ITERATIONS, pinRecord.getIterations())
                .commit();
    }

    public boolean verifyPin(String pin) {
        if (!isConfigured()) {
            return false;
        }
        int iterations = preferences.getInt(
                KEY_PIN_ITERATIONS,
                PinHasher.LEGACY_ITERATIONS
        );
        boolean verified = PinHasher.verify(
                pin,
                preferences.getString(KEY_PIN_SALT, null),
                preferences.getString(KEY_PIN_HASH, null),
                iterations
        );
        if (verified && iterations != PinHasher.CURRENT_ITERATIONS) {
            PinHasher.Record upgraded = PinHasher.create(pin);
            preferences.edit()
                    .putString(KEY_PIN_SALT, upgraded.getSaltHex())
                    .putString(KEY_PIN_HASH, upgraded.getHashHex())
                    .putInt(KEY_PIN_ITERATIONS, upgraded.getIterations())
                    .commit();
        }
        return verified;
    }

    public synchronized DayState getDayState(String dayKey) {
        ensureDay(dayKey);
        return new DayState(
                preferences.getLong(KEY_USAGE_MILLIS, 0L),
                preferences.getLong(KEY_BONUS_MILLIS, 0L),
                decodeLongMap(preferences.getString(KEY_APP_USAGE, "")),
                decodeSignedLongMap(preferences.getString(KEY_APP_BONUSES, ""))
        );
    }

    public synchronized boolean addUsage(String dayKey, long deltaMillis) {
        return addUsage(dayKey, deltaMillis, Collections.emptyMap());
    }

    public synchronized boolean addUsage(
            String dayKey,
            long globalDeltaMillis,
            Map<String, Long> appDeltasMillis
    ) {
        ensureDay(dayKey);
        long current = preferences.getLong(KEY_USAGE_MILLIS, 0L);
        long safeDelta = Math.max(0L, globalDeltaMillis);
        long updated = current > Long.MAX_VALUE - safeDelta ? Long.MAX_VALUE : current + safeDelta;
        Map<String, Long> appUsage = decodeLongMap(preferences.getString(KEY_APP_USAGE, ""));
        if (appDeltasMillis != null) {
            for (Map.Entry<String, Long> entry : appDeltasMillis.entrySet()) {
                String packageName = entry.getKey();
                long delta = entry.getValue() == null ? 0L : Math.max(0L, entry.getValue());
                if (!isValidPackageName(packageName) || delta == 0L) {
                    continue;
                }
                Long prior = appUsage.get(packageName);
                long previous = prior == null ? 0L : prior;
                appUsage.put(packageName,
                        previous > Long.MAX_VALUE - delta ? Long.MAX_VALUE : previous + delta);
            }
        }
        return preferences.edit()
                .putLong(KEY_USAGE_MILLIS, updated)
                .putString(KEY_APP_USAGE, encodeLongMap(appUsage))
                .commit();
    }

    public Map<String, Long> getAppLimitsMillis() {
        return decodeLongMap(preferences.getString(KEY_APP_LIMITS, ""));
    }

    public boolean setAppLimitMillis(String packageName, long limitMillis) {
        if (!isValidPackageName(packageName)
                || limitMillis < 60_000L
                || limitMillis > 24L * 60L * 60L * 1_000L) {
            throw new IllegalArgumentException("Invalid application limit");
        }
        Map<String, Long> limits = getAppLimitsMillis();
        limits.put(packageName, limitMillis);
        return preferences.edit().putString(KEY_APP_LIMITS, encodeLongMap(limits)).commit();
    }

    public boolean removeAppLimit(String packageName) {
        Map<String, Long> limits = getAppLimitsMillis();
        if (!limits.containsKey(packageName)) {
            return true;
        }
        limits.remove(packageName);
        return preferences.edit().putString(KEY_APP_LIMITS, encodeLongMap(limits)).commit();
    }

    /** Сбрасывает дневной бонус/штраф конкретного приложения (на текущий день). */
    public boolean resetAppBonus(String dayKey, String packageName) {
        if (!isValidPackageName(packageName)) {
            throw new IllegalArgumentException("Invalid application package");
        }
        ensureDay(dayKey);
        Map<String, Long> bonuses = decodeSignedLongMap(preferences.getString(KEY_APP_BONUSES, ""));
        if (bonuses.containsKey(packageName)) {
            bonuses.remove(packageName);
            return preferences.edit()
                    .putString(KEY_APP_BONUSES, encodeSignedLongMap(bonuses))
                    .commit();
        }
        return true;
    }

    public boolean setDailyLimitMillis(long limitMillis) {
        if (limitMillis < 60_000L || limitMillis > 24L * 60L * 60L * 1_000L) {
            throw new IllegalArgumentException("Invalid daily limit");
        }
        return preferences.edit().putLong(KEY_DAILY_LIMIT, limitMillis).commit();
    }

    public synchronized boolean addBonus(String dayKey, long bonusMillis) {
        ensureDay(dayKey);
        long current = preferences.getLong(KEY_BONUS_MILLIS, 0L);
        long safeBonus = Math.max(0L, bonusMillis);
        long updated = current > Long.MAX_VALUE - safeBonus ? Long.MAX_VALUE : current + safeBonus;
        preferences.edit().putLong(KEY_BONUS_MILLIS, updated).apply();
        return true;
    }

    public synchronized RemoteAdjustment applyRemoteAdjustment(
            String dayKey,
            int minutes,
            String comment,
            long nowMillis
    ) {
        if (minutes == 0 || Math.abs((long) minutes) > 1_440L) {
            throw new IllegalArgumentException("Adjustment must be between -1440 and 1440 minutes");
        }
        ensureDay(dayKey);
        long delta = minutes * 60_000L;
        long current = preferences.getLong(KEY_BONUS_MILLIS, 0L);
        long updated;
        try {
            updated = Math.addExact(current, delta);
        } catch (ArithmeticException exception) {
            updated = delta > 0L ? Long.MAX_VALUE : Long.MIN_VALUE + 1L;
        }
        long id = preferences.getLong(KEY_REMOTE_NOTICE_ID, 0L) + 1L;
        String safeComment = comment == null ? "" : comment.trim();
        if (safeComment.length() > 120) {
            safeComment = safeComment.substring(0, 120);
        }
        long until = nowMillis + 5_000L;
        if (!preferences.edit()
                .putLong(KEY_BONUS_MILLIS, updated)
                .putLong(KEY_REMOTE_NOTICE_ID, id)
                .putInt(KEY_REMOTE_NOTICE_MINUTES, minutes)
                .putString(KEY_REMOTE_NOTICE_COMMENT, safeComment)
                .putLong(KEY_REMOTE_NOTICE_UNTIL, until)
                .commit()) {
            throw new IllegalStateException("Unable to save remote adjustment");
        }
        return new RemoteAdjustment(id, minutes, safeComment, until);
    }

    public synchronized RemoteAdjustment applyRemoteAppAdjustment(
            String dayKey, String packageName, int minutes, String comment, long nowMillis) {
        if (!isValidPackageName(packageName) || minutes == 0 || Math.abs((long) minutes) > 1_440L) {
            throw new IllegalArgumentException("Invalid application adjustment");
        }
        ensureDay(dayKey);
        Map<String, Long> bonuses = decodeSignedLongMap(preferences.getString(KEY_APP_BONUSES, ""));
        long current = bonuses.containsKey(packageName) ? bonuses.get(packageName) : 0L;
        long delta = minutes * 60_000L;
        long updated;
        try { updated = Math.addExact(current, delta); }
        catch (ArithmeticException exception) { updated = delta > 0L ? Long.MAX_VALUE : Long.MIN_VALUE + 1L; }
        bonuses.put(packageName, updated);
        long id = preferences.getLong(KEY_REMOTE_NOTICE_ID, 0L) + 1L;
        String safeComment = comment == null ? "" : comment.trim();
        if (safeComment.length() > 120) safeComment = safeComment.substring(0, 120);
        long until = nowMillis + 5_000L;
        if (!preferences.edit().putString(KEY_APP_BONUSES, encodeSignedLongMap(bonuses))
                .putLong(KEY_REMOTE_NOTICE_ID, id).putInt(KEY_REMOTE_NOTICE_MINUTES, minutes)
                .putString(KEY_REMOTE_NOTICE_COMMENT, safeComment)
                .putLong(KEY_REMOTE_NOTICE_UNTIL, until).commit()) {
            throw new IllegalStateException("Unable to save application adjustment");
        }
        return new RemoteAdjustment(id, minutes, safeComment, until);
    }

    public RemoteAdjustment getRemoteAdjustment(long nowMillis) {
        long until = preferences.getLong(KEY_REMOTE_NOTICE_UNTIL, 0L);
        if (nowMillis >= until) {
            return null;
        }
        return new RemoteAdjustment(
                preferences.getLong(KEY_REMOTE_NOTICE_ID, 0L),
                preferences.getInt(KEY_REMOTE_NOTICE_MINUTES, 0),
                preferences.getString(KEY_REMOTE_NOTICE_COMMENT, ""),
                until
        );
    }

    public boolean beginBlockerMute(int currentMusicVolume) {
        if (preferences.getBoolean(KEY_BLOCKER_AUDIO_MUTED, false)) {
            return false;
        }
        return preferences.edit()
                .putBoolean(KEY_BLOCKER_AUDIO_MUTED, true)
                .putInt(KEY_BLOCKER_AUDIO_VOLUME, Math.max(0, currentMusicVolume))
                .commit();
    }

    public int endBlockerMute() {
        if (!preferences.getBoolean(KEY_BLOCKER_AUDIO_MUTED, false)) {
            return -1;
        }
        int previousVolume = preferences.getInt(KEY_BLOCKER_AUDIO_VOLUME, -1);
        preferences.edit()
                .remove(KEY_BLOCKER_AUDIO_VOLUME)
                .putBoolean(KEY_BLOCKER_AUDIO_MUTED, false)
                .commit();
        return previousVolume;
    }

    public boolean setEnforcementEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENFORCEMENT_ENABLED, enabled).apply();
        return true;
    }

    public boolean grantMaintenanceWindow() {
        return preferences.edit()
                .putLong(
                        KEY_MAINTENANCE_UNTIL,
                        System.currentTimeMillis() + MAINTENANCE_WINDOW_MILLIS
                )
                .commit();
    }

    public boolean isMaintenanceAllowed(long nowMillis) {
        return nowMillis < preferences.getLong(KEY_MAINTENANCE_UNTIL, 0L);
    }

    public boolean requestRecoveryMode() {
        if (!isUsbRecoveryEnabled()) {
            return false;
        }
        return preferences.edit().putBoolean(KEY_RECOVERY_REQUESTED, true).commit();
    }

    public boolean isUsbRecoveryEnabled() {
        return preferences.getBoolean(KEY_USB_RECOVERY_ENABLED, true);
    }

    public boolean isParentModeGestureEnabled() {
        return preferences.getBoolean(KEY_PARENT_MODE_GESTURE_ENABLED, true);
    }

    public boolean isSystemSettingsProtectionEnabled() {
        return preferences.getBoolean(KEY_SYSTEM_SETTINGS_PROTECTION_ENABLED, true);
    }

    public boolean isRecoveryModeRequested() {
        return preferences.getBoolean(KEY_RECOVERY_REQUESTED, false);
    }

    public boolean clearRecoveryModeRequest() {
        return preferences.edit().putBoolean(KEY_RECOVERY_REQUESTED, false).commit();
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    static boolean affectsRuntimeConfiguration(String key) {
        return key == null
                || KEY_CONFIGURED.equals(key)
                || KEY_ENFORCEMENT_ENABLED.equals(key)
                || KEY_DAILY_LIMIT.equals(key)
                || KEY_SCOPE.equals(key)
                || KEY_SELECTED_PACKAGES.equals(key)
                || KEY_MAINTENANCE_UNTIL.equals(key)
                || KEY_RECOVERY_REQUESTED.equals(key)
                || KEY_USB_RECOVERY_ENABLED.equals(key)
                || KEY_PARENT_MODE_GESTURE_ENABLED.equals(key)
                || KEY_SYSTEM_SETTINGS_PROTECTION_ENABLED.equals(key)
                || KEY_DEFAULT_EXTENSION_MINUTES.equals(key)
                || KEY_USAGE_WARNING_INTERVAL_MINUTES.equals(key)
                || KEY_AUTHENTICATOR_SECRET.equals(key)
                || KEY_BONUS_MILLIS.equals(key)
                || KEY_REMOTE_NOTICE_ID.equals(key)
                || KEY_REMOTE_NOTICE_MINUTES.equals(key)
                || KEY_REMOTE_NOTICE_COMMENT.equals(key)
                || KEY_REMOTE_NOTICE_UNTIL.equals(key)
                || KEY_APP_LIMITS.equals(key)
                || KEY_APP_BONUSES.equals(key)
                || KEY_LANGUAGE.equals(key);
    }

    static boolean isLanguagePreference(String key) {
        return KEY_LANGUAGE.equals(key);
    }

    static boolean isParentModeGesturePreference(String key) {
        return KEY_PARENT_MODE_GESTURE_ENABLED.equals(key);
    }

    private void ensureDay(String dayKey) {
        String storedDay = preferences.getString(KEY_USAGE_DAY, null);
        if (!dayKey.equals(storedDay)) {
            preferences.edit()
                    .putString(KEY_USAGE_DAY, dayKey)
                    .putLong(KEY_USAGE_MILLIS, 0L)
                    .putLong(KEY_BONUS_MILLIS, 0L)
                    .putString(KEY_APP_USAGE, "")
                    .putString(KEY_APP_BONUSES, "")
                    .apply();
        }
    }

    private static boolean isValidPackageName(String packageName) {
        return packageName != null
                && packageName.length() <= 255
                && packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");
    }

    private static Map<String, Long> decodeLongMap(String encoded) {
        return decodeLongMap(encoded, false);
    }

    private static Map<String, Long> decodeSignedLongMap(String encoded) {
        return decodeLongMap(encoded, true);
    }

    private static Map<String, Long> decodeLongMap(String encoded, boolean allowNegative) {
        Map<String, Long> values = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return values;
        }
        for (String item : encoded.split(";")) {
            int separator = item.lastIndexOf('=');
            if (separator <= 0 || separator == item.length() - 1) {
                continue;
            }
            String key = item.substring(0, separator);
            try {
                long value = Long.parseLong(item.substring(separator + 1));
                if (isValidPackageName(key) && (allowNegative || value >= 0L)) {
                    values.put(key, value);
                }
            } catch (NumberFormatException ignored) {
                // Ignore a damaged individual record without losing the rest.
            }
        }
        return values;
    }

    private static String encodeLongMap(Map<String, Long> values) {
        return encodeLongMap(values, false);
    }

    private static String encodeSignedLongMap(Map<String, Long> values) {
        return encodeLongMap(values, true);
    }

    private static String encodeLongMap(Map<String, Long> values, boolean allowNegative) {
        StringBuilder encoded = new StringBuilder();
        java.util.List<String> keys = new java.util.ArrayList<>(values.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Long value = values.get(key);
            if (!isValidPackageName(key) || value == null || (!allowNegative && value < 0L)) continue;
            if (encoded.length() > 0) encoded.append(';');
            encoded.append(key).append('=').append(value);
        }
        return encoded.toString();
    }

    private static void validateSettings(
            long dailyLimitMillis,
            String scope,
            Set<String> selectedPackages
    ) {
        if (dailyLimitMillis < 60_000L || dailyLimitMillis > 24L * 60L * 60L * 1_000L) {
            throw new IllegalArgumentException("Daily limit is outside the supported range");
        }
        if (!AppScope.ALL.equals(scope) && !AppScope.SELECTED.equals(scope)) {
            throw new IllegalArgumentException("Unknown application scope");
        }
        if (AppScope.SELECTED.equals(scope)
                && (selectedPackages == null || selectedPackages.isEmpty())) {
            throw new IllegalArgumentException("At least one application must be selected");
        }
    }

    public static final class DayState {
        private final long usedMillis;
        private final long bonusMillis;
        private final Map<String, Long> appUsageMillis;
        private final Map<String, Long> appBonusMillis;

        DayState(long usedMillis, long bonusMillis, Map<String, Long> appUsageMillis,
                 Map<String, Long> appBonusMillis) {
            this.usedMillis = usedMillis;
            this.bonusMillis = bonusMillis;
            this.appUsageMillis = Collections.unmodifiableMap(new HashMap<>(appUsageMillis));
            this.appBonusMillis = Collections.unmodifiableMap(new HashMap<>(appBonusMillis));
        }

        public long getUsedMillis() {
            return usedMillis;
        }

        public long getBonusMillis() {
            return bonusMillis;
        }

        public long getAppUsedMillis(String packageName) {
            Long value = appUsageMillis.get(packageName);
            return value == null ? 0L : value;
        }

        public Map<String, Long> getAppUsageMillis() {
            return appUsageMillis;
        }

        public long getAppBonusMillis(String packageName) {
            Long value = appBonusMillis.get(packageName);
            return value == null ? 0L : value;
        }
    }

    public static final class RemoteAdjustment {
        private final long id;
        private final int minutes;
        private final String comment;
        private final long untilMillis;

        RemoteAdjustment(long id, int minutes, String comment, long untilMillis) {
            this.id = id;
            this.minutes = minutes;
            this.comment = comment == null ? "" : comment;
            this.untilMillis = untilMillis;
        }

        public long getId() { return id; }
        public int getMinutes() { return minutes; }
        public String getComment() { return comment; }
        public long getUntilMillis() { return untilMillis; }
    }
}
