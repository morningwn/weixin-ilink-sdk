package io.github.morningwn.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    @Test
    void splitShouldKeepOrderAndBoundaries() {
        String text = "a".repeat(1998) + "\n\n" + "b".repeat(1200);
        List<String> chunks = TextChunker.split(text, 2000);
        assertEquals(2, chunks.size());
        assertEquals(2000, chunks.get(0).length());
        assertEquals(1200, chunks.get(1).length());
    }

    @Test
    void splitShouldNotExceedLimit() {
        String text = "x".repeat(5500);
        List<String> chunks = TextChunker.split(text, 2000);
        assertEquals(3, chunks.size());
        assertTrue(chunks.stream().allMatch(s -> s.length() <= 2000));
    }
}
