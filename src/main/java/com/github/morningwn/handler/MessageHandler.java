package com.github.morningwn.handler;

import com.github.morningwn.protocol.WeixinMessage;

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
