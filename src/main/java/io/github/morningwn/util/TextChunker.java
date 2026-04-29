package io.github.morningwn.util;

import io.github.morningwn.exception.ILinkException;

import java.util.ArrayList;
import java.util.List;

/**
 * Text splitter for protocol-safe long message sending.
 */
public final class TextChunker {

    /** Conservative compatibility limit from protocol practice. */
    public static final int DEFAULT_MAX_CHARS = 2000;

    private TextChunker() {
    }

    /**
     * Splits text by default 2000-char strategy.
     *
     * @param text input text
     * @return ordered chunks
     */
    public static List<String> split(String text) {
        return split(text, DEFAULT_MAX_CHARS);
    }

    /**
     * Splits text with boundary preference: double new line, new line, space, hard cut.
     *
     * @param text input text
     * @param maxChars max chars of each chunk
     * @return ordered chunks
     */
    public static List<String> split(String text, int maxChars) {
        if (text == null) {
            throw new ILinkException("text cannot be null");
        }
        if (maxChars <= 0) {
            throw new ILinkException("maxChars must be positive");
        }
        if (text.length() <= maxChars) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end == text.length()) {
                chunks.add(text.substring(start));
                break;
            }

            int split = preferredSplit(text, start, end);
            if (split <= start) {
                split = end;
            }
            chunks.add(text.substring(start, split));
            start = split;
        }
        return chunks;
    }

    private static int preferredSplit(String text, int start, int end) {
        int dnl = text.lastIndexOf("\n\n", end - 1);
        if (dnl >= start) {
            return dnl + 2;
        }

        int nl = text.lastIndexOf('\n', end - 1);
        if (nl >= start) {
            return nl + 1;
        }

        int space = text.lastIndexOf(' ', end - 1);
        if (space >= start) {
            return space + 1;
        }
        return end;
    }
}
