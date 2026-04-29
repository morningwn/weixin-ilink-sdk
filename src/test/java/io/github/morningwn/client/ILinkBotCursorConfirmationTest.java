package io.github.morningwn.client;

import io.github.morningwn.handler.SessionHandler;
import io.github.morningwn.protocol.GetUpdatesResponse;
import io.github.morningwn.protocol.ProtocolValues;
import io.github.morningwn.protocol.WeixinMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ILinkBotCursorConfirmationTest {

    @Test
    void shouldRetryWithSameCursorWhenMessageHandlerFails() throws Exception {
        ILinkClientConfig config = baseConfig();
        RetrySameCursorClient client = new RetrySameCursorClient(config);
        ILinkBot bot = new ILinkBot(client, config, defaultSessionHandler());

        bot.startAutoPull(message -> {
            throw new IllegalStateException("simulated handler failure");
        });

        assertTrue(client.awaitSecondCall(3, TimeUnit.SECONDS), "second getUpdates call should happen");

        bot.stopAutoPull();
        bot.close();

        List<String> requestedBuffers = client.requestedBuffers();
        assertTrue(requestedBuffers.size() >= 2, "at least two getUpdates calls should be captured");
        assertEquals("", requestedBuffers.get(0));
        assertEquals("", requestedBuffers.get(1));
    }

    @Test
    void shouldUseSessionHandlerConfirmationBeforeCursorCommit() throws Exception {
        ILinkClientConfig config = baseConfig();
        RejectCommitClient client = new RejectCommitClient(config);
        SessionHandler sessionHandler = new SessionHandler() {
            @Override
            public ILinkAuthSession loadSession() {
                return new ILinkAuthSession("token", "https://example.com", "bot", "user");
            }

            @Override
            public String confirmGetUpdatesBuf(
                    String currentGetUpdatesBuf,
                    String suggestedGetUpdatesBuf,
                    List<WeixinMessage> receivedMessages,
                    boolean fullyProcessed
            ) {
                return currentGetUpdatesBuf;
            }
        };

        ILinkBot bot = new ILinkBot(client, config, sessionHandler);
        bot.startAutoPull(message -> {
        });

        assertTrue(client.awaitSecondCall(1, TimeUnit.SECONDS), "second getUpdates call should happen");

        bot.stopAutoPull();
        bot.close();

        List<String> requestedBuffers = client.requestedBuffers();
        assertTrue(requestedBuffers.size() >= 2, "at least two getUpdates calls should be captured");
        assertEquals("", requestedBuffers.get(0));
        assertEquals("", requestedBuffers.get(1));
    }

    @Test
    void closeShouldRunShutdownCleanupAndCommitOnlyWhenNoPendingMessages() {
        ILinkClientConfig config = baseConfig();
        CleanupClient client = new CleanupClient(config);
        ILinkBot bot = new ILinkBot(client, config, defaultSessionHandler());
        bot.setGetUpdatesBuf("cursor-a");

        bot.close();

        assertEquals(List.of("cursor-a"), client.requestedBuffers());
        assertEquals(List.of(Duration.ofMillis(1_000)), client.requestedTimeouts());
        assertEquals("cursor-b", bot.getGetUpdatesBuf());
    }

    private static ILinkClientConfig baseConfig() {
        return ILinkClientConfig.builder()
                .baseUrl("https://example.com")
                .cdnBaseUrl("https://example.com")
                .longPollingTimeout(Duration.ofSeconds(5))
                .build();
    }

    private static SessionHandler defaultSessionHandler() {
        return new SessionHandler() {
            @Override
            public ILinkAuthSession loadSession() {
                return new ILinkAuthSession("token", "https://example.com", "bot", "user");
            }
        };
    }

    private static WeixinMessage testMessage(long messageId) {
        return new WeixinMessage(
                null,
                messageId,
                "from",
                "to",
                "client",
                null,
                null,
                null,
                null,
                null,
                ProtocolValues.MESSAGE_TYPE_BOT,
                ProtocolValues.MESSAGE_STATE_FINISH,
                List.of(),
                "context"
        );
    }

    private static final class RetrySameCursorClient extends ILinkClient {

        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch secondCallLatch = new CountDownLatch(1);
        private final List<String> requestedBuffers = new CopyOnWriteArrayList<>();

        private RetrySameCursorClient(ILinkClientConfig config) {
            super(config);
        }

        @Override
        public GetUpdatesResponse getUpdates(ILinkAuthSession session, String getUpdatesBuf, Duration timeout) {
            requestedBuffers.add(getUpdatesBuf == null ? "" : getUpdatesBuf);
            int call = calls.incrementAndGet();
            if (call == 1) {
                return new GetUpdatesResponse(
                        ProtocolValues.RET_OK,
                        ProtocolValues.RET_OK,
                        null,
                        List.of(testMessage(1L)),
                        "next-1",
                        null
                );
            }
            secondCallLatch.countDown();
            return new GetUpdatesResponse(
                    ProtocolValues.RET_OK,
                    ProtocolValues.RET_OK,
                    null,
                    List.of(),
                    getUpdatesBuf,
                    null
            );
        }

        private boolean awaitSecondCall(long timeout, TimeUnit unit) throws InterruptedException {
            return secondCallLatch.await(timeout, unit);
        }

        private List<String> requestedBuffers() {
            return requestedBuffers;
        }
    }

    private static final class RejectCommitClient extends ILinkClient {

        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch secondCallLatch = new CountDownLatch(1);
        private final List<String> requestedBuffers = new CopyOnWriteArrayList<>();

        private RejectCommitClient(ILinkClientConfig config) {
            super(config);
        }

        @Override
        public GetUpdatesResponse getUpdates(ILinkAuthSession session, String getUpdatesBuf, Duration timeout) {
            requestedBuffers.add(getUpdatesBuf == null ? "" : getUpdatesBuf);
            int call = calls.incrementAndGet();
            if (call == 1) {
                return new GetUpdatesResponse(
                        ProtocolValues.RET_OK,
                        ProtocolValues.RET_OK,
                        null,
                        List.of(testMessage(2L)),
                        "next-2",
                        null
                );
            }
            secondCallLatch.countDown();
            return new GetUpdatesResponse(
                    ProtocolValues.RET_OK,
                    ProtocolValues.RET_OK,
                    null,
                    List.of(),
                    getUpdatesBuf,
                    null
            );
        }

        private boolean awaitSecondCall(long timeout, TimeUnit unit) throws InterruptedException {
            return secondCallLatch.await(timeout, unit);
        }

        private List<String> requestedBuffers() {
            return requestedBuffers;
        }
    }

    private static final class CleanupClient extends ILinkClient {

        private final List<String> requestedBuffers = new CopyOnWriteArrayList<>();
        private final List<Duration> requestedTimeouts = new CopyOnWriteArrayList<>();

        private CleanupClient(ILinkClientConfig config) {
            super(config);
        }

        @Override
        public GetUpdatesResponse getUpdates(ILinkAuthSession session, String getUpdatesBuf, Duration timeout) {
            requestedBuffers.add(getUpdatesBuf);
            requestedTimeouts.add(timeout);
            return new GetUpdatesResponse(
                    ProtocolValues.RET_OK,
                    ProtocolValues.RET_OK,
                    null,
                    List.of(),
                    "cursor-b",
                    null
            );
        }

        private List<String> requestedBuffers() {
            return requestedBuffers;
        }

        private List<Duration> requestedTimeouts() {
            return requestedTimeouts;
        }
    }
}
