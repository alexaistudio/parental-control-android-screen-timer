package dev.tvtimer.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
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
    private static final String KEY_USB_RECOVERY = "usb_recovery";

    public static final long DEFAULT_LIMIT_MILLIS = 60L * 60L * 1_000L;
    public static final long EXTRA_TIME_MILLIS = 15L * 60L * 1_000L;

    private final SharedPreferences preferences;

    public ConfigStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
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

    public boolean configure(
            String pin,
            long dailyLimitMillis,
            String scope,
            Set<String> selectedPackages
    ) {
        validateSettings(dailyLimitMillis, scope, selectedPackages);
        Set<String> safeSelectedPackages = selectedPackages == null
                ? Collections.emptySet()
                : selectedPackages;
        PinHasher.Record pinRecord = PinHasher.create(pin);
        return preferences.edit()
                .putBoolean(KEY_CONFIGURED, true)
                .putBoolean(KEY_ENFORCEMENT_ENABLED, true)
                .putString(KEY_PIN_SALT, pinRecord.getSaltHex())
                .putString(KEY_PIN_HASH, pinRecord.getHashHex())
                .putInt(KEY_PIN_ITERATIONS, pinRecord.getIterations())
                .putLong(KEY_DAILY_LIMIT, dailyLimitMillis)
                .putString(KEY_SCOPE, scope)
                .putStringSet(KEY_SELECTED_PACKAGES, new HashSet<>(safeSelectedPackages))
                .putBoolean(KEY_USB_RECOVERY, false)
                .commit();
    }

    public boolean updateSettings(
            long dailyLimitMillis,
            String scope,
            Set<String> selectedPackages,
            boolean enforcementEnabled
    ) {
        validateSettings(dailyLimitMillis, scope, selectedPackages);
        Set<String> safeSelectedPackages = selectedPackages == null
                ? Collections.emptySet()
                : selectedPackages;
        return preferences.edit()
                .putLong(KEY_DAILY_LIMIT, dailyLimitMillis)
                .putString(KEY_SCOPE, scope)
                .putStringSet(KEY_SELECTED_PACKAGES, new HashSet<>(safeSelectedPackages))
                .putBoolean(KEY_ENFORCEMENT_ENABLED, enforcementEnabled)
                .commit();
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
                preferences.getLong(KEY_BONUS_MILLIS, 0L)
        );
    }

    public synchronized boolean addUsage(String dayKey, long deltaMillis) {
        ensureDay(dayKey);
        long current = preferences.getLong(KEY_USAGE_MILLIS, 0L);
        long safeDelta = Math.max(0L, deltaMillis);
        long updated = current > Long.MAX_VALUE - safeDelta ? Long.MAX_VALUE : current + safeDelta;
        preferences.edit().putLong(KEY_USAGE_MILLIS, updated).apply();
        return true;
    }

    public synchronized boolean addBonus(String dayKey, long bonusMillis) {
        ensureDay(dayKey);
        long current = preferences.getLong(KEY_BONUS_MILLIS, 0L);
        long safeBonus = Math.max(0L, bonusMillis);
        long updated = current > Long.MAX_VALUE - safeBonus ? Long.MAX_VALUE : current + safeBonus;
        preferences.edit().putLong(KEY_BONUS_MILLIS, updated).apply();
        return true;
    }

    public boolean setEnforcementEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENFORCEMENT_ENABLED, enabled).apply();
        return true;
    }

    public boolean resetForUsbRecovery() {
        return preferences.edit()
                .clear()
                .putBoolean(KEY_USB_RECOVERY, true)
                .commit();
    }

    public boolean hasUsbRecoveryNotice() {
        return preferences.getBoolean(KEY_USB_RECOVERY, false);
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
                || KEY_SELECTED_PACKAGES.equals(key);
    }

    private void ensureDay(String dayKey) {
        String storedDay = preferences.getString(KEY_USAGE_DAY, null);
        if (!dayKey.equals(storedDay)) {
            preferences.edit()
                    .putString(KEY_USAGE_DAY, dayKey)
                    .putLong(KEY_USAGE_MILLIS, 0L)
                    .putLong(KEY_BONUS_MILLIS, 0L)
                    .apply();
        }
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

        DayState(long usedMillis, long bonusMillis) {
            this.usedMillis = usedMillis;
            this.bonusMillis = bonusMillis;
        }

        public long getUsedMillis() {
            return usedMillis;
        }

        public long getBonusMillis() {
            return bonusMillis;
        }
    }
}
