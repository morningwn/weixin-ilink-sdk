package io.github.morningwn.client;

import io.github.morningwn.exception.ILinkException;
import io.github.morningwn.handler.SessionHandler;
import io.github.morningwn.protocol.ILinkAuthSession;
import io.github.morningwn.protocol.enums.BusinessCode;
import io.github.morningwn.protocol.response.GetUpdatesResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ILinkBotNotifyTest {

    @Test
    void autoPullShouldNotifyBackendWhenItStartsAndStops() throws Exception {
        ILinkClientConfig config = ILinkClientConfig.builder()
                .baseUrl("https://example.com")
                .cdnBaseUrl("https://example.com")
                .longPollingTimeout(Duration.ofSeconds(5))
                .build();
        LifecycleTrackingClient client = new LifecycleTrackingClient(config);
        ILinkBot bot = new ILinkBot(client, config, new SessionHandler() {
            @Override
            public ILinkAuthSession loadSession() {
                return new ILinkAuthSession("token", "https://example.com", "bot", "user");
            }
        });

        bot.startAutoPull((message, sender) -> {
        });

        assertTrue(client.getUpdatesEntered.await(1, TimeUnit.SECONDS), "auto pull should enter getUpdates");
        bot.stopAutoPull();
        assertTrue(client.notifyStopCalled.await(1, TimeUnit.SECONDS), "notifyStop should run after auto pull stops");
        bot.close();

        assertEquals(List.of("start", "getupdates", "stop"), client.events.subList(0, 3));
        assertEquals("token", client.notifyStartSession.token());
        assertEquals("token", client.notifyStopSession.token());
    }

    private static final class LifecycleTrackingClient extends ILinkClient {

        private final List<String> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch getUpdatesEntered = new CountDownLatch(1);
        private final CountDownLatch notifyStopCalled = new CountDownLatch(1);
        private final AtomicInteger getUpdatesCalls = new AtomicInteger();
        private ILinkAuthSession notifyStartSession;
        private ILinkAuthSession notifyStopSession;

        private LifecycleTrackingClient(ILinkClientConfig config) {
            super(config);
        }

        @Override
        void notifyStart(ILinkAuthSession session) {
            events.add("start");
            notifyStartSession = session;
        }

        @Override
        void notifyStop(ILinkAuthSession session) {
            events.add("stop");
            notifyStopSession = session;
            notifyStopCalled.countDown();
        }

        @Override
        public GetUpdatesResponse getUpdates(ILinkAuthSession session, String getUpdatesBuf, Duration timeout) {
            events.add("getupdates");
            if (getUpdatesCalls.getAndIncrement() == 0) {
                getUpdatesEntered.countDown();
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ILinkException("HTTP request interrupted", e);
                }
            }
            return new GetUpdatesResponse(
                    BusinessCode.OK.code(),
                    BusinessCode.OK.code(),
                    null,
                    List.of(),
                    getUpdatesBuf,
                    null
            );
        }
    }
}
