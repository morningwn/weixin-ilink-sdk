package io.github.morningwn.handler;

import io.github.morningwn.protocol.ILinkAuthSession;
import io.github.morningwn.protocol.message.InboundMessage;
import io.github.morningwn.protocol.response.QrCodeResponse;

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
     * Loads locally persisted bot tokens in most-recent-first order.
     *
     * <p>The SDK sends at most the first ten values when it must request a QR code.
     * Return only tokens stored by this client; never return tokens collected from
     * another client or account store.</p>
     *
     * @return recent local bot tokens, or an empty list when none exist
     */
    default List<String> loadRecentBotTokens() {
        return List.of();
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
     * Notifies the application that QR login requires a verification code.
     *
     * <p>The SDK calls this at most once for each QR code. Applications can use
     * this callback to prompt the user and make the submitted code available from
     * {@link #loadVerificationCode(String)}.</p>
     *
     * @param qrCodeResponse current QR code payload
     */
    default void onVerificationCodeRequired(QrCodeResponse qrCodeResponse) {
    }

    /**
     * Loads a one-time verification code submitted for a QR login.
     *
     * <p>Return {@code null} until the user has submitted a code. Implementations
     * should consume the returned code and must not log or persist it.</p>
     *
     * @param qrcode QR polling token associated with the verification challenge
     * @return a verification code, or {@code null} when one is not available
     */
    default String loadVerificationCode(String qrcode) {
        return null;
    }

    /**
     * Confirms whether the suggested getupdates cursor can be committed.
     *
     * <p>Called after a batch response is handled and before bot updates internal cursor.
     * Applications can return current cursor to postpone commit when external durability
     * (for example database flush) is not finished.</p>
     *
     * @param currentGetUpdatesBuf   currently committed cursor
     * @param suggestedGetUpdatesBuf cursor suggested by latest getupdates response
     * @param receivedMessages       messages returned in latest batch
     * @param fullyProcessed         whether this batch has been fully processed by message handler
     * @return confirmed cursor to commit; return current cursor to skip commit
     */
    default String confirmGetUpdatesBuf(
            String currentGetUpdatesBuf,
            String suggestedGetUpdatesBuf,
            List<InboundMessage> receivedMessages,
            boolean fullyProcessed
    ) {
        return fullyProcessed ? suggestedGetUpdatesBuf : currentGetUpdatesBuf;
    }
}
