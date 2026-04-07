package com.github.morningwn.util;

import com.github.morningwn.exception.ILinkException;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cryptography helpers used by iLink CDN flow.
 */
public final class CryptoUtils {

    private CryptoUtils() {
    }

    /**
     * Encrypts plaintext with AES-128-ECB and PKCS7-compatible padding.
     *
     * @param plaintext source bytes
     * @param key 16-byte AES key
     * @return encrypted bytes
     */
    public static byte[] encryptAesEcb(byte[] plaintext, byte[] key) {
        validateAes128Key(key);
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new ILinkException("Failed to encrypt payload by AES-128-ECB", e);
        }
    }

    /**
     * Decrypts ciphertext with AES-128-ECB and PKCS7-compatible padding.
     *
     * @param ciphertext encrypted bytes
     * @param key 16-byte AES key
     * @return decrypted bytes
     */
    public static byte[] decryptAesEcb(byte[] ciphertext, byte[] key) {
        validateAes128Key(key);
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new ILinkException("Failed to decrypt payload by AES-128-ECB", e);
        }
    }

    /**
     * Decodes protocol aes_key compatible with both documented formats.
     *
     * @param encodedAesKey base64 payload from media field
     * @return decoded 16-byte AES key
     */
    public static byte[] decodeCompatibleAesKey(String encodedAesKey) {
        if (StringUtils.isBlank(encodedAesKey)) {
            throw new ILinkException("aes_key cannot be null or blank");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedAesKey);
        } catch (IllegalArgumentException e) {
            throw new ILinkException("Invalid base64 aes_key", e);
        }

        if (decoded.length == 16) {
            return decoded;
        }
        if (decoded.length == 32) {
            try {
                return HexUtils.fromHex(new String(decoded, StandardCharsets.US_ASCII));
            } catch (ILinkException e) {
                throw new ILinkException("32-byte aes_key payload is not valid hex ascii", e);
            }
        }

        throw new ILinkException("Unsupported aes_key decoded length: " + decoded.length);
    }

    /**
     * Calculates encrypted size for AES-128-ECB/PKCS7.
     *
     * @param plaintextSize plaintext bytes count
     * @return encrypted bytes count
     */
    public static long encryptedSize(long plaintextSize) {
        if (plaintextSize < 0) {
            throw new ILinkException("plaintextSize cannot be negative");
        }
        return ((plaintextSize + 1 + 15) / 16) * 16;
    }

    private static void validateAes128Key(byte[] key) {
        if (key == null || key.length != 16) {
            throw new ILinkException("AES-128 key must be exactly 16 bytes");
        }
    }
}
