package io.github.morningwn.api;

import io.github.morningwn.protocol.enums.TypingStatus;
import io.github.morningwn.protocol.message.MessageItem;
import io.github.morningwn.protocol.response.SendMessageResponse;
import io.github.morningwn.protocol.response.SendTypingResponse;

import java.util.List;

/**
 * Sends outbound messages without exposing a concrete bot implementation.
 *
 * <p>Message handlers receive this port for the duration of a dispatch. They
 * should not retain it after handling has completed.</p>
 */
public interface MessageSender {

    /**
     * Sends text and automatically splits oversized content into multiple chunks.
     *
     * @param toUserId target user id
     * @param contextToken conversation context token
     * @param text text content
     * @return send responses in sending order
     */
    List<SendMessageResponse> sendText(String toUserId, String contextToken, String text);

    /**
     * Sends one prepared, type-safe message item.
     *
     * @param toUserId target user id
     * @param contextToken conversation context token
     * @param item message item to send
     * @return send response
     */
    SendMessageResponse send(String toUserId, String contextToken, MessageItem item);

    /**
     * Sends typing status to one target user.
     *
     * @param toUserId target user id
     * @param contextToken conversation context token
     * @param status typing status
     * @return sendtyping response
     */
    SendTypingResponse sendTyping(String toUserId, String contextToken, TypingStatus status);
}
