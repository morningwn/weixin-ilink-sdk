package io.github.morningwn.exception;

/**
 * Indicates that the QR-login verification code can no longer be used.
 */
public class VerificationCodeBlockedException extends ILinkException {

    /**
     * Creates an exception for a blocked verification-code login flow.
     */
    public VerificationCodeBlockedException() {
        super("QR-login verification code is blocked; request a new QR code before retrying");
    }
}
