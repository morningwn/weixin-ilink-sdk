package io.github.morningwn.protocol.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.morningwn.protocol.enums.MessageItemType;
import tools.jackson.databind.annotation.JsonDeserialize;

/** A type-safe message item from the iLink protocol. */
@JsonDeserialize(using = MessageItemDeserializer.class)
public sealed interface MessageItem permits TextMessageItem, ImageMessageItem, VoiceMessageItem, FileMessageItem, VideoMessageItem,
        UnknownMessageItem {

    /** Protocol numeric type code, retained for unknown future types. */
    Integer typeCode();

    @JsonIgnore
    default MessageItemType type() {
        return MessageItemType.fromCode(typeCode());
    }
}
