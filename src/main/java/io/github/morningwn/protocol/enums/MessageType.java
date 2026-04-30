package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Message direction type in iLink protocol.
 */
public enum MessageType {

    /** User inbound message. */
    USER(1),
    /** Bot outbound message. */
    BOT(2);

    private final int code;

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
        if (code == null) {
            return null;
        }
        for (MessageType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}