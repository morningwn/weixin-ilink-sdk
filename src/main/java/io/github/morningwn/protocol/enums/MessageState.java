package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Message state in iLink protocol.
 */
public enum MessageState {

    /** New state. */
    NEW(0),
    /** Generating state. */
    GENERATING(1),
    /** Finish state. */
    FINISH(2);

    private final int code;

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
        if (code == null) {
            return null;
        }
        for (MessageState value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
