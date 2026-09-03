package dev.tvtimer.app;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class TotpAuthenticator {
    static final int CODE_DIGITS = 6;
    static final long PERIOD_SECONDS = 30L;
    static final int ACCEPTED_PERIODS = 10;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;

    private TotpAuthenticator() {
    }

    static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return encodeBase32(bytes);
    }

    static boolean isValidSecret(String secret) {
        if (secret == null || secret.length() < 16) {
            return false;
        }
        try {
            return decodeBase32(secret).length >= 10;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String provisioningUri(String secret) {
        if (!isValidSecret(secret)) {
            throw new IllegalArgumentException("Invalid authenticator secret");
        }
        String issuer = encodeUriComponent("Android Screen Timer");
        String account = encodeUriComponent("Android Screen Timer:Family device");
        return "otpauth://totp/" + account
                + "?secret=" + secret
                + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=" + CODE_DIGITS
                + "&period=" + PERIOD_SECONDS;
    }

    static boolean verify(String code, String secret, long nowMillis) {
        if (!isValidCode(code) || !isValidSecret(secret) || nowMillis < 0L) {
            return false;
        }
        long currentCounter = nowMillis / 1_000L / PERIOD_SECONDS;
        boolean matched = false;
        for (int offset = 0; offset < ACCEPTED_PERIODS; offset++) {
            long counter = currentCounter - offset;
            if (counter >= 0L) {
                matched |= constantTimeEquals(code, codeForCounter(secret, counter));
            }
        }
        return matched;
    }

    static String codeForCounter(String secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int binary = ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);
            int value = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP is unavailable", exception);
        }
    }

    private static boolean isValidCode(String code) {
        if (code == null || code.length() != CODE_DIGITS) {
            return false;
        }
        for (int index = 0; index < code.length(); index++) {
            if (!Character.isDigit(code.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int difference = 0;
        for (int index = 0; index < left.length(); index++) {
            difference |= left.charAt(index) ^ right.charAt(index);
        }
        return difference == 0;
    }

    private static String encodeBase32(byte[] input) {
        StringBuilder encoded = new StringBuilder((input.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                encoded.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            encoded.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return encoded.toString();
    }

    private static byte[] decodeBase32(String encoded) {
        String normalized = encoded.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        byte[] output = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int outputIndex = 0;
        for (int index = 0; index < normalized.length(); index++) {
            int value = BASE32.indexOf(normalized.charAt(index));
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 secret");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                output[outputIndex++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return outputIndex == output.length ? output : Arrays.copyOf(output, outputIndex);
    }

    private static String encodeUriComponent(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException("UTF-8 is unavailable", exception);
        }
    }
}
