package io.github.morningwn.protocol.enums;

/**
 * Business response codes in iLink protocol.
 */
public enum BusinessCode {

    /** Request succeeded. */
    OK(0),
    /** Session token expired. */
    SESSION_EXPIRED(-14);

    private final int code;

    BusinessCode(int code) {
        this.code = code;
    }

    /**
     * @return protocol numeric code
     */
    public int code() {
        return code;
    }

    /**
     * Resolve enum from protocol code.
     *
     * @param code protocol numeric code
     * @return enum value or null when unknown
     */
    public static BusinessCode fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (BusinessCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
