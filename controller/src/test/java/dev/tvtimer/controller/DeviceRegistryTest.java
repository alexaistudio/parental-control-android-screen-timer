package dev.tvtimer.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceRegistryTest {
    @Test
    public void combinesPairAndConnectAnnouncementsForSameHost() {
        DeviceRegistry registry = new DeviceRegistry();
        registry.upsert(new DeviceEndpoint("192.168.1.40", "Living room TV", 37123, -1));
        DeviceEndpoint result = registry.upsert(
                new DeviceEndpoint("192.168.1.40", "Living room TV", -1, 41231));

        assertEquals(37123, result.pairingPort);
        assertEquals(41231, result.connectionPort);
        assertTrue(result.canPair());
        assertTrue(result.canConnect());
        assertEquals(1, registry.snapshot().size());
    }
}
