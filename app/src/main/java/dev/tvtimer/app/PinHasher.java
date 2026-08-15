package dev.tvtimer.app;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PinHasher {
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 150_000;
    private static final int KEY_LENGTH_BITS = 256;

    private PinHasher() {
    }

    public static boolean isValidFormat(String pin) {
        return pin != null && pin.matches("[0-9]{4,8}");
    }

    public static Record create(String pin) {
        if (!isValidFormat(pin)) {
            throw new IllegalArgumentException("PIN must contain 4 to 8 digits");
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return new Record(toHex(salt), derive(pin, salt));
    }

    public static boolean verify(String pin, String saltHex, String expectedHashHex) {
        if (!isValidFormat(pin) || saltHex == null || expectedHashHex == null) {
            return false;
        }
        try {
            byte[] actual = fromHex(derive(pin, fromHex(saltHex)));
            byte[] expected = fromHex(expectedHashHex);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String deriveForTest(String pin, String saltHex) {
        return derive(pin, fromHex(saltHex));
    }

    private static String derive(String pin, byte[] salt) {
        PBEKeySpec keySpec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return toHex(factory.generateSecret(keySpec).getEncoded());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PIN hashing is unavailable", exception);
        } finally {
            keySpec.clearPassword();
        }
    }

    private static String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(Character.forDigit((item >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(item & 0x0f, 16));
        }
        return result.toString();
    }

    private static byte[] fromHex(String value) {
        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex value");
        }
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < value.length(); index += 2) {
            int high = Character.digit(value.charAt(index), 16);
            int low = Character.digit(value.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex value");
            }
            result[index / 2] = (byte) ((high << 4) | low);
        }
        return result;
    }

    public static final class Record {
        private final String saltHex;
        private final String hashHex;

        Record(String saltHex, String hashHex) {
            this.saltHex = saltHex;
            this.hashHex = hashHex;
        }

        public String getSaltHex() {
            return saltHex;
        }

        public String getHashHex() {
            return hashHex;
        }
    }
}
