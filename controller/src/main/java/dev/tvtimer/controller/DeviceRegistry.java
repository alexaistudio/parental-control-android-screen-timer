package dev.tvtimer.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DeviceRegistry {
    private final Map<String, DeviceEndpoint> byHost = new LinkedHashMap<>();

    synchronized DeviceEndpoint upsert(DeviceEndpoint endpoint) {
        DeviceEndpoint existing = byHost.get(endpoint.host);
        DeviceEndpoint merged = existing == null ? endpoint : existing.merge(endpoint);
        byHost.put(endpoint.host, merged);
        return merged;
    }

    synchronized List<DeviceEndpoint> snapshot() {
        return new ArrayList<>(byHost.values());
    }

    synchronized void clear() {
        byHost.clear();
    }
}
