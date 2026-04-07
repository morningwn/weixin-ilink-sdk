package com.github.morningwn.util;

import java.util.UUID;

/**
 * Client id generator for sendmessage requests.
 */
public final class ClientIdGenerator {

    private ClientIdGenerator() {
    }

    /**
     * Generates an id in format prefix:epochMillis-random8.
     *
     * @param prefix id prefix
     * @return generated client id
     */
    public static String generate(String prefix) {
        String safePrefix = (prefix == null || prefix.isBlank()) ? "weixin-ilink" : prefix;
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return safePrefix + ":" + System.currentTimeMillis() + "-" + random;
    }
}
