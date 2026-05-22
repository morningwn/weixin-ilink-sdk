package io.github.morningwn.protocol.message;

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

    public static MessageItem ofText(TextItem textItem) {
        return new MessageItem(MessageItemType.TEXT, null, null, null, null, null, textItem, null, null, null, null);
    }

    public static MessageItem ofImage(ImageItem imageItem) {
        return new MessageItem(MessageItemType.IMAGE, null, null, null, null, null, null, imageItem, null, null, null);
    }

    public static MessageItem ofVoice(VoiceItem voiceItem) {
        return new MessageItem(MessageItemType.VOICE, null, null, null, null, null, null, null, voiceItem, null, null);
    }

    public static MessageItem ofFile(FileItem fileItem) {
        return new MessageItem(MessageItemType.FILE, null, null, null, null, null, null, null, null, fileItem, null);
    }

    public static MessageItem ofVideo(VideoItem videoItem) {
        return new MessageItem(MessageItemType.VIDEO, null, null, null, null, null, null, null, null, null, videoItem);
    }
}
