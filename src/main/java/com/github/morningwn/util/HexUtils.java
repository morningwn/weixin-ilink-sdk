package com.github.morningwn.util;

import com.github.morningwn.exception.ILinkException;

/**
 * Hex encoding and decoding helpers.
 */
public final class HexUtils {

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private HexUtils() {
    }

    /**
     * Encodes bytes into lowercase hex.
     *
     * @param bytes source bytes
     * @return lowercase hex string
     */
    public static String toHex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX_ARRAY[v >>> 4];
            chars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * Decodes hex string into bytes.
     *
     * @param hex hex string
     * @return decoded bytes
     */
    public static byte[] fromHex(String hex) {
        if (hex == null || hex.isBlank()) {
            throw new ILinkException("Hex string cannot be null or blank");
        }
        String normalized = hex.trim();
        if ((normalized.length() & 1) == 1) {
            throw new ILinkException("Hex string length must be even");
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            int hi = Character.digit(normalized.charAt(i), 16);
            int lo = Character.digit(normalized.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new ILinkException("Hex string contains invalid characters");
            }
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}
