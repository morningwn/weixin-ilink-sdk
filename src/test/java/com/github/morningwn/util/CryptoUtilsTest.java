package com.github.morningwn.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptoUtilsTest {

    @Test
    void encryptAndDecryptShouldRoundTrip() {
        byte[] key = HexUtils.fromHex("00112233445566778899aabbccddeeff");
        byte[] plain = "hello-ilink".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = CryptoUtils.encryptAesEcb(plain, key);
        byte[] decrypted = CryptoUtils.decryptAesEcb(encrypted, key);
        assertArrayEquals(plain, decrypted);
    }

    @Test
    void decodeCompatibleAesKeyShouldSupportFormatAAndB() {
        String hex = "00112233445566778899aabbccddeeff";
        byte[] key = HexUtils.fromHex(hex);

        String formatA = Base64.getEncoder().encodeToString(key);
        String formatB = Base64.getEncoder().encodeToString(hex.getBytes(StandardCharsets.US_ASCII));

        assertArrayEquals(key, CryptoUtils.decodeCompatibleAesKey(formatA));
        assertArrayEquals(key, CryptoUtils.decodeCompatibleAesKey(formatB));
    }

    @Test
    void encryptedSizeShouldMatchSpecFormula() {
        assertEquals(16, CryptoUtils.encryptedSize(0));
        assertEquals(16, CryptoUtils.encryptedSize(15));
        assertEquals(32, CryptoUtils.encryptedSize(16));
    }
}
