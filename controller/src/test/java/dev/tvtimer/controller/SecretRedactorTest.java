package dev.tvtimer.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SecretRedactorTest {
    @Test
    public void removesPairingCodesWithoutRemovingPorts() {
        String redacted = SecretRedactor.redact(
                "host=192.168.1.4 port=37123 pairingCode=123456");

        assertTrue(redacted.contains("port=37123"));
        assertTrue(redacted.contains("pairingCode=<redacted>"));
        assertFalse(redacted.contains("123456"));
    }

    @Test
    public void removesPemPrivateKeyBlocks() {
        String begin = "-----BEGIN " + "PRIVATE KEY-----";
        String end = "-----END " + "PRIVATE KEY-----";
        String redacted = SecretRedactor.redact("before\n" + begin
                + "\nsecret\n" + end + "\nafter");

        assertEquals("before\n<private-key-redacted>\nafter", redacted);
    }
}
