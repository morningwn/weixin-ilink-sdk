package com.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CDN media reference shared by image/file/voice/video.
 *
 * @param encryptQueryParam encrypted query param for CDN access
 * @param aesKey base64-encoded key payload
 * @param encryptType encrypt type
 * @param fullUrl direct full URL if provided by server
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CDNMedia(
        @JsonProperty("encrypt_query_param") String encryptQueryParam,
        @JsonProperty("aes_key") String aesKey,
        @JsonProperty("encrypt_type") Integer encryptType,
        @JsonProperty("full_url") String fullUrl
) {
}
