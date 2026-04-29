package io.github.morningwn.util;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generator for X-WECHAT-UIN header value.
 */
public final class WechatUinGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private WechatUinGenerator() {
    }

    /**
     * Generates one X-WECHAT-UIN value.
     *
     * @return base64(decimal(uint32 random))
     */
    public static String randomWechatUin() {
        int value = SECURE_RANDOM.nextInt();
        String decimal = Long.toUnsignedString(Integer.toUnsignedLong(value));
        return Base64.getEncoder().encodeToString(decimal.getBytes(StandardCharsets.UTF_8));
    }
}
