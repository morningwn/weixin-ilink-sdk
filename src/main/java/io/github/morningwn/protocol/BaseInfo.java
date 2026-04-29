package io.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Common base_info payload for business POST requests.
 *
 * @param channelVersion SDK channel version sent to iLink service
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaseInfo(@JsonProperty("channel_version") String channelVersion) {

    /**
     * Creates a {@link BaseInfo} instance.
     *
     * @param channelVersion channel version string
     * @return base info record
     */
    public static BaseInfo of(String channelVersion) {
        return new BaseInfo(channelVersion);
    }
}
