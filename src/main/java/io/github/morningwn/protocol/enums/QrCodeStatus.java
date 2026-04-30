package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * QR login status in iLink protocol.
 */
public enum QrCodeStatus {

    /** Waiting for scan. */
    WAIT("wait"),
    /** QR scanned. */
    SCANED("scaned"),
    /** QR scanned and redirect host should be used. */
    SCANED_BUT_REDIRECT("scaned_but_redirect"),
    /** QR confirmed and login is ready. */
    CONFIRMED("confirmed"),
    /** QR expired. */
    EXPIRED("expired");

    private final String value;

    QrCodeStatus(String value) {
        this.value = value;
    }

    /**
     * @return protocol string value
     */
    @JsonValue
    public String value() {
        return value;
    }

    /**
     * Resolve enum from protocol status string.
     *
     * @param value protocol status
     * @return enum value or null when unknown
     */
    @JsonCreator
    public static QrCodeStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (QrCodeStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return null;
    }
}
