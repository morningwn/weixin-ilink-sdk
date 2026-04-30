package io.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.enums.MessageItemType;

/**
 * Union-like message item structure.
 *
 * @param type item type code, see {@link MessageItemType}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageItem(
        @JsonProperty("type") MessageItemType type,
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
