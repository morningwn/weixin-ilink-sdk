package com.github.morningwn.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.morningwn.exception.ILinkException;

/**
 * Jackson-based JSON codec implementation.
 */
public final class JacksonJsonCodec implements JsonCodec {

    private final ObjectMapper objectMapper;

    /**
     * Constructs a codec with default iLink-oriented mapper options.
     */
    public JacksonJsonCodec() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ILinkException("Failed to serialize request payload", e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new ILinkException("Failed to deserialize response payload", e);
        }
    }
}
