package io.github.morningwn.protocol.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * QR login status in iLink protocol.
 */
public enum QrCodeStatus {

    /**
     * Waiting for scan.
     */
    WAIT("wait"),
    /**
     * QR scanned.
     */
    SCANED("scaned"),
    /**
     * QR scanned and redirect host should be used.
     */
    SCANED_BUT_REDIRECT("scaned_but_redirect"),
    /**
     * QR confirmed and login is ready.
     */
    CONFIRMED("confirmed"),
    /**
     * QR expired.
     */
    EXPIRED("expired");

    private final String value;

    private static final Map<String, QrCodeStatus> VALUE_MAP = Arrays.stream(values()).collect(Collectors.toMap(x -> x.value, Function.identity()));

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
        return VALUE_MAP.get(value);
    }
}
