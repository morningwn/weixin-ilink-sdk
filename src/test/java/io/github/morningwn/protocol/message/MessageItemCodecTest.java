package io.github.morningwn.protocol.message;

import io.github.morningwn.codec.JacksonJsonCodec;
import io.github.morningwn.codec.JsonCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageItemCodecTest {

    private final JsonCodec codec = new JacksonJsonCodec();

    @Test
    void shouldDeserializeAndSerializeTextItemWithItsConcreteType() {
        MessageItem item = codec.fromJson("{\"type\":1,\"text_item\":{\"text\":\"hello\"}}", MessageItem.class);

        TextMessageItem textItem = assertInstanceOf(TextMessageItem.class, item);
        assertEquals("hello", textItem.textItem().text());
        assertEquals("{\"text_item\":{\"text\":\"hello\"},\"type\":1}", codec.toJson(item));
    }

    @Test
    void shouldDeserializeConcreteTypeWithoutMessageItemDeserializer() {
        TextMessageItem item = codec.fromJson("{\"text_item\":{\"text\":\"hello\"}}", TextMessageItem.class);

        assertEquals("hello", item.textItem().text());
    }

    @Test
    void shouldPreserveUnknownTypeAndPayload() {
        MessageItem item = codec.fromJson("{\"type\":99,\"future_item\":{\"value\":1}}", MessageItem.class);

        UnknownMessageItem unknownItem = assertInstanceOf(UnknownMessageItem.class, item);
        assertEquals(99, unknownItem.typeCode());
        assertNull(unknownItem.type());
        assertEquals("{\"type\":99,\"future_item\":{\"value\":1}}", codec.toJson(item));
    }
}
