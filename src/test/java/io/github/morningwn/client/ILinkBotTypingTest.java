package io.github.morningwn.client;

import io.github.morningwn.handler.SessionHandler;
import io.github.morningwn.protocol.ILinkAuthSession;
import io.github.morningwn.protocol.enums.BusinessCode;
import io.github.morningwn.protocol.enums.TypingStatus;
import io.github.morningwn.protocol.response.GetConfigResponse;
import io.github.morningwn.protocol.response.SendTypingResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ILinkBotTypingTest {

    private static void expireCachedTypingTicket(ILinkBot bot, String toUserId, String typingTicket)
            throws ReflectiveOperationException {
        setField(bot, "cachedTypingTicketUserId", toUserId);
        setField(bot, "cachedTypingTicketValue", typingTicket);
        setField(
                bot,
                "cachedTypingTicketExpiresAtMillis",
                System.currentTimeMillis() - Duration.ofHours(24).toMillis() - 1L
        );
    }

    private static void setField(ILinkBot bot, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = ILinkBot.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(bot, value);
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

    @Test
    void sendTypingShouldReuseCachedTypingTicketForSameUser() {
        ILinkClientConfig config = baseConfig();
        TypingClient client = new TypingClient(config, List.of("ticket-123"));
        ILinkBot bot = new ILinkBot(client, config, defaultSessionHandler());

        SendTypingResponse firstResponse = bot.sendTyping("target-user", "context-1", TypingStatus.START);
        SendTypingResponse secondResponse = bot.sendTyping("target-user", "context-2", TypingStatus.STOP);

        assertEquals(1, client.getConfigCalls);
        assertEquals(2, client.sendTypingCalls);
        assertEquals(List.of("target-user"), client.getConfigUserIds);
        assertEquals(List.of("context-1"), client.getConfigContextTokens);
        assertEquals(List.of("target-user", "target-user"), client.sendTypingUserIds);
        assertEquals(List.of("ticket-123", "ticket-123"), client.sendTypingTickets);
        assertEquals(List.of(TypingStatus.START, TypingStatus.STOP), client.sendTypingStatuses);
        assertEquals(BusinessCode.OK.code(), firstResponse.ret());
        assertEquals(BusinessCode.OK.code(), secondResponse.ret());
    }

    @Test
    void sendTypingShouldRefreshTypingTicketAfterExpiration() throws ReflectiveOperationException {
        ILinkClientConfig config = baseConfig();
        TypingClient client = new TypingClient(config, List.of("ticket-123", "ticket-456"));
        ILinkBot bot = new ILinkBot(client, config, defaultSessionHandler());

        bot.sendTyping("target-user", "context-1", TypingStatus.START);
        expireCachedTypingTicket(bot, "target-user", "ticket-123");
        SendTypingResponse response = bot.sendTyping("target-user", "context-2", TypingStatus.STOP);

        assertEquals(2, client.getConfigCalls);
        assertEquals(2, client.sendTypingCalls);
        assertEquals(List.of("target-user", "target-user"), client.getConfigUserIds);
        assertEquals(List.of("context-1", "context-2"), client.getConfigContextTokens);
        assertEquals(List.of("ticket-123", "ticket-456"), client.sendTypingTickets);
        assertEquals(List.of(TypingStatus.START, TypingStatus.STOP), client.sendTypingStatuses);
        assertEquals(BusinessCode.OK.code(), response.ret());
    }

    @Test
    void sendTypingShouldRefreshTypingTicketWhenTargetUserChanges() {
        ILinkClientConfig config = baseConfig();
        TypingClient client = new TypingClient(config, List.of("ticket-123", "ticket-456"));
        ILinkBot bot = new ILinkBot(client, config, defaultSessionHandler());

        bot.sendTyping("target-user-1", "context-1", TypingStatus.START);
        SendTypingResponse response = bot.sendTyping("target-user-2", "context-2", TypingStatus.STOP);

        assertEquals(2, client.getConfigCalls);
        assertEquals(2, client.sendTypingCalls);
        assertEquals(List.of("target-user-1", "target-user-2"), client.getConfigUserIds);
        assertEquals(List.of("context-1", "context-2"), client.getConfigContextTokens);
        assertEquals(List.of("target-user-1", "target-user-2"), client.sendTypingUserIds);
        assertEquals(List.of("ticket-123", "ticket-456"), client.sendTypingTickets);
        assertEquals(List.of(TypingStatus.START, TypingStatus.STOP), client.sendTypingStatuses);
        assertEquals(BusinessCode.OK.code(), response.ret());
    }

    private static final class TypingClient extends ILinkClient {

        private final List<String> typingTicketsToReturn;
        private final List<String> getConfigUserIds = new ArrayList<>();
        private final List<String> getConfigContextTokens = new ArrayList<>();
        private final List<String> sendTypingUserIds = new ArrayList<>();
        private final List<String> sendTypingTickets = new ArrayList<>();
        private final List<TypingStatus> sendTypingStatuses = new ArrayList<>();
        private int getConfigCalls;
        private int sendTypingCalls;

        private TypingClient(ILinkClientConfig config, List<String> typingTicketsToReturn) {
            super(config);
            this.typingTicketsToReturn = List.copyOf(typingTicketsToReturn);
        }

        @Override
        public GetConfigResponse getConfig(ILinkAuthSession session, String ilinkUserId, String contextToken) {
            getConfigCalls++;
            getConfigUserIds.add(ilinkUserId);
            getConfigContextTokens.add(contextToken);
            return new GetConfigResponse(
                    BusinessCode.OK.code(),
                    BusinessCode.OK.code(),
                    null,
                    typingTicketsToReturn.get(getConfigCalls - 1)
            );
        }

        @Override
        public SendTypingResponse sendTyping(
                ILinkAuthSession session,
                String ilinkUserId,
                String typingTicket,
                TypingStatus status
        ) {
            sendTypingCalls++;
            sendTypingUserIds.add(ilinkUserId);
            sendTypingTickets.add(typingTicket);
            sendTypingStatuses.add(status);
            return new SendTypingResponse(BusinessCode.OK.code(), BusinessCode.OK.code(), null);
        }
    }
}