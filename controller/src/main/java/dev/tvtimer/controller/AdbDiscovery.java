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
    private volatile boolean stopped;

    AdbDiscovery(Context context, Listener listener) {
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.listener = listener;
    }

    void start(long durationMs) {
        stopped = false;
        ControllerLog.info("NSD", "Discovery started durationMs=" + durationMs
                + " types=" + ADB_TCP + "," + ADB_TLS_CONNECT + "," + ADB_TLS_PAIRING);
        discover(ADB_TCP, false, true);
        discover(ADB_TLS_CONNECT, false, true);
        discover(ADB_TLS_PAIRING, true, false);
        handler.postDelayed(this::stop, durationMs);
    }

    void stop() {
        if (stopped) {
            ControllerLog.info("NSD", "Stop ignored because discovery is already stopped");
            return;
        }
        ControllerLog.info("NSD", "Stopping discovery listeners=" + discoveryListeners.size());
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        for (DiscoveryListener discovery : discoveryListeners) {
            if (!discovery.started) {
                continue;
            }
            try {
                nsdManager.stopServiceDiscovery(discovery);
            } catch (RuntimeException exception) {
                ControllerLog.warning("NSD", "stopServiceDiscovery failed type="
                        + discovery.type, exception);
                // OEM NSD implementations sometimes unregister a listener before notifying us.
            }
        }
        discoveryListeners.clear();
        synchronized (pendingResolutions) {
            pendingResolutions.clear();
        }
        dispatchFinished();
        ControllerLog.info("NSD", "Discovery stopped");
    }

    private void discover(String type, boolean pairing, boolean connection) {
        DiscoveryListener discovery = new DiscoveryListener(type, pairing, connection);
        discoveryListeners.add(discovery);
        try {
            ControllerLog.info("NSD", "discoverServices request type=" + type);
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (RuntimeException exception) {
            ControllerLog.error("NSD", "discoverServices threw type=" + type, exception);
            // The manual address and legacy scan remain available.
        }
    }

    private void enqueue(NsdServiceInfo info, boolean pairing, boolean connection) {
        ControllerLog.info("NSD", "Service queued name=" + info.getServiceName()
                + " type=" + info.getServiceType() + " pairing=" + pairing
                + " connection=" + connection);
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
                    ControllerLog.warning("NSD", "resolveService failed name="
                            + serviceInfo.getServiceName() + " errorCode=" + errorCode, null);
                    resolving.set(false);
                    resolveNext();
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                    ControllerLog.info("NSD", "resolveService response name="
                            + serviceInfo.getServiceName() + " host=" + serviceInfo.getHost()
                            + " port=" + serviceInfo.getPort());
                    if (!stopped && serviceInfo.getHost() != null && serviceInfo.getPort() > 0) {
                        String host = serviceInfo.getHost().getHostAddress();
                        int pairingPort = pending.pairing ? serviceInfo.getPort() : -1;
                        int connectionPort = pending.connection ? serviceInfo.getPort() : -1;
                        dispatchEndpoint(new DeviceEndpoint(
                                host,
                                cleanName(serviceInfo.getServiceName(), host),
                                pairingPort,
                                connectionPort));
                    }
                    resolving.set(false);
                    resolveNext();
                }
            });
        } catch (RuntimeException exception) {
            ControllerLog.error("NSD", "resolveService threw name="
                    + pending.info.getServiceName(), exception);
            resolving.set(false);
            resolveNext();
        }
    }

    private void dispatchEndpoint(DeviceEndpoint endpoint) {
        handler.post(() -> {
            if (!stopped) {
                listener.onEndpoint(endpoint);
            }
        });
    }

    private void dispatchFinished() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onFinished();
        } else {
            handler.post(listener::onFinished);
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
            ControllerLog.info("NSD", "onDiscoveryStarted type=" + serviceType);
        }

        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            started = false;
            ControllerLog.warning("NSD", "onStartDiscoveryFailed type=" + serviceType
                    + " errorCode=" + errorCode, null);
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            started = false;
            ControllerLog.info("NSD", "onDiscoveryStopped type=" + serviceType);
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            started = false;
            ControllerLog.warning("NSD", "onStopDiscoveryFailed type=" + serviceType
                    + " errorCode=" + errorCode, null);
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            ControllerLog.info("NSD", "onServiceFound name=" + serviceInfo.getServiceName()
                    + " type=" + serviceInfo.getServiceType());
            enqueue(serviceInfo, pairing, connection);
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            ControllerLog.info("NSD", "onServiceLost name=" + serviceInfo.getServiceName()
                    + " type=" + serviceInfo.getServiceType());
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
