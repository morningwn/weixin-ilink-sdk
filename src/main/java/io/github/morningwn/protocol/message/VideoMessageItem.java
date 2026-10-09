package io.github.morningwn.protocol.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.enums.MessageItemType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(using = ValueDeserializer.None.class)
public record VideoMessageItem(
        @JsonProperty("create_time_ms") Long createTimeMs,
        @JsonProperty("update_time_ms") Long updateTimeMs,
        @JsonProperty("is_completed") Boolean isCompleted,
        @JsonProperty("msg_id") String msgId,
        @JsonProperty("ref_msg") RefMessage refMsg,
        @JsonProperty("video_item") VideoItem videoItem
) implements MessageItem {
    public VideoMessageItem(VideoItem videoItem) {
        this(null, null, null, null, null, videoItem);
    }

    @Override
    @JsonProperty("type")
    public Integer typeCode() {
        return MessageItemType.VIDEO.code();
    }
}
