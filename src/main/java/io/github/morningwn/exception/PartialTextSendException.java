package io.github.morningwn.exception;

import io.github.morningwn.client.SentTextChunk;

import java.util.List;

/**
 * Indicates that a multi-chunk text send failed after one or more chunks succeeded.
 */
public final class PartialTextSendException extends ILinkException {

    private final List<SentTextChunk> sentChunks;

    /**
     * Creates an exception that preserves chunks accepted before the failed request.
     *
     * @param message message describing the partial delivery
     * @param sentChunks successfully sent chunks
     * @param cause failure for the next chunk
     */
    public PartialTextSendException(String message, List<SentTextChunk> sentChunks, Throwable cause) {
        super(message, cause);
        this.sentChunks = List.copyOf(sentChunks);
    }

    /**
     * @return successfully sent chunks in send order
     */
    public List<SentTextChunk> getSentChunks() {
        return sentChunks;
    }
}
