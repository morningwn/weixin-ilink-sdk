package io.github.morningwn.protocol.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.BaseInfo;
import io.github.morningwn.protocol.enums.TypingStatus;

/**
 * Request body for sendtyping.
 *
 * @param status typing status, see {@link TypingStatus}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendTypingRequest(
        @JsonProperty("ilink_user_id") String ilinkUserId,
        @JsonProperty("typing_ticket") String typingTicket,
        @JsonProperty("status") TypingStatus status,
        @JsonProperty("base_info") BaseInfo baseInfo
) {
}
