package com.github.morningwn.client;

import com.github.morningwn.exception.ILinkException;
import com.github.morningwn.handler.SessionHandler;
import com.github.morningwn.protocol.GetUpdatesResponse;
import com.github.morningwn.protocol.ProtocolValues;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ILinkBotLongPollingStrategyTest {

    @Test
    void shouldAdjustLongPollingTimeoutByServerHint() throws Exception {
        ILinkClientConfig config = ILinkClientConfig.builder()
                .baseUrl("https://example.com")
                .cdnBaseUrl("https://example.com")
                .longPollingTimeout(Duration.ofSeconds(40))
                .build();

        TimeoutTrackingClient client = new TimeoutTrackingClient(config);
        SessionHandler sessionHandler = new SessionHandler() {
            @Override
            public ILinkAuthSession loadSession() {
                return new ILinkAuthSession("token", "https://example.com", "bot", "user");
            }
        };

        ILinkBot bot = new ILinkBot(client, config, sessionHandler);
        bot.startAutoPull(message -> {
        });

        assertTrue(client.awaitSecondCall(1, TimeUnit.SECONDS), "second getUpdates call should happen");

        bot.stopAutoPull();
        bot.close();

        List<Duration> requestedTimeouts = client.requestedTimeouts();
        assertTrue(requestedTimeouts.size() >= 2, "at least two getUpdates calls should be captured");
        assertEquals(Duration.ofSeconds(40), requestedTimeouts.get(0));
        assertEquals(Duration.ofSeconds(36), requestedTimeouts.get(1));
    }

    private static final class TimeoutTrackingClient extends ILinkClient {

        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch secondCallLatch = new CountDownLatch(1);
        private final List<Duration> requestedTimeouts = new CopyOnWriteArrayList<>();

        private TimeoutTrackingClient(ILinkClientConfig config) {
            super(config);
        }

        @Override
        public GetUpdatesResponse getUpdates(ILinkAuthSession session, String getUpdatesBuf, Duration timeout) {
            requestedTimeouts.add(timeout);
            int call = calls.incrementAndGet();
            if (call == 1) {
                return new GetUpdatesResponse(
                        ProtocolValues.RET_OK,
                        ProtocolValues.RET_OK,
                        null,
                        List.of(),
                        getUpdatesBuf,
                        35_000
                );
            }

            secondCallLatch.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ILinkException("HTTP request interrupted", e);
            }
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

        private List<Duration> requestedTimeouts() {
            return requestedTimeouts;
        }
    }
}
