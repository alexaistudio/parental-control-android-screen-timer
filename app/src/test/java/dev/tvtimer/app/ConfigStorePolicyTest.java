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

        assertFalse(ConfigStore.affectsRuntimeConfiguration("usage_ms"));
        assertFalse(ConfigStore.affectsRuntimeConfiguration("bonus_ms"));
        assertFalse(ConfigStore.affectsRuntimeConfiguration("pin_hash"));
    }
}
