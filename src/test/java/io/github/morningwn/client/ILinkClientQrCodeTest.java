package io.github.morningwn.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.morningwn.protocol.CDNMedia;
import io.github.morningwn.protocol.response.QrCodeResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ILinkClientQrCodeTest {

    @Test
    void getBotQrcodeShouldPostRecentLocalTokens() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> authorizationType = new AtomicReference<>();
        AtomicReference<String> wechatUin = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();

        HttpServer server = startServer(exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            authorizationType.set(exchange.getRequestHeaders().getFirst("AuthorizationType"));
            wechatUin.set(exchange.getRequestHeaders().getFirst("X-WECHAT-UIN"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            query.set(exchange.getRequestURI().getRawQuery());
            writeQrCode(exchange);
        });
        try {
            ILinkClient client = new ILinkClient(configFor(server));

            QrCodeResponse response = client.getBotQrcode(List.of("new-token", "old-token"));

            assertEquals("POST", method.get());
            assertEquals("application/json", contentType.get());
            assertEquals("ilink_bot_token", authorizationType.get());
            assertNotNull(wechatUin.get());
            assertEquals("bot_type=3", query.get());
            assertEquals("{\"local_token_list\":[\"new-token\",\"old-token\"]}", requestBody.get());
            assertEquals("qr-token", response.qrcode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getBotQrcodeShouldLimitAndNormalizeLocalTokens() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeQrCode(exchange);
        });
        try {
            List<String> tokens = new ArrayList<>();
            tokens.add(" ");
            for (int index = 0; index < 11; index++) {
                tokens.add("token-" + index);
            }

            new ILinkClient(configFor(server)).getBotQrcode(tokens);

            assertEquals(
                    "{\"local_token_list\":[\"token-0\",\"token-1\",\"token-2\",\"token-3\",\"token-4\","
                            + "\"token-5\",\"token-6\",\"token-7\",\"token-8\",\"token-9\"]}",
                    requestBody.get()
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getBotQrcodeShouldNotUseDefaultRequestTimeout() throws Exception {
        HttpServer server = startServer(exchange -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            writeQrCode(exchange);
        });
        try {
            ILinkClientConfig config = ILinkClientConfig.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .requestTimeout(Duration.ofMillis(1))
                    .build();

            QrCodeResponse response = new ILinkClient(config).getBotQrcode();

            assertEquals("qr-token", response.qrcode());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getQrcodeStatusShouldUrlEncodeVerificationCode() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = startStatusServer(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] response = "{\"status\":\"wait\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        try {
            new ILinkClient(configFor(server)).getQrcodeStatus("qr token", configFor(server).getBaseUrl(), "123 456+");

            assertEquals("qrcode=qr%20token&verify_code=123%20456%2B", query.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cdnFallbackUrlsShouldUrlEncodeQueryParameters() throws Exception {
        AtomicReference<String> uploadQuery = new AtomicReference<>();
        AtomicReference<String> downloadQuery = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/c2c/upload", exchange -> {
            uploadQuery.set(exchange.getRequestURI().getRawQuery());
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("x-encrypted-param", "encrypted-response");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/c2c/download", exchange -> {
            downloadQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] response = "payload".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ILinkClientConfig config = ILinkClientConfig.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .cdnBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/c2c/")
                    .build();
            ILinkClient client = new ILinkClient(config);

            client.uploadEncryptedMedia(null, "upload value+", "file key+", new byte[]{1});
            byte[] downloaded = client.downloadEncryptedMedia(new CDNMedia("download value+", null, null, null));

            assertEquals("encrypted_query_param=upload%20value%2B&filekey=file%20key%2B", uploadQuery.get());
            assertEquals("encrypted_query_param=download%20value%2B", downloadQuery.get());
            assertEquals("payload", new String(downloaded, StandardCharsets.UTF_8));
        } finally {
            server.stop(0);
        }
    }

    private static ILinkClientConfig configFor(HttpServer server) {
        return ILinkClientConfig.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
    }

    private static HttpServer startServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ilink/bot/get_bot_qrcode", handler::handle);
        server.start();
        return server;
    }

    private static HttpServer startStatusServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ilink/bot/get_qrcode_status", handler::handle);
        server.start();
        return server;
    }

    private static void writeQrCode(HttpExchange exchange) throws IOException {
        byte[] response = "{\"qrcode\":\"qr-token\",\"qrcode_img_content\":\"https://example.com/qr\"}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
