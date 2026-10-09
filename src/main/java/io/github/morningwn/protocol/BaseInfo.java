package io.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Common base_info payload for business POST requests.
 *
 * @param channelVersion SDK channel version sent to iLink service
 * @param botAgent       SDK identifier sent to iLink service
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BaseInfo(
        @JsonProperty("channel_version") String channelVersion,
        @JsonProperty("bot_agent") String botAgent
) {

    /**
     * Creates a base info record without a bot agent identifier.
     *
     * @param channelVersion channel version string
     */
    public BaseInfo(String channelVersion) {
        this(channelVersion, null);
    }

    /**
     * Creates a {@link BaseInfo} instance.
     *
     * @param channelVersion channel version string
     * @return base info record
     */
    public static BaseInfo of(String channelVersion) {
        return new BaseInfo(channelVersion);
    }

    /**
     * Creates a {@link BaseInfo} instance.
     *
     * @param channelVersion channel version string
     * @param botAgent       SDK identifier
     * @return base info record
     */
    public static BaseInfo of(String channelVersion, String botAgent) {
        return new BaseInfo(channelVersion, botAgent);
    }
}
