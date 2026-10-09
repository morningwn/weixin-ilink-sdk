package io.github.morningwn.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.morningwn.protocol.ILinkAuthSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ILinkClientNotifyTest {

    @Test
    void notifyStartAndNotifyStopShouldPostBaseInfoToTheirEndpoints() throws Exception {
        AtomicReference<String> startMethod = new AtomicReference<>();
        AtomicReference<String> startBody = new AtomicReference<>();
        AtomicReference<String> stopMethod = new AtomicReference<>();
        AtomicReference<String> stopBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ilink/bot/msg/notifystart", exchange -> {
            startMethod.set(exchange.getRequestMethod());
            startBody.set(readRequestBody(exchange));
            writeSuccess(exchange);
        });
        server.createContext("/ilink/bot/msg/notifystop", exchange -> {
            stopMethod.set(exchange.getRequestMethod());
            stopBody.set(readRequestBody(exchange));
            writeSuccess(exchange);
        });
        server.start();
        try {
            ILinkClientConfig config = ILinkClientConfig.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .channelVersion("2.4.8")
                    .botAgent("test-agent")
                    .build();
            ILinkClient client = new ILinkClient(config);
            ILinkAuthSession session = new ILinkAuthSession("token", config.getBaseUrl(), "bot", "user");

            client.notifyStart(session);
            client.notifyStop(session);

            assertEquals("POST", startMethod.get());
            assertEquals("POST", stopMethod.get());
            assertEquals("{\"base_info\":{\"channel_version\":\"2.4.8\",\"bot_agent\":\"test-agent\"}}", startBody.get());
            assertEquals(startBody.get(), stopBody.get());
        } finally {
            server.stop(0);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void writeSuccess(HttpExchange exchange) throws IOException {
        byte[] response = "{\"ret\":0,\"errmsg\":\"\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
