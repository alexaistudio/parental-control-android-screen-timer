package dev.tvtimer.controller;

import java.util.Objects;

final class DeviceEndpoint {
    static final int UNKNOWN_PORT = -1;

    final String host;
    final String name;
    final int pairingPort;
    final int connectionPort;

    DeviceEndpoint(String host, String name, int pairingPort, int connectionPort) {
        this.host = Objects.requireNonNull(host);
        this.name = name == null || name.isBlank() ? host : name;
        this.pairingPort = pairingPort;
        this.connectionPort = connectionPort;
    }

    DeviceEndpoint merge(DeviceEndpoint other) {
        if (!host.equals(other.host)) {
            throw new IllegalArgumentException("Cannot merge different hosts");
        }
        String mergedName = name.equals(host) ? other.name : name;
        int mergedPairing = other.pairingPort > 0 ? other.pairingPort : pairingPort;
        int mergedConnection = other.connectionPort > 0 ? other.connectionPort : connectionPort;
        return new DeviceEndpoint(host, mergedName, mergedPairing, mergedConnection);
    }

    boolean canPair() {
        return pairingPort > 0;
    }

    boolean canConnect() {
        return connectionPort > 0;
    }
}
