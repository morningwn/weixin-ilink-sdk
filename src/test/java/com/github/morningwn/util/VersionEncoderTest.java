package com.github.morningwn.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersionEncoderTest {

    @Test
    void encodeShouldMatchSpecExample() {
        assertEquals("131334", VersionEncoder.encode("2.1.6"));
    }

    @Test
    void encodeShouldRejectInvalidFormat() {
        assertThrows(RuntimeException.class, () -> VersionEncoder.encode("2.1"));
    }
}
