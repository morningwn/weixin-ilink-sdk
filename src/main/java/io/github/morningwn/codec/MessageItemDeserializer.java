package io.github.morningwn.codec;

import io.github.morningwn.protocol.enums.MessageItemType;
import io.github.morningwn.protocol.message.FileMessageItem;
import io.github.morningwn.protocol.message.ImageMessageItem;
import io.github.morningwn.protocol.message.MessageItem;
import io.github.morningwn.protocol.message.TextMessageItem;
import io.github.morningwn.protocol.message.UnknownMessageItem;
import io.github.morningwn.protocol.message.VideoMessageItem;
import io.github.morningwn.protocol.message.VoiceMessageItem;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/** Resolves a concrete message item from the protocol type code. */
public final class MessageItemDeserializer extends StdDeserializer<MessageItem> {

    public MessageItemDeserializer() {
        super(MessageItem.class);
    }

    @Override
    public MessageItem deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        JsonNode node = context.readTree(parser);
        JsonNode typeNode = node.get("type");
        MessageItemType type = typeNode == null ? null : MessageItemType.fromCode(typeNode.asInt());
        if (type == null) {
            return context.readTreeAsValue(node, UnknownMessageItem.class);
        }
        return switch (type) {
            case TEXT -> context.readTreeAsValue(node, TextMessageItem.class);
            case IMAGE -> context.readTreeAsValue(node, ImageMessageItem.class);
            case VOICE -> context.readTreeAsValue(node, VoiceMessageItem.class);
            case FILE -> context.readTreeAsValue(node, FileMessageItem.class);
            case VIDEO -> context.readTreeAsValue(node, VideoMessageItem.class);
        };
    }
}
