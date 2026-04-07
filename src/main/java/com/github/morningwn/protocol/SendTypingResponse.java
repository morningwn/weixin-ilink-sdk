package com.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for sendtyping.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendTypingResponse(
        @JsonProperty("ret") Integer ret,
        @JsonProperty("errcode") Integer errcode,
        @JsonProperty("errmsg") String errmsg
) {
}
