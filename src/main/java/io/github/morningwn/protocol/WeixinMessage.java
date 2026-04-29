package io.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Core message structure for inbound and outbound traffic.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeixinMessage(
        @JsonProperty("seq") Long seq,
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("from_user_id") String fromUserId,
        @JsonProperty("to_user_id") String toUserId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("create_time_ms") Long createTimeMs,
        @JsonProperty("update_time_ms") Long updateTimeMs,
        @JsonProperty("delete_time_ms") Long deleteTimeMs,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("group_id") String groupId,
        @JsonProperty("message_type") Integer messageType,
        @JsonProperty("message_state") Integer messageState,
        @JsonProperty("item_list") List<MessageItem> itemList,
        @JsonProperty("context_token") String contextToken
) {
}
