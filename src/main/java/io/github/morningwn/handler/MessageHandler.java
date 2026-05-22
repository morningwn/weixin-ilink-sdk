package io.github.morningwn.handler;

import io.github.morningwn.protocol.message.WeixinMessage;

/**
 * Inbound message handler contract.
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * Handles one inbound message.
     *
     * @param message inbound message
     */
    void handle(WeixinMessage message);
}
