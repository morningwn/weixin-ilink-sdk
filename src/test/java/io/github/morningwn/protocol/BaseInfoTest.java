package io.github.morningwn.protocol;

import io.github.morningwn.codec.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseInfoTest {

    private final JacksonJsonCodec jsonCodec = new JacksonJsonCodec();

    @Test
    void shouldSerializeBotAgent() {
        String json = jsonCodec.toJson(BaseInfo.of("1.0.0", "weixin-ilink-sdk/1.0.1"));

        assertEquals(
                "{\"channel_version\":\"1.0.0\",\"bot_agent\":\"weixin-ilink-sdk/1.0.1\"}",
                json
        );
    }

    @Test
    void singleArgumentConstructorShouldRemainCompatible() {
        String json = jsonCodec.toJson(new BaseInfo("1.0.0"));

        assertEquals("{\"channel_version\":\"1.0.0\"}", json);
    }
}
