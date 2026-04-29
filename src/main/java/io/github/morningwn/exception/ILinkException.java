package io.github.morningwn.exception;

/**
 * Base runtime exception for iLink SDK.
 */
public class ILinkException extends RuntimeException {

    /**
     * Creates a new exception.
     *
     * @param message error message
     */
    public ILinkException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     *
     * @param message error message
     * @param cause root cause
     */
    public ILinkException(String message, Throwable cause) {
        super(message, cause);
    }
}
