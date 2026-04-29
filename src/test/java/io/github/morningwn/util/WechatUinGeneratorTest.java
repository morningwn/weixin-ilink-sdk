package io.github.morningwn.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatUinGeneratorTest {

    @Test
    void generatedUinShouldDecodeToUnsignedDecimal() {
        String value = WechatUinGenerator.randomWechatUin();
        String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        assertTrue(decoded.matches("^[0-9]{1,10}$"));
        long asLong = Long.parseLong(decoded);
        assertTrue(asLong >= 0 && asLong <= 4294967295L);
    }
}
