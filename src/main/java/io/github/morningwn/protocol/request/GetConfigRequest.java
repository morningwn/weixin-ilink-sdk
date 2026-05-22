package io.github.morningwn.protocol.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.BaseInfo;

/**
 * Request body for getconfig.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetConfigRequest(
        @JsonProperty("ilink_user_id") String ilinkUserId,
        @JsonProperty("context_token") String contextToken,
        @JsonProperty("base_info") BaseInfo baseInfo
) {
}
