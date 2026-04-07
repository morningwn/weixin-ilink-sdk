package com.github.morningwn.exception;

/**
 * Protocol-level error from iLink endpoints.
 */
public class ILinkProtocolException extends ILinkException {

    private final Integer ret;
    private final Integer errcode;
    private final int httpStatus;

    /**
     * Creates a new protocol exception.
     *
     * @param message error message
     * @param ret response ret value
     * @param errcode response errcode value
     * @param httpStatus HTTP status
     */
    public ILinkProtocolException(String message, Integer ret, Integer errcode, int httpStatus) {
        super(message);
        this.ret = ret;
        this.errcode = errcode;
        this.httpStatus = httpStatus;
    }

    /**
     * @return protocol ret
     */
    public Integer getRet() {
        return ret;
    }

    /**
     * @return protocol errcode
     */
    public Integer getErrcode() {
        return errcode;
    }

    /**
     * @return HTTP status code
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}
