package io.github.morningwn.handler;

import io.github.morningwn.client.ILinkAuthSession;
import io.github.morningwn.protocol.QrCodeResponse;
import io.github.morningwn.protocol.WeixinMessage;

import java.util.List;

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

    /**
     * Confirms whether the suggested getupdates cursor can be committed.
     *
     * <p>Called after a batch response is handled and before bot updates internal cursor.
     * Applications can return current cursor to postpone commit when external durability
     * (for example database flush) is not finished.</p>
     *
     * @param currentGetUpdatesBuf currently committed cursor
     * @param suggestedGetUpdatesBuf cursor suggested by latest getupdates response
     * @param receivedMessages messages returned in latest batch
     * @param fullyProcessed whether this batch has been fully processed by message handler
     * @return confirmed cursor to commit; return current cursor to skip commit
     */
    default String confirmGetUpdatesBuf(
            String currentGetUpdatesBuf,
            String suggestedGetUpdatesBuf,
            List<WeixinMessage> receivedMessages,
            boolean fullyProcessed
    ) {
        return fullyProcessed ? suggestedGetUpdatesBuf : currentGetUpdatesBuf;
    }
}
