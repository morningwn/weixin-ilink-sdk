package io.github.morningwn.protocol.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.enums.MessageState;
import io.github.morningwn.protocol.enums.MessageType;

import java.util.List;
import java.util.Objects;

/** Message ready to send through the iLink sendmessage endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutboundMessage(
        @JsonProperty("to_user_id") String toUserId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("message_type") MessageType messageType,
        @JsonProperty("message_state") MessageState messageState,
        @JsonProperty("item_list") List<MessageItem> itemList,
        @JsonProperty("context_token") String contextToken
) {
    public OutboundMessage {
        Objects.requireNonNull(toUserId, "toUserId cannot be null");
        Objects.requireNonNull(clientId, "clientId cannot be null");
        Objects.requireNonNull(itemList, "itemList cannot be null");
        Objects.requireNonNull(contextToken, "contextToken cannot be null");
    }

    public static OutboundMessage botFinish(String toUserId, String clientId, List<MessageItem> itemList, String contextToken) {
        return new OutboundMessage(toUserId, clientId, MessageType.BOT, MessageState.FINISH, itemList, contextToken);
    }
}
