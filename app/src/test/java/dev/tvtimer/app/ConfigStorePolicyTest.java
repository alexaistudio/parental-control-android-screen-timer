package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ConfigStorePolicyTest {
    @Test
    public void runtimeRefreshIgnoresFrequentUsageAndPinWrites() {
        assertTrue(ConfigStore.affectsRuntimeConfiguration(null));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("enforcement_enabled"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("daily_limit_ms"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("scope"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("selected_packages"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("maintenance_until_ms"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("recovery_requested"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("usb_recovery_enabled"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("parent_mode_gesture_enabled"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("system_settings_protection_enabled"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("default_extension_minutes"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("authenticator_secret"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("usage_warning_interval_minutes"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("language"));
        assertTrue(ConfigStore.isLanguagePreference("language"));
        assertTrue(ConfigStore.isParentModeGesturePreference("parent_mode_gesture_enabled"));

        assertFalse(ConfigStore.affectsRuntimeConfiguration("usage_ms"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("bonus_ms"));
        assertTrue(ConfigStore.affectsRuntimeConfiguration("remote_notice_id"));
        assertFalse(ConfigStore.affectsRuntimeConfiguration("pin_hash"));
        assertFalse(ConfigStore.isLanguagePreference("daily_limit_ms"));
        assertFalse(ConfigStore.isParentModeGesturePreference("usb_recovery_enabled"));
    }
}
