package io.github.morningwn.protocol.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.morningwn.protocol.enums.MessageItemType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImageMessageItem(
        @JsonProperty("create_time_ms") Long createTimeMs,
        @JsonProperty("update_time_ms") Long updateTimeMs,
        @JsonProperty("is_completed") Boolean isCompleted,
        @JsonProperty("msg_id") String msgId,
        @JsonProperty("ref_msg") RefMessage refMsg,
        @JsonProperty("image_item") ImageItem imageItem
) implements MessageItem {

    public ImageMessageItem(ImageItem imageItem) {
        this(null, null, null, null, null, imageItem);
    }

    @Override
    @JsonProperty("type")
    public Integer typeCode() {
        return MessageItemType.IMAGE.code();
    }
}
