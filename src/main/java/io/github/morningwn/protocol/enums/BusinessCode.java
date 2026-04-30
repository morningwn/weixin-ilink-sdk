package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business response codes in iLink protocol.
 */
public enum BusinessCode {

    /**
     * Request succeeded.
     */
    OK(0),
    /**
     * Session token expired.
     */
    SESSION_EXPIRED(-14);

    private final int code;

    private static final Map<Integer, BusinessCode> CODE_MAP = Arrays.stream(values()).collect(Collectors.toMap(x -> x.code, Function.identity()));

    BusinessCode(int code) {
        this.code = code;
    }

    /**
     * Resolve enum from protocol code.
     *
     * @param code protocol numeric code
     * @return enum value or null when unknown
     */
    @JsonCreator
    public static BusinessCode fromCode(Integer code) {
        return CODE_MAP.get(code);
    }

    /**
     * @return protocol numeric code
     */
    @JsonValue
    public int code() {
        return code;
    }
}
