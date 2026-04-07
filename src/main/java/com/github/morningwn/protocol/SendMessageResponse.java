package com.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for sendmessage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageResponse(
        @JsonProperty("ret") Integer ret,
        @JsonProperty("errcode") Integer errcode,
        @JsonProperty("errmsg") String errmsg
) {
}
