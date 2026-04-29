package io.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for getuploadurl.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetUploadUrlResponse(
        @JsonProperty("ret") Integer ret,
        @JsonProperty("errcode") Integer errcode,
        @JsonProperty("errmsg") String errmsg,
        @JsonProperty("upload_param") String uploadParam,
        @JsonProperty("thumb_upload_param") String thumbUploadParam,
        @JsonProperty("upload_full_url") String uploadFullUrl
) {
}
