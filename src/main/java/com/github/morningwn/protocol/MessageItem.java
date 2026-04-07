package com.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Union-like message item structure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageItem(
        @JsonProperty("type") Integer type,
        @JsonProperty("create_time_ms") Long createTimeMs,
        @JsonProperty("update_time_ms") Long updateTimeMs,
        @JsonProperty("is_completed") Boolean isCompleted,
        @JsonProperty("msg_id") String msgId,
        @JsonProperty("ref_msg") RefMessage refMsg,
        @JsonProperty("text_item") TextItem textItem,
        @JsonProperty("image_item") ImageItem imageItem,
        @JsonProperty("voice_item") VoiceItem voiceItem,
        @JsonProperty("file_item") FileItem fileItem,
        @JsonProperty("video_item") VideoItem videoItem
) {
}
