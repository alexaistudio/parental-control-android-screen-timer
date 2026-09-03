package dev.tvtimer.controller;

import java.util.LinkedHashSet;
import java.util.Set;

final class AccessibilityServices {
    private AccessibilityServices() {
    }

    static String add(String current, String service) {
        Set<String> services = new LinkedHashSet<>();
        if (current != null && !current.isBlank() && !"null".equals(current.trim())) {
            for (String item : current.trim().split(":")) {
                if (!item.isBlank()) {
                    services.add(item);
                }
            }
        }
        services.add(service);
        return String.join(":", services);
    }

    static boolean contains(String current, String service) {
        if (current == null || current.isBlank() || "null".equals(current.trim())) {
            return false;
        }
        for (String item : current.trim().split(":")) {
            if (service.equals(item)) {
                return true;
            }
        }
        return false;
    }
}
