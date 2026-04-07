package com.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response body for getupdates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetUpdatesResponse(
        @JsonProperty("ret") Integer ret,
        @JsonProperty("errcode") Integer errcode,
        @JsonProperty("errmsg") String errmsg,
        @JsonProperty("msgs") List<WeixinMessage> msgs,
        @JsonProperty("get_updates_buf") String getUpdatesBuf,
        @JsonProperty("longpolling_timeout_ms") Integer longpollingTimeoutMs
) {
}
