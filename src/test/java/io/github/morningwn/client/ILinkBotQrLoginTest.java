package io.github.morningwn.client;

import io.github.morningwn.handler.SessionHandler;
import io.github.morningwn.protocol.ILinkAuthSession;
import io.github.morningwn.protocol.enums.BusinessCode;
import io.github.morningwn.protocol.enums.QrCodeStatus;
import io.github.morningwn.protocol.response.QrCodeResponse;
import io.github.morningwn.protocol.response.QrCodeStatusResponse;
import io.github.morningwn.protocol.response.SendMessageResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ILinkBotQrLoginTest {

    @Test
    void qrLoginShouldForwardRecentLocalTokensFromSessionHandler() {
        ILinkClientConfig config = ILinkClientConfig.builder()
                .baseUrl("https://example.com")
                .longPollingTimeout(Duration.ofSeconds(5))
                .build();
        LoginClient client = new LoginClient(config);
        RecordingSessionHandler sessionHandler = new RecordingSessionHandler();

        try (ILinkBot bot = new ILinkBot(client, config, sessionHandler)) {
            bot.sendText("target", "context", "message");
        }

        assertEquals(List.of("latest-token", "previous-token"), client.localTokens);
        assertEquals("confirmed-token", sessionHandler.persistedSession.token());
        assertEquals(1, client.sendTextCalls);
    }

    private static final class RecordingSessionHandler implements SessionHandler {

        private ILinkAuthSession persistedSession;

        @Override
        public List<String> loadRecentBotTokens() {
            return List.of("latest-token", "previous-token");
        }

        @Override
        public void persistSession(ILinkAuthSession session) {
            persistedSession = session;
        }
    }

    private static final class LoginClient extends ILinkClient {

        private List<String> localTokens;
        private int sendTextCalls;

        private LoginClient(ILinkClientConfig config) {
            super(config);
        }

        @Override
        public QrCodeResponse getBotQrcode(List<String> localTokenList) {
            localTokens = List.copyOf(localTokenList);
            return new QrCodeResponse("qr-token", "https://example.com/qr");
        }

        @Override
        public QrCodeStatusResponse getQrcodeStatus(String qrcode, String baseUrl) {
            return new QrCodeStatusResponse(
                    QrCodeStatus.CONFIRMED,
                    null,
                    "confirmed-token",
                    "bot-id",
                    "user-id",
                    "https://example.com"
            );
        }

        @Override
        public List<SendMessageResponse> sendText(
                ILinkAuthSession session,
                String toUserId,
                String contextToken,
                String text,
                String clientIdPrefix
        ) {
            sendTextCalls++;
            return List.of(new SendMessageResponse(BusinessCode.OK.code(), BusinessCode.OK.code(), null));
        }
    }
}
