package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Typing status in iLink protocol.
 */
public enum TypingStatus {

    /**
     * Start or keep typing.
     */
    START(1),
    /**
     * Stop typing.
     */
    STOP(2);

    private final int code;

    private static final Map<Integer, TypingStatus> CODE_MAP = Arrays.stream(values()).collect(Collectors.toMap(x -> x.code, Function.identity()));

    TypingStatus(int code) {
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
    public static TypingStatus fromCode(Integer code) {
        return CODE_MAP.get(code);
    }
}
