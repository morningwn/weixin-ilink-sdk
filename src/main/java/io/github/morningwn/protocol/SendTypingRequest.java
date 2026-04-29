package io.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for sendtyping.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendTypingRequest(
        @JsonProperty("ilink_user_id") String ilinkUserId,
        @JsonProperty("typing_ticket") String typingTicket,
        @JsonProperty("status") Integer status,
        @JsonProperty("base_info") BaseInfo baseInfo
) {
}
