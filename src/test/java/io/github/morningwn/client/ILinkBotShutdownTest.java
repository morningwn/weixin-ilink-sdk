package io.github.morningwn.client;

import io.github.morningwn.exception.ILinkException;
import io.github.morningwn.handler.SessionHandler;
import io.github.morningwn.protocol.GetUpdatesResponse;
import io.github.morningwn.protocol.ProtocolValues;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ILinkBotShutdownTest {

    @Test
    void closeShouldGracefullyStopAutoPull() throws Exception {
        ILinkClientConfig config = ILinkClientConfig.builder()
                .baseUrl("https://example.com")
                .cdnBaseUrl("https://example.com")
                .build();

        CountDownLatch enteredGetUpdates = new CountDownLatch(1);
        CountDownLatch interruptedByShutdown = new CountDownLatch(1);
        BlockingGetUpdatesClient client = new BlockingGetUpdatesClient(config, enteredGetUpdates, interruptedByShutdown);

        SessionHandler sessionHandler = new SessionHandler() {
            @Override
            public ILinkAuthSession loadSession() {
                return new ILinkAuthSession("token", "https://example.com", "bot", "user");
            }
        };

        ILinkBot bot = new ILinkBot(client, config, sessionHandler);
        bot.startAutoPull(message -> {
        });

        assertTrue(enteredGetUpdates.await(1, TimeUnit.SECONDS), "auto pull should enter getUpdates");

        long startNanos = System.nanoTime();
        bot.close();
        bot.close();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertFalse(bot.isAutoPulling(), "auto pull should be stopped after close");
        assertTrue(interruptedByShutdown.await(2, TimeUnit.SECONDS), "blocking getUpdates should be interrupted by forced shutdown");
        assertTrue(elapsedMillis < 6_000, "close should finish within a bounded time");
    }

    private static final class BlockingGetUpdatesClient extends ILinkClient {

        private final CountDownLatch enteredGetUpdates;
        private final CountDownLatch interruptedByShutdown;

        private BlockingGetUpdatesClient(
                ILinkClientConfig config,
                CountDownLatch enteredGetUpdates,
                CountDownLatch interruptedByShutdown
        ) {
            super(config);
            this.enteredGetUpdates = enteredGetUpdates;
            this.interruptedByShutdown = interruptedByShutdown;
        }

        @Override
        public GetUpdatesResponse getUpdates(ILinkAuthSession session, String getUpdatesBuf, Duration timeout) {
            enteredGetUpdates.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interruptedByShutdown.countDown();
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
    }
}
