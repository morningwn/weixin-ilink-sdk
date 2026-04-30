package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Typing status in iLink protocol.
 */
public enum TypingStatus {

    /** Start or keep typing. */
    START(1),
    /** Stop typing. */
    STOP(2);

    private final int code;

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
        if (code == null) {
            return null;
        }
        for (TypingStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
