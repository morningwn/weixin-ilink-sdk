package io.github.morningwn.util;

import io.github.morningwn.exception.ILinkException;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

/**
 * Hex encoding and decoding helpers.
 */
public final class HexUtils {

    private HexUtils() {
    }

    /**
     * Encodes bytes into lowercase hex.
     *
     * @param bytes source bytes
     * @return lowercase hex string
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            throw new ILinkException("bytes cannot be null");
        }
        return Hex.encodeHexString(bytes);
    }

    /**
     * Decodes hex string into bytes.
     *
     * @param hex hex string
     * @return decoded bytes
     */
    public static byte[] fromHex(String hex) {
        if (StringUtils.isBlank(hex)) {
            throw new ILinkException("Hex string cannot be null or blank");
        }
        String normalized = StringUtils.trim(hex);
        try {
            return Hex.decodeHex(normalized);
        } catch (DecoderException e) {
            throw new ILinkException("Hex string is invalid", e);
        }
    }
}
