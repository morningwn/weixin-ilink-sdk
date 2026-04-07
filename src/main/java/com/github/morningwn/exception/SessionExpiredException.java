package com.github.morningwn.exception;

/**
 * Indicates session timeout/expiration for current bot token.
 */
public class SessionExpiredException extends ILinkProtocolException {

    /**
     * Creates a session expired exception.
     *
     * @param message error message
     * @param ret response ret value
     * @param errcode response errcode value
     * @param httpStatus HTTP status
     */
    public SessionExpiredException(String message, Integer ret, Integer errcode, int httpStatus) {
        super(message, ret, errcode, httpStatus);
    }
}
