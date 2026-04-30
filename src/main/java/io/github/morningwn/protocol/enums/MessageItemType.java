package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Message item type in iLink protocol.
 */
public enum MessageItemType {

    /**
     * Text item.
     */
    TEXT(1),
    /**
     * Image item.
     */
    IMAGE(2),
    /**
     * Voice item.
     */
    VOICE(3),
    /**
     * File item.
     */
    FILE(4),
    /**
     * Video item.
     */
    VIDEO(5);

    private final int code;

    private static final Map<Integer, MessageItemType> CODE_MAP = Arrays.stream(values()).collect(Collectors.toMap(x -> x.code, Function.identity()));


    MessageItemType(int code) {
        this.code = code;
    }

    /**
     * @return protocol numeric code
     */
    @JsonValue
    public int code() {
        return code;
    }

    /**
     * Resolve enum from protocol code.
     *
     * @param code protocol numeric code
     * @return enum value or null when unknown
     */
    @JsonCreator
    public static MessageItemType fromCode(Integer code) {
        return CODE_MAP.get(code);
    }
}
