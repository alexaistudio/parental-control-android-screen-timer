package dev.tvtimer.controller;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ControllerResultMessagesTest {
    @Test
    public void installSuccessAlwaysContainsTargetAndPackageManagerResult() {
        String message = ControllerResultMessages.installed(
                "192.168.31.55", 37123, "dev.tvtimer.app", "Success");

        assertTrue(message.contains("SUCCESS: APP INSTALLED"));
        assertTrue(message.contains("target=192.168.31.55:37123"));
        assertTrue(message.contains("package=dev.tvtimer.app"));
        assertTrue(message.contains("packageManagerResponse=Success"));
    }

    @Test
    public void connectionFailureAlwaysContainsAttemptedTarget() {
        String message = ControllerResultMessages.failed(
                "CONNECT", "192.168.31.55", 37123, new IllegalStateException("refused"));

        assertTrue(message.contains("FAILURE: CONNECT"));
        assertTrue(message.contains("target=192.168.31.55:37123"));
        assertTrue(message.contains("IllegalStateException: refused"));
    }
}
