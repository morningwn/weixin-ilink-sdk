package com.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for getuploadurl.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetUploadUrlRequest(
        @JsonProperty("filekey") String filekey,
        @JsonProperty("media_type") Integer mediaType,
        @JsonProperty("to_user_id") String toUserId,
        @JsonProperty("rawsize") Long rawsize,
        @JsonProperty("rawfilemd5") String rawfilemd5,
        @JsonProperty("filesize") Long filesize,
        @JsonProperty("thumb_rawsize") Long thumbRawsize,
        @JsonProperty("thumb_rawfilemd5") String thumbRawfilemd5,
        @JsonProperty("thumb_filesize") Long thumbFilesize,
        @JsonProperty("no_need_thumb") Boolean noNeedThumb,
        @JsonProperty("aeskey") String aeskey,
        @JsonProperty("base_info") BaseInfo baseInfo
) {
}
