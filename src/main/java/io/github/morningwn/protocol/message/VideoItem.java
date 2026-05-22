package io.github.morningwn.protocol.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.CDNMedia;

/**
 * Video message payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoItem(
        @JsonProperty("media") CDNMedia media,
        @JsonProperty("video_size") Long videoSize,
        @JsonProperty("play_length") Long playLength,
        @JsonProperty("video_md5") String videoMd5,
        @JsonProperty("thumb_media") CDNMedia thumbMedia,
        @JsonProperty("thumb_size") Long thumbSize,
        @JsonProperty("thumb_height") Integer thumbHeight,
        @JsonProperty("thumb_width") Integer thumbWidth
) {

    public static VideoItem ofUpload(CDNMedia media, long videoSize, String videoMd5) {
        return new VideoItem(media, videoSize, null, videoMd5, null, null, null, null);
    }
}
