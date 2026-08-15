package dev.tvtimer.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class PinHasherTest {
    @Test
    public void acceptsOnlyFourToEightDigits() {
        assertTrue(PinHasher.isValidFormat("1234"));
        assertTrue(PinHasher.isValidFormat("12345678"));
        assertFalse(PinHasher.isValidFormat("123"));
        assertFalse(PinHasher.isValidFormat("123456789"));
        assertFalse(PinHasher.isValidFormat("12a4"));
        assertFalse(PinHasher.isValidFormat(null));
    }

    @Test
    public void createdRecordVerifiesOnlyTheOriginalPin() {
        PinHasher.Record record = PinHasher.create("4826");

        assertTrue(PinHasher.verify("4826", record.getSaltHex(), record.getHashHex()));
        assertFalse(PinHasher.verify("4827", record.getSaltHex(), record.getHashHex()));
        assertFalse(PinHasher.verify("4826", "not-hex", record.getHashHex()));
    }

    @Test
    public void derivationIsDeterministicAndSalted() {
        String first = PinHasher.deriveForTest("1234", "00112233445566778899aabbccddeeff");
        String same = PinHasher.deriveForTest("1234", "00112233445566778899aabbccddeeff");
        String otherSalt = PinHasher.deriveForTest("1234", "ffeeddccbbaa99887766554433221100");

        assertEquals(first, same);
        assertNotEquals(first, otherSalt);
    }
}
