package com.github.morningwn.codec;

/**
 * JSON codec abstraction.
 */
public interface JsonCodec {

    /**
     * Serializes an object into JSON string.
     *
     * @param value source object
     * @return JSON string
     */
    String toJson(Object value);

    /**
     * Deserializes JSON string into target type.
     *
     * @param json json string
     * @param type target class
     * @param <T> target generic type
     * @return decoded object
     */
    <T> T fromJson(String json, Class<T> type);
}
