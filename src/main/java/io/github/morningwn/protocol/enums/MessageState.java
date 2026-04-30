package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Message state in iLink protocol.
 */
public enum MessageState {

    /**
     * New state.
     */
    NEW(0),
    /**
     * Generating state.
     */
    GENERATING(1),
    /**
     * Finish state.
     */
    FINISH(2);

    private final int code;

    private static final Map<Integer, MessageState> CODE_MAP = Arrays.stream(values()).collect(Collectors.toMap(x -> x.code, Function.identity()));

    MessageState(int code) {
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
    public static MessageState fromCode(Integer code) {
        return CODE_MAP.get(code);
    }
}
