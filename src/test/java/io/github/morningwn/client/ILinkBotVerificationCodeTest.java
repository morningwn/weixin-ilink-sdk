package io.github.morningwn.client;

import io.github.morningwn.exception.VerificationCodeBlockedException;
import io.github.morningwn.handler.SessionHandler;
import io.github.morningwn.protocol.ILinkAuthSession;
import io.github.morningwn.protocol.enums.BusinessCode;
import io.github.morningwn.protocol.enums.QrCodeStatus;
import io.github.morningwn.protocol.response.QrCodeResponse;
import io.github.morningwn.protocol.response.QrCodeStatusResponse;
import io.github.morningwn.protocol.response.SendMessageResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ILinkBotVerificationCodeTest {

    @Test
    void qrLoginShouldSubmitOneTimeVerificationCode() {
        ILinkClientConfig config = baseConfig();
        VerificationClient client = new VerificationClient(config, List.of(
                QrCodeStatus.NEED_VERIFYCODE,
                QrCodeStatus.NEED_VERIFYCODE,
                QrCodeStatus.CONFIRMED
        ));
        VerificationSessionHandler sessionHandler = new VerificationSessionHandler("123 456");

        try (ILinkBot bot = new ILinkBot(client, config, sessionHandler)) {
            bot.sendText("target", "context", "message");
        }

        assertEquals(1, sessionHandler.verificationRequiredCalls);
        assertEquals(2, sessionHandler.loadVerificationCodeCalls);
        assertEquals(Arrays.asList(null, "123 456", "123 456"), client.verificationCodes);
        assertEquals("confirmed-token", sessionHandler.persistedSession.token());
    }

    @Test
    void qrLoginShouldStopWhenVerificationCodeIsBlocked() {
        ILinkClientConfig config = baseConfig();
        VerificationClient client = new VerificationClient(config, List.of(QrCodeStatus.VERIFY_CODE_BLOCKED));

        try (ILinkBot bot = new ILinkBot(client, config, new VerificationSessionHandler(null))) {
            assertThrows(VerificationCodeBlockedException.class,
                    () -> bot.sendText("target", "context", "message"));
        }
    }

    private static ILinkClientConfig baseConfig() {
        return ILinkClientConfig.builder()
                .baseUrl("https://example.com")
                .longPollingTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static final class VerificationSessionHandler implements SessionHandler {

        private final String verificationCode;
        private int verificationRequiredCalls;
        private int loadVerificationCodeCalls;
        private ILinkAuthSession persistedSession;

        private VerificationSessionHandler(String verificationCode) {
            this.verificationCode = verificationCode;
        }

        @Override
        public void onVerificationCodeRequired(QrCodeResponse qrCodeResponse) {
            verificationRequiredCalls++;
        }

        @Override
        public String loadVerificationCode(String qrcode) {
            loadVerificationCodeCalls++;
            return verificationCode;
        }

        @Override
        public void persistSession(ILinkAuthSession session) {
            persistedSession = session;
        }
    }

    private static final class VerificationClient extends ILinkClient {

        private final List<QrCodeStatus> statuses;
        private final List<String> verificationCodes = new ArrayList<>();
        private int statusIndex;

        private VerificationClient(ILinkClientConfig config, List<QrCodeStatus> statuses) {
            super(config);
            this.statuses = List.copyOf(statuses);
        }

        @Override
        public QrCodeResponse getBotQrCode(List<String> localTokenList) {
            return new QrCodeResponse("qr-token", "https://example.com/qr");
        }

        @Override
        public QrCodeStatusResponse getQrCodeStatus(String qrcode, String baseUrl, String verifyCode) {
            verificationCodes.add(verifyCode);
            QrCodeStatus status = statuses.get(statusIndex++);
            if (status == QrCodeStatus.CONFIRMED) {
                return new QrCodeStatusResponse(
                        status,
                        null,
                        "confirmed-token",
                        "bot-id",
                        "user-id",
                        "https://example.com"
                );
            }
            return new QrCodeStatusResponse(status, null, null, null, null, null);
        }

        @Override
        public List<SendMessageResponse> sendText(
                ILinkAuthSession session,
                String toUserId,
                String contextToken,
                String text,
                String clientIdPrefix
        ) {
            return List.of(new SendMessageResponse(BusinessCode.OK.code(), BusinessCode.OK.code(), null));
        }
    }
}
