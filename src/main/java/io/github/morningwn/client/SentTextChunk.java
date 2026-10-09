package io.github.morningwn.client;

import io.github.morningwn.protocol.response.SendMessageResponse;

/**
 * A successfully sent text chunk.
 *
 * @param index zero-based chunk index
 * @param clientId id used for the sendmessage request
 * @param response sendmessage response
 */
public record SentTextChunk(int index, String clientId, SendMessageResponse response) {
}
