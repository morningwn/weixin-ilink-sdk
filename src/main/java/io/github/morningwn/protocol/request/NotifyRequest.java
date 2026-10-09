package io.github.morningwn.protocol.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.BaseInfo;

/**
 * Request body for notifystart and notifystop.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotifyRequest(
        @JsonProperty("base_info") BaseInfo baseInfo
) {
}
