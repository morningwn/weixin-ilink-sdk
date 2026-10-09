package io.github.morningwn.exception;

/**
 * Indicates that QR login found a bot already bound to this local client.
 */
public class AlreadyBoundException extends ILinkException {

    /**
     * Creates an exception for a binding that requires local session recovery.
     */
    public AlreadyBoundException() {
        super("Bot is already bound to this client; restore its local session before retrying login");
    }
}
