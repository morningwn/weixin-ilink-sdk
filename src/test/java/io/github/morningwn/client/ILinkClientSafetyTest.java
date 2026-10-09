package io.github.morningwn.client;

import com.sun.net.httpserver.HttpServer;
import io.github.morningwn.exception.ILinkException;
import io.github.morningwn.exception.ILinkProtocolException;
import io.github.morningwn.exception.PartialTextSendException;
import io.github.morningwn.protocol.CDNMedia;
import io.github.morningwn.protocol.ILinkAuthSession;
import io.github.morningwn.protocol.message.OutboundMessage;
import io.github.morningwn.protocol.response.SendMessageResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ILinkClientSafetyTest {

    @Test
    void getUpdatesRejectsResponseWithoutBusinessStatus() throws Exception {
        HttpServer server = startServer("/ilink/bot/getupdates", "{}");
        try {
            ILinkClient client = new ILinkClient(configFor(server));
            ILinkAuthSession session = new ILinkAuthSession(
                    "token",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "bot",
                    "user"
            );

            assertThrows(ILinkProtocolException.class, () -> client.getUpdates(session, ""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void downloadRejectsResponseLargerThanConfiguredLimit() throws Exception {
        HttpServer server = startServer("/c2c/download", "12");
        try {
            ILinkClientConfig config = ILinkClientConfig.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .cdnBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/c2c")
                    .maxMediaDownloadBytes(1)
                    .build();

            assertThrows(
                    ILinkException.class,
                    () -> new ILinkClient(config).downloadEncryptedMedia(new CDNMedia("parameter", null, null, null))
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void downloadRejectsUntrustedFullUrl() {
        ILinkClient client = new ILinkClient(ILinkClientConfig.builder().build());

        assertThrows(
                ILinkException.class,
                () -> client.downloadEncryptedMedia(new CDNMedia(null, null, null, "http://example.com/download"))
        );
    }

    @Test
    void sendTextExposesPreviouslySentChunksAfterPartialFailure() {
        FailingSendClient client = new FailingSendClient();
        ILinkAuthSession session = new ILinkAuthSession("token", "https://example.com", "bot", "user");
        String text = "x".repeat(2001);

        PartialTextSendException exception = assertThrows(
                PartialTextSendException.class,
                () -> client.sendText(session, "user", "context", text, "test")
        );

        assertEquals(1, exception.getSentChunks().size());
        assertEquals(0, exception.getSentChunks().get(0).index());
        assertEquals(2, client.sendCalls);
    }

    private static ILinkClientConfig configFor(HttpServer server) {
        return ILinkClientConfig.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
    }

    private static HttpServer startServer(String path, String response) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static final class FailingSendClient extends ILinkClient {

        private int sendCalls;

        private FailingSendClient() {
            super(ILinkClientConfig.builder().build());
        }

        @Override
        public SendMessageResponse sendMessage(ILinkAuthSession session, OutboundMessage msg) {
            sendCalls++;
            if (sendCalls == 2) {
                throw new ILinkException("second chunk failed");
            }
            return new SendMessageResponse(0, null, null);
        }
    }
}
