package io.github.morningwn.handler;

import io.github.morningwn.api.MessageSender;
import io.github.morningwn.protocol.message.InboundMessage;

/**
 * Inbound message handler contract.
 *
 * <p>The sender is supplied by the dispatcher so handlers do not need to
 * depend on or retain a concrete client.</p>
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * Handles one inbound message.
     *
     * @param message inbound message
     * @param sender outbound message port
     */
    void handle(InboundMessage message, MessageSender sender);
}
