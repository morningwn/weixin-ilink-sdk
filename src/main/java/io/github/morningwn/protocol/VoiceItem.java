package io.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Voice message payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoiceItem(
        @JsonProperty("media") CDNMedia media,
        @JsonProperty("encode_type") Integer encodeType,
        @JsonProperty("bits_per_sample") Integer bitsPerSample,
        @JsonProperty("sample_rate") Integer sampleRate,
        @JsonProperty("playtime") Long playtime,
        @JsonProperty("text") String text
) {
}
