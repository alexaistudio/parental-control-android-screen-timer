package dev.tvtimer.controller;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class LegacyAdbScanner {
    interface Listener {
        void onFound(DeviceEndpoint endpoint);
    }

    private LegacyAdbScanner() {
    }

    static void scan(Listener listener) {
        String local = findLocalIpv4();
        if (local == null) {
            return;
        }
        int lastDot = local.lastIndexOf('.');
        if (lastDot < 0) {
            return;
        }
        String prefix = local.substring(0, lastDot + 1);
        ExecutorService workers = Executors.newFixedThreadPool(32);
        for (int suffix = 1; suffix <= 254; suffix++) {
            String host = prefix + suffix;
            if (host.equals(local)) {
                continue;
            }
            workers.execute(() -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, 5555), 180);
                    listener.onFound(new DeviceEndpoint(host, host, -1, 5555));
                } catch (Exception ignored) {
                    // Closed ports are the expected result for almost every address.
                }
            });
        }
        workers.shutdown();
        try {
            workers.awaitTermination(8, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            workers.shutdownNow();
        }
    }

    private static String findLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // The UI keeps the manual IP path available.
        }
        return null;
    }
}
