package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Message direction type in iLink protocol.
 */
public enum MessageType {

    /**
     * User inbound message.
     */
    USER(1),
    /**
     * Bot outbound message.
     */
    BOT(2);

    private final int code;

    private static final Map<Integer, MessageType> CODE_MAP = Arrays.stream(values()).collect(Collectors.toMap(x -> x.code, Function.identity()));

    MessageType(int code) {
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
    public static MessageType fromCode(Integer code) {
        return CODE_MAP.get(code);
    }
}