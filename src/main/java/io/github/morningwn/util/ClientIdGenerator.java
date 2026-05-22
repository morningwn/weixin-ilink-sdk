package io.github.morningwn.util;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;

/**
 * Client id generator for sendmessage requests.
 */
public final class ClientIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_BYTES = 4;

    private ClientIdGenerator() {
    }

    /**
     * Generates an id in format prefix:epochMillis-random8.
     *
     * @param prefix id prefix
     * @return generated client id
     */
    public static String generate(String prefix) {
        String safePrefix = StringUtils.defaultIfBlank(prefix, "weixin-ilink");
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return safePrefix + ":" + System.currentTimeMillis() + "-" + Hex.encodeHexString(bytes);
    }
}
