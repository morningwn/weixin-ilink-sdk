package io.github.morningwn.protocol.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for notifystart and notifystop.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotifyResponse(
        @JsonProperty("ret") Integer ret,
        @JsonProperty("errcode") Integer errcode,
        @JsonProperty("errmsg") String errmsg
) {
}
