package dev.tvtimer.controller;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

final class AdbDiscovery {
    interface Listener {
        void onEndpoint(DeviceEndpoint endpoint);

        void onFinished();
    }

    private static final String ADB_TCP = "_adb._tcp";
    private static final String ADB_TLS_CONNECT = "_adb-tls-connect._tcp";
    private static final String ADB_TLS_PAIRING = "_adb-tls-pairing._tcp";

    private final NsdManager nsdManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final List<DiscoveryListener> discoveryListeners = new ArrayList<>();
    private final Queue<PendingResolution> pendingResolutions = new ArrayDeque<>();
    private final AtomicBoolean resolving = new AtomicBoolean(false);
    private boolean stopped;

    AdbDiscovery(Context context, Listener listener) {
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.listener = listener;
    }

    void start(long durationMs) {
        stopped = false;
        discover(ADB_TCP, false, true);
        discover(ADB_TLS_CONNECT, false, true);
        discover(ADB_TLS_PAIRING, true, false);
        handler.postDelayed(this::stop, durationMs);
    }

    void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        for (DiscoveryListener discovery : discoveryListeners) {
            if (!discovery.started) {
                continue;
            }
            try {
                nsdManager.stopServiceDiscovery(discovery);
            } catch (RuntimeException ignored) {
                // OEM NSD implementations sometimes unregister a listener before notifying us.
            }
        }
        discoveryListeners.clear();
        synchronized (pendingResolutions) {
            pendingResolutions.clear();
        }
        listener.onFinished();
    }

    private void discover(String type, boolean pairing, boolean connection) {
        DiscoveryListener discovery = new DiscoveryListener(type, pairing, connection);
        discoveryListeners.add(discovery);
        try {
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (RuntimeException ignored) {
            // The manual address and legacy scan remain available.
        }
    }

    private void enqueue(NsdServiceInfo info, boolean pairing, boolean connection) {
        synchronized (pendingResolutions) {
            pendingResolutions.add(new PendingResolution(info, pairing, connection));
        }
        resolveNext();
    }

    private void resolveNext() {
        if (stopped || !resolving.compareAndSet(false, true)) {
            return;
        }
        PendingResolution pending;
        synchronized (pendingResolutions) {
            pending = pendingResolutions.poll();
        }
        if (pending == null) {
            resolving.set(false);
            return;
        }
        try {
            nsdManager.resolveService(pending.info, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    resolving.set(false);
                    resolveNext();
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                    if (!stopped && serviceInfo.getHost() != null && serviceInfo.getPort() > 0) {
                        String host = serviceInfo.getHost().getHostAddress();
                        int pairingPort = pending.pairing ? serviceInfo.getPort() : -1;
                        int connectionPort = pending.connection ? serviceInfo.getPort() : -1;
                        listener.onEndpoint(new DeviceEndpoint(
                                host,
                                cleanName(serviceInfo.getServiceName(), host),
                                pairingPort,
                                connectionPort));
                    }
                    resolving.set(false);
                    resolveNext();
                }
            });
        } catch (RuntimeException ignored) {
            resolving.set(false);
            resolveNext();
        }
    }

    private static String cleanName(String serviceName, String fallback) {
        if (serviceName == null || serviceName.isBlank()) {
            return fallback;
        }
        return serviceName.replace("adb-", "Android ");
    }

    private final class DiscoveryListener implements NsdManager.DiscoveryListener {
        final String type;
        final boolean pairing;
        final boolean connection;
        boolean started;

        DiscoveryListener(String type, boolean pairing, boolean connection) {
            this.type = type;
            this.pairing = pairing;
            this.connection = connection;
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            started = true;
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            started = false;
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            started = false;
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            started = false;
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            enqueue(serviceInfo, pairing, connection);
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            // A short pairing advertisement disappearing is normal when its dialog closes.
        }
    }

    private static final class PendingResolution {
        final NsdServiceInfo info;
        final boolean pairing;
        final boolean connection;

        PendingResolution(NsdServiceInfo info, boolean pairing, boolean connection) {
            this.info = info;
            this.pairing = pairing;
            this.connection = connection;
        }
    }
}
