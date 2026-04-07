package com.github.morningwn.handler;

import com.github.morningwn.client.ILinkAuthSession;
import com.github.morningwn.protocol.QrCodeResponse;

/**
 * Optional session lifecycle callback.
 *
 * <p>Applications can implement this handler to persist sessions and display QR code
 * content when re-login is required. All methods are optional.</p>
 */
public interface SessionHandler {

    /**
     * Loads a persisted session.
     *
     * @return persisted session, or {@code null} if none
     */
    default ILinkAuthSession loadSession() {
        return null;
    }

    /**
     * Persists a new confirmed session.
     *
     * @param session confirmed session
     */
    default void persistSession(ILinkAuthSession session) {
    }

    /**
     * Clears a known expired session from persistence.
     *
     * @param expiredSession expired session
     */
    default void clearSession(ILinkAuthSession expiredSession) {
    }

    /**
     * Receives newly generated QR code content for user scan.
     *
     * @param qrCodeResponse qr code payload
     */
    default void onQrcode(QrCodeResponse qrCodeResponse) {
    }
}
