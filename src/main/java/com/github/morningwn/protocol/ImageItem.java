package com.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Image message payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageItem(
        @JsonProperty("media") CDNMedia media,
        @JsonProperty("thumb_media") CDNMedia thumbMedia,
        @JsonProperty("aeskey") String aeskey,
        @JsonProperty("url") String url,
        @JsonProperty("mid_size") Long midSize,
        @JsonProperty("thumb_size") Long thumbSize,
        @JsonProperty("thumb_height") Integer thumbHeight,
        @JsonProperty("thumb_width") Integer thumbWidth,
        @JsonProperty("hd_size") Long hdSize
) {
}
