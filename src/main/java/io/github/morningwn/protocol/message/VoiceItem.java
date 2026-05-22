package io.github.morningwn.protocol.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.CDNMedia;

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

    public static VoiceItem ofUpload(CDNMedia media, long playtime) {
        return new VoiceItem(media, null, null, null, playtime, null);
    }
}
