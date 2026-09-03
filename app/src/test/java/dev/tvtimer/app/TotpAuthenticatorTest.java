package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class TotpAuthenticatorTest {
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    public void generatesRfcCompatibleSixDigitCode() {
        assertTrue(TotpAuthenticator.verify("287082", RFC_SECRET, 59_000L));
        assertFalse(TotpAuthenticator.verify("287083", RFC_SECRET, 59_000L));
    }

    @Test
    public void acceptsEachCodeForAtMostFiveMinutes() {
        long sourceCounter = 100L;
        String code = TotpAuthenticator.codeForCounter(RFC_SECRET, sourceCounter);

        assertTrue(TotpAuthenticator.verify(code, RFC_SECRET, (109L * 30L + 29L) * 1_000L));
        assertFalse(TotpAuthenticator.verify(code, RFC_SECRET, 110L * 30L * 1_000L));
    }

    @Test
    public void rejectsMalformedCodesAndSecrets() {
        assertFalse(TotpAuthenticator.verify("12345", RFC_SECRET, 1_000L));
        assertFalse(TotpAuthenticator.verify("12A456", RFC_SECRET, 1_000L));
        assertFalse(TotpAuthenticator.verify("123456", "BAD!", 1_000L));
    }

    @Test
    public void createsUniqueStandardProvisioningSecrets() {
        String first = TotpAuthenticator.generateSecret();
        String second = TotpAuthenticator.generateSecret();

        assertTrue(TotpAuthenticator.isValidSecret(first));
        assertTrue(TotpAuthenticator.provisioningUri(first).startsWith("otpauth://totp/"));
        assertNotEquals(first, second);
    }

    @Test
    public void sameSecretAlwaysCreatesSameQrPayload() {
        assertEquals(
                "otpauth://totp/Android%20Screen%20Timer%3AFamily%20device?secret=" + RFC_SECRET
                        + "&issuer=Android%20Screen%20Timer&algorithm=SHA1&digits=6&period=30",
                TotpAuthenticator.provisioningUri(RFC_SECRET)
        );
    }
}
