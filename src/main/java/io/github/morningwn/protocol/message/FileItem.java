package io.github.morningwn.protocol.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.CDNMedia;

/**
 * File message payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileItem(
        @JsonProperty("media") CDNMedia media,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("md5") String md5,
        @JsonProperty("len") String len
) {
}
