package io.github.morningwn.protocol.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.BaseInfo;

/**
 * Request body for getupdates.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetUpdatesRequest(
        @JsonProperty("get_updates_buf") String getUpdatesBuf,
        @JsonProperty("base_info") BaseInfo baseInfo,
        @JsonProperty("sync_buf") String syncBuf
) {
}
