package io.github.morningwn.codec;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.github.morningwn.exception.ILinkException;

/**
 * Jackson-based JSON codec implementation.
 */
public final class JacksonJsonCodec implements JsonCodec {

    private final ObjectMapper objectMapper;

    /**
     * Constructs a codec with default iLink-oriented mapper options.
     */
    public JacksonJsonCodec() {
        this.objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    @Override
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new ILinkException("Failed to serialize request payload", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new ILinkException("Failed to deserialize response payload", e);
        }
    }
}
